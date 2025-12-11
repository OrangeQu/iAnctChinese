package com.ianctchinese.service.impl;

import com.ianctchinese.dto.AutoAnnotationResponse;
import com.ianctchinese.dto.ClassificationResponse;
import com.ianctchinese.dto.ModelAnalysisResponse;
import com.ianctchinese.dto.SentenceSegmentRequest;
import com.ianctchinese.dto.TextInsightsResponse;
import com.ianctchinese.dto.TextInsightsResponse.BattleEvent;
import com.ianctchinese.dto.TextInsightsResponse.FamilyNode;
import com.ianctchinese.dto.TextInsightsResponse.MapPathPoint;
import com.ianctchinese.dto.TextInsightsResponse.OfficialNode;
import com.ianctchinese.dto.TextInsightsResponse.ProcessStep;
import com.ianctchinese.dto.TextInsightsResponse.Stats;
import com.ianctchinese.dto.TextInsightsResponse.TimelineEvent;
import com.ianctchinese.dto.TextInsightsResponse.WordCloudItem;
import com.ianctchinese.llm.GeoService;
import com.ianctchinese.llm.SiliconFlowClient;
import com.ianctchinese.llm.dto.AnnotationPayload;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
import com.ianctchinese.llm.dto.AnnotationPayload.AnnotationEntity;
import com.ianctchinese.llm.dto.AnnotationPayload.AnnotationRelation;
import com.ianctchinese.llm.dto.ClassificationPayload;
import com.ianctchinese.llm.dto.SentenceSuggestion;
import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.EntityAnnotation.EntityCategory;
import com.ianctchinese.model.RelationAnnotation;
import com.ianctchinese.model.RelationAnnotation.RelationType;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.model.TextSection;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import com.ianctchinese.service.AnalysisService;
import com.ianctchinese.service.TextSectionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisServiceImpl implements AnalysisService {

  private static final Map<String, String> CATEGORY_LABELS = Map.ofEntries(
      Map.entry("warfare", "战争纪实"),
      Map.entry("travelogue", "游记地理"),
      Map.entry("biography", "人物传记"),
      Map.entry("official", "官职体系"),
      Map.entry("agriculture", "农书类"),
      Map.entry("crafts", "工艺技术"),
      Map.entry("other", "其他"),
      Map.entry("unknown", "综合待识别")
  );

  private final TextDocumentRepository textDocumentRepository;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;
  private final TextSectionRepository textSectionRepository;
  private final TextSectionService textSectionService;
  private final SiliconFlowClient siliconFlowClient;
  private final GeoService geoService;
  private final Executor analysisTaskExecutor;

  @Override
  @Transactional
  public ClassificationResponse classifyText(Long textId, String model) {
    TextDocument document = loadText(textId);
    ClassificationPayload payload = classifyWithFallback(document.getContent(), model);
    String normalizedCategory = normalizeCategory(payload.getCategory(), document.getCategory());
    document.setCategory(normalizedCategory);
    textDocumentRepository.save(document);
    return ClassificationResponse.builder()
        .textId(textId)
        .suggestedCategory(normalizedCategory)
        .confidence(payload.getConfidence())
        .reasons(payload.getReasons())
        .build();
  }

  @Override
  public TextInsightsResponse buildInsights(Long textId, boolean light) {
    TextDocument text = loadText(textId);
    List<EntityAnnotation> entities = entityAnnotationRepository.findByTextDocumentId(textId);
    List<RelationAnnotation> relations = relationAnnotationRepository.findByTextDocumentId(textId);
    List<TextSection> sections = textSectionRepository.findByTextDocumentId(textId);

    String category = text.getCategory();
    String content = text.getContent();

    log.info("开始并行构建洞察，textId={}, light={}", textId, light);

    // 统计信息（快速计算，不需要并行）
    Stats stats = Stats.builder()
        .entityCount(entities.size())
        .relationCount(relations.size())
        .punctuationProgress(calculatePunctuationProgress(sections))
        .build();

    // ============ 并行构建各种可视化图谱 ============
    // 这些图谱构建互不依赖，可以并行执行以加快速度
    // 使用自定义线程池，设置30秒超时
    CompletableFuture<List<WordCloudItem>> wordCloudFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行构建：词云");
      return buildWordCloud(entities, content);
    }, analysisTaskExecutor);

    CompletableFuture<List<TimelineEvent>> timelineFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行构建：时间轴");
      return buildTimelineFromEntities(text, entities, relations);
    }, analysisTaskExecutor);

    CompletableFuture<List<MapPathPoint>> mapPointsFuture = CompletableFuture.supplyAsync(() -> {
      if (light) {
        log.info("Light模式：跳过地图点构建");
        return Collections.<MapPathPoint>emptyList();
      }
      log.info("并行构建：地图点（调用腾讯地图API）");
      return buildMapPointsFromEntities(textId, entities);
    }, analysisTaskExecutor);

    CompletableFuture<List<BattleEvent>> battleTimelineFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行构建：战役时间轴（调用LLM）");
      return buildBattleTimeline(category, content);
    }, analysisTaskExecutor);

    CompletableFuture<List<FamilyNode>> familyTreeFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行构建：家族树");
      return buildFamilyTree(category, entities, relations);
    }, analysisTaskExecutor);

    CompletableFuture<List<OfficialNode>> officialTreeFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行构建：官职树（可能调用LLM）");
      return buildOfficialTree(category, content, entities, relations);
    }, analysisTaskExecutor);

    CompletableFuture<List<ProcessStep>> processCycleFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行构建：流程周期");
      return buildProcessCycle(category, content, entities, relations);
    }, analysisTaskExecutor);

    // 等待所有并行任务完成（设置30秒超时）
    List<WordCloudItem> wordCloud;
    List<TimelineEvent> timeline;
    List<MapPathPoint> mapPoints;
    List<BattleEvent> battleTimeline;
    List<FamilyNode> familyTree;
    List<OfficialNode> officialTree;
    List<ProcessStep> processCycle;

    try {
      log.info("等待所有可视化图谱构建完成...");
      wordCloud = wordCloudFuture.get(30, TimeUnit.SECONDS);
      timeline = timelineFuture.get(30, TimeUnit.SECONDS);
      mapPoints = mapPointsFuture.get(30, TimeUnit.SECONDS);
      battleTimeline = battleTimelineFuture.get(30, TimeUnit.SECONDS);
      familyTree = familyTreeFuture.get(30, TimeUnit.SECONDS);
      officialTree = officialTreeFuture.get(30, TimeUnit.SECONDS);
      processCycle = processCycleFuture.get(30, TimeUnit.SECONDS);
      log.info("所有可视化图谱构建完成");
    } catch (TimeoutException e) {
      log.error("构建洞察时超时，使用部分结果", e);
      // 超时时使用已完成的结果，未完成的使用空列表
      wordCloud = wordCloudFuture.isDone() ? wordCloudFuture.join() : Collections.emptyList();
      timeline = timelineFuture.isDone() ? timelineFuture.join() : Collections.emptyList();
      mapPoints = mapPointsFuture.isDone() ? mapPointsFuture.join() : Collections.emptyList();
      battleTimeline = battleTimelineFuture.isDone() ? battleTimelineFuture.join() : Collections.emptyList();
      familyTree = familyTreeFuture.isDone() ? familyTreeFuture.join() : Collections.emptyList();
      officialTree = officialTreeFuture.isDone() ? officialTreeFuture.join() : Collections.emptyList();
      processCycle = processCycleFuture.isDone() ? processCycleFuture.join() : Collections.emptyList();
    } catch (InterruptedException | ExecutionException e) {
      log.error("构建洞察时并行任务失败", e);
      throw new RuntimeException("构建洞察时发生错误", e);
    }

    List<String> recommendedViews = buildRecommendedViews(category);

    return TextInsightsResponse.builder()
        .textId(textId)
        .category(category)
        .stats(stats)
        .wordCloud(wordCloud)
        .timeline(timeline)
        .mapPoints(mapPoints)
        .battleTimeline(battleTimeline)
        .familyTree(familyTree)
        .officialTree(officialTree)
        .processCycle(processCycle)
        .recommendedViews(recommendedViews)
        .analysisSummary(buildAnalysisSummary(text, stats))
        .mode(light ? "light" : "full")
        .build();
  }

  @Override
  @Transactional
  public AutoAnnotationResponse autoAnnotate(Long textId) {
    TextDocument document = loadText(textId);
    AnnotationPayload payload = siliconFlowClient.annotateText(document.getContent(), null);
    relationAnnotationRepository.deleteByTextDocumentId(textId);
    entityAnnotationRepository.deleteByTextDocumentId(textId);

    List<AnnotationEntity> fallbackEntities = buildHeuristicEntities(document.getContent());
    List<AnnotationEntity> payloadEntities = payload.getEntities().isEmpty()
        ? fallbackEntities
        : payload.getEntities();

    List<AnnotationEntity> payloadRelationsSource = payloadEntities.isEmpty()
        ? fallbackEntities
        : payloadEntities;

    List<AnnotationRelation> payloadRelations = payload.getRelations().isEmpty()
        ? buildHeuristicRelations(payloadRelationsSource)
        : payload.getRelations();

    List<EntityAnnotation> entities = saveEntities(document, payloadEntities);
    List<RelationAnnotation> relations = saveRelations(document, payloadRelations, entities);
    if (relations.isEmpty()) {
      relations = saveHeuristicRelations(document, entities);
    }

    if (!payload.getSentences().isEmpty()) {
      textSectionService.replaceSections(textId, toSegmentRequests(textId, payload.getSentences()));
    } else {
      textSectionService.autoSegment(textId);
    }

    return AutoAnnotationResponse.builder()
        .textId(textId)
        .createdEntities(entities.size())
        .createdRelations(relations.size())
        .message("模型已生成实体、关系与句读，可在前端继续校对。")
        .build();
  }

  @Override
  @Transactional
  public ModelAnalysisResponse runFullAnalysis(Long textId, String model) {
    try {
      return doRunFullAnalysis(textId, model);
    } catch (Exception ex) {
      // 防御性降级：LLM 失败或超时时，回退到启发式结果，避免前端看到整体失败。
      TextDocument document = loadText(textId);
      ClassificationPayload clsPayload = heuristicClassify(document.getContent());
      document.setCategory(normalizeCategory(clsPayload.getCategory(), document.getCategory()));
      textDocumentRepository.save(document);

      List<AnnotationEntity> fallbackEntities = buildHeuristicEntities(document.getContent());
      List<RelationAnnotation> relations = saveHeuristicRelations(document,
          saveEntities(document, fallbackEntities));

      if (relations.isEmpty()) {
        relations = relationAnnotationRepository.findByTextDocumentId(textId);
      }

      textSectionService.autoSegment(textId);

      ClassificationResponse classification = ClassificationResponse.builder()
          .textId(textId)
          .suggestedCategory(document.getCategory())
          .confidence(clsPayload.getConfidence())
          .reasons(clsPayload.getReasons())
          .build();

      AutoAnnotationResponse annotation = AutoAnnotationResponse.builder()
          .textId(textId)
          .createdEntities(fallbackEntities.size())
          .createdRelations(relations.size())
          .message("LLM 失败，已返回启发式结果")
          .build();

      TextInsightsResponse insights = buildInsights(textId, false);
      List<TextSection> sections = textSectionRepository.findByTextDocumentId(textId);
      return ModelAnalysisResponse.builder()
          .classification(classification)
          .annotation(annotation)
          .insights(insights)
          .sections(sections)
          .build();
    }
  }

  private ModelAnalysisResponse doRunFullAnalysis(Long textId, String model) {
    TextDocument document = loadText(textId);
    String content = document.getContent();

    log.info("开始并行调用API进行全面分析，textId={}", textId);

    // ============ 第一阶段：并行调用LLM（分类 + 标注） ============
    // 这两个API调用互不依赖，可以并行执行以加快速度
    // 使用自定义线程池，设置60秒超时
    CompletableFuture<ClassificationPayload> classificationFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行任务1：开始分类分析");
      ClassificationPayload result = classifyWithFallback(content, model);
      log.info("并行任务1：分类完成，类别={}", result.getCategory());
      return result;
    }, analysisTaskExecutor);

    CompletableFuture<AnnotationPayload> annotationFuture = CompletableFuture.supplyAsync(() -> {
      log.info("并行任务2：开始实体标注");
      AnnotationPayload result = siliconFlowClient.annotateText(content, model);
      log.info("并行任务2：标注完成，实体数={}, 关系数={}",
               result.getEntities().size(), result.getRelations().size());
      return result;
    }, analysisTaskExecutor);

    // 等待两个并行任务都完成（60秒超时）
    ClassificationPayload clsPayload;
    AnnotationPayload annPayload;
    try {
      log.info("等待并行任务完成...");
      clsPayload = classificationFuture.get(60, TimeUnit.SECONDS);
      annPayload = annotationFuture.get(60, TimeUnit.SECONDS);
      log.info("所有并行任务已完成");
    } catch (TimeoutException e) {
      log.error("API调用超时", e);
      throw new RuntimeException("分析过程超时，请稍后重试", e);
    } catch (InterruptedException | ExecutionException e) {
      log.error("并行调用失败", e);
      throw new RuntimeException("分析过程中发生错误", e);
    }

    // ============ 第二阶段：保存数据（需要在事务中顺序执行） ============
    String normalizedCategory = normalizeCategory(clsPayload.getCategory(), document.getCategory());
    document.setCategory(normalizedCategory);
    textDocumentRepository.save(document);

    relationAnnotationRepository.deleteByTextDocumentId(textId);
    entityAnnotationRepository.deleteByTextDocumentId(textId);

    List<AnnotationEntity> payloadEntities = annPayload.getEntities().isEmpty()
        ? buildHeuristicEntities(document.getContent())
        : annPayload.getEntities();
    List<AnnotationRelation> payloadRelations = annPayload.getRelations().isEmpty()
        ? buildHeuristicRelations(payloadEntities)
        : annPayload.getRelations();

    List<EntityAnnotation> entities = saveEntities(document, payloadEntities);
    List<RelationAnnotation> relations = saveRelations(document, payloadRelations, entities);
    if (relations.isEmpty()) {
      relations = saveHeuristicRelations(document, entities);
    }

    if (!annPayload.getSentences().isEmpty()) {
      textSectionService.replaceSections(textId, toSegmentRequests(textId, annPayload.getSentences()));
    } else {
      textSectionService.autoSegment(textId);
    }

    ClassificationResponse classification = ClassificationResponse.builder()
        .textId(textId)
        .suggestedCategory(normalizedCategory)
        .confidence(clsPayload.getConfidence())
        .reasons(clsPayload.getReasons())
        .build();

    AutoAnnotationResponse annotation = AutoAnnotationResponse.builder()
        .textId(textId)
        .createdEntities(entities.size())
        .createdRelations(relations.size())
        .message("模型已生成实体、关系与句读，可在前端继续校对。")
        .build();

    // ============ 第三阶段：构建洞察（已优化为并行） ============
    TextInsightsResponse insights = buildInsights(textId, false);
    List<TextSection> sections = textSectionRepository.findByTextDocumentId(textId);

    log.info("全面分析完成，textId={}", textId);
    return ModelAnalysisResponse.builder()
        .classification(classification)
        .annotation(annotation)
        .insights(insights)
        .sections(sections)
        .build();
  }

  private List<SentenceSegmentRequest> toSegmentRequests(Long textId, List<SentenceSuggestion> suggestions) {
    List<SentenceSegmentRequest> requests = new ArrayList<>();
    int index = 1;
    for (SentenceSuggestion suggestion : suggestions) {
      SentenceSegmentRequest request = new SentenceSegmentRequest();
      request.setTextId(textId);
      request.setSequenceIndex(index++);
      request.setOriginalText(Optional.ofNullable(suggestion.getOriginal()).orElse(suggestion.getPunctuated()));
      request.setPunctuatedText(suggestion.getPunctuated());
      request.setSummary(suggestion.getSummary());
      requests.add(request);
    }
    return requests;
  }

  private List<EntityAnnotation> saveEntities(TextDocument document, List<AnnotationEntity> payloadEntities) {
    List<EntityAnnotation> entities = payloadEntities.stream()
        .map(item -> EntityAnnotation.builder()
            .textDocument(document)
            .label(item.getLabel())
            .startOffset(Optional.ofNullable(item.getStartOffset()).orElse(0))
            .endOffset(Optional.ofNullable(item.getEndOffset()).orElse(0))
            .category(resolveCategory(item.getCategory()))
            .confidence(item.getConfidence())
            .color("#d16a5d")
            .build())
        .collect(Collectors.toList());
    return entityAnnotationRepository.saveAll(entities);
  }

  private List<RelationAnnotation> saveRelations(TextDocument document,
      List<AnnotationRelation> payloadRelations,
      List<EntityAnnotation> entities) {
    Map<String, EntityAnnotation> entityLookup = entities.stream()
        .collect(Collectors.toMap(EntityAnnotation::getLabel, e -> e, (a, b) -> a));
    List<RelationAnnotation> relations = new ArrayList<>();
    for (AnnotationRelation relation : payloadRelations) {
      EntityAnnotation source = entityLookup.get(relation.getSourceLabel());
      EntityAnnotation target = entityLookup.get(relation.getTargetLabel());
      if (source == null || target == null) {
        continue;
      }
      relations.add(RelationAnnotation.builder()
          .textDocument(document)
          .source(source)
          .target(target)
          .relationType(resolveRelation(relation.getRelationType()))
          .confidence(relation.getConfidence())
          .evidence(relation.getDescription())
          .build());
    }
    return relationAnnotationRepository.saveAll(relations);
  }

  private List<RelationAnnotation> saveHeuristicRelations(TextDocument document,
      List<EntityAnnotation> entities) {
    List<RelationAnnotation> relations = new ArrayList<>();
    if (entities == null || entities.size() < 2) {
      return relations;
    }
    for (int i = 0; i < entities.size() - 1 && relations.size() < 8; i++) {
      EntityAnnotation a = entities.get(i);
      EntityAnnotation b = entities.get(i + 1);
      relations.add(RelationAnnotation.builder()
          .textDocument(document)
          .source(a)
          .target(b)
          .relationType(RelationType.CUSTOM)
          .confidence(0.4)
          .evidence("相邻共现推断")
          .build());
    }
    return relationAnnotationRepository.saveAll(relations);
  }

  private List<AnnotationRelation> buildHeuristicRelations(List<AnnotationEntity> entities) {
    List<AnnotationRelation> relations = new ArrayList<>();
    if (entities == null || entities.size() < 2) {
      return relations;
    }
    for (int i = 0; i < entities.size() - 1 && relations.size() < 8; i++) {
      AnnotationEntity a = entities.get(i);
      AnnotationEntity b = entities.get(i + 1);
      relations.add(AnnotationRelation.builder()
          .sourceLabel(a.getLabel())
          .targetLabel(b.getLabel())
          .relationType("CUSTOM")
          .confidence(0.5)
          .description("相邻共现")
          .build());
    }
    return relations;
  }

  private TextDocument loadText(Long textId) {
    return textDocumentRepository.findById(textId)
        .orElseThrow(() -> new IllegalArgumentException("Text not found: " + textId));
  }

  /**
   * 调用大模型分类；如返回为空或“unknown”，使用关键词启发式兜底，至少给出一个可用类别。
   */
  private ClassificationPayload classifyWithFallback(String content, String model) {
    ClassificationPayload payload = null;
    try {
      payload = siliconFlowClient.classifyText(content, model);
    } catch (Exception ex) {
    }

    boolean invalid = payload == null
        || payload.getCategory() == null
        || payload.getCategory().isBlank()
        || "unknown".equalsIgnoreCase(payload.getCategory());

    if (!invalid) {
      return payload;
    }

    return heuristicClassify(content);
  }

  /**
   * 简单关键词统计，用于无网络/模型失败时的本地兜底分类。
   */
  private ClassificationPayload heuristicClassify(String content) {
    if (content == null) {
      content = "";
    }
    Map<String, Integer> scores = new HashMap<>();
    scores.put("warfare", countKeywords(content, List.of("战", "兵", "攻", "军", "将", "敌", "阵", "戎")));
    scores.put("travelogue", countKeywords(content, List.of("山", "水", "江", "河", "湖", "舟", "行", "游", "路", "岭", "津")));
    scores.put("biography", countKeywords(content, List.of("生", "卒", "字", "号", "君", "父", "母", "兄", "子", "仕", "官", "谥", "年")));
    scores.put("official", countKeywords(content, List.of("官", "职", "尚书", "御史", "太守", "知府", "刺史", "令", "丞", "郎", "侍", "阁", "部", "司")));
    scores.put("agriculture", countKeywords(content, List.of("田", "种", "耕", "播", "收", "稼", "穑", "农", "桑", "蚕", "丝", "麦", "稻", "谷", "粮", "肥")));
    scores.put("crafts", countKeywords(content, List.of("工", "匠", "制", "造", "铸", "锻", "织", "染", "烧", "炼", "器", "具", "技", "法", "术")));

    Entry<String, Integer> best = scores.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .orElse(Map.entry("unknown", 0));

    String category = best.getValue() > 0 ? best.getKey() : "unknown";
    double confidence = Math.min(0.9, 0.55 + best.getValue() * 0.05);
    List<String> reasons = List.of("本地关键词推断：" + CATEGORY_LABELS.getOrDefault(category, "待识别"));

    return ClassificationPayload.builder()
        .category(category)
        .confidence(confidence)
        .reasons(reasons)
        .build();
  }

  private int countKeywords(String content, List<String> keywords) {
    int score = 0;
    for (String keyword : keywords) {
      int idx = content.indexOf(keyword);
      while (idx >= 0) {
        score++;
        idx = content.indexOf(keyword, idx + keyword.length());
      }
    }
    return score;
  }

  private String normalizeCategory(String category, String fallback) {
    if (category == null || category.isBlank()) {
      return Optional.ofNullable(fallback).orElse("unknown");
    }
    String normalized = category.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "warfare", "travelogue", "biography", "official", "agriculture", "crafts", "other" -> normalized;
      case "unknown" -> Optional.ofNullable(fallback).orElse("other");
      default -> Optional.ofNullable(fallback).orElse("other");
    };
  }

  private EntityCategory resolveCategory(String category) {
    if (category == null) {
      return EntityCategory.CUSTOM;
    }
    return switch (category.toUpperCase(Locale.ROOT)) {
      case "PERSON" -> EntityCategory.PERSON;
      case "LOCATION" -> EntityCategory.LOCATION;
      case "EVENT" -> EntityCategory.EVENT;
      case "ORGANIZATION" -> EntityCategory.ORGANIZATION;
      case "OBJECT" -> EntityCategory.OBJECT;
      default -> EntityCategory.CUSTOM;
    };
  }

  private RelationType resolveRelation(String relationType) {
    if (relationType == null) {
      return RelationType.CUSTOM;
    }
    return switch (relationType.toUpperCase(Locale.ROOT)) {
      case "ALLY" -> RelationType.ALLY;
      case "SUPPORT" -> RelationType.SUPPORT;
      case "RIVAL" -> RelationType.RIVAL;
      case "CONFLICT" -> RelationType.CONFLICT;
      case "FAMILY" -> RelationType.FAMILY;
      case "MENTOR" -> RelationType.MENTOR;
      case "INFLUENCE" -> RelationType.INFLUENCE;
      case "LOCATION_OF" -> RelationType.LOCATION_OF;
      case "PART_OF" -> RelationType.PART_OF;
      case "CAUSE" -> RelationType.CAUSE;
      case "TEMPORAL" -> RelationType.TEMPORAL;
      case "TRAVEL" -> RelationType.TRAVEL;
      default -> RelationType.CUSTOM;
    };
  }

  private Double calculatePunctuationProgress(List<TextSection> sections) {
    if (sections.isEmpty()) {
      return 0.05;
    }
    long completed = sections.stream()
        .filter(section -> section.getPunctuatedText() != null && !section.getPunctuatedText().isBlank())
        .count();
    return (double) completed / sections.size();
  }

  private List<WordCloudItem> buildWordCloud(List<EntityAnnotation> entities, String content) {
    // 使用LLM生成词云（强制调用API）
    String entityList = entities.stream()
        .limit(20)
        .map(EntityAnnotation::getLabel)
        .collect(Collectors.joining("、"));

    try {
      JsonNode result = siliconFlowClient.analyzeWordCloud(content, entityList, null);
      if (result != null && result.has("wordCloud") && result.get("wordCloud").isArray()) {
        List<WordCloudItem> items = new ArrayList<>();
        result.get("wordCloud").forEach(item -> {
          items.add(WordCloudItem.builder()
              .label(item.path("label").asText())
              .weight(item.path("weight").asDouble(0.5))
              .build());
        });
        if (!items.isEmpty()) {
          log.info("词云构建完成：LLM返回{}个关键词", items.size());
          return items;
        }
      }
    } catch (Exception ex) {
      log.warn("LLM词云分析失败，使用降级方案: {}", ex.getMessage());
    }

    // 降级方案：基于实体频次
    if (entities != null && !entities.isEmpty()) {
      Map<String, Integer> freq = new HashMap<>();
      entities.forEach(e -> {
        if (e.getLabel() != null && !e.getLabel().isBlank()) {
          freq.merge(e.getLabel(), 1, Integer::sum);
        }
      });
      int max = freq.values().stream().max(Integer::compareTo).orElse(1);
      return freq.entrySet().stream()
          .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .limit(12)
          .map(entry -> WordCloudItem.builder()
              .label(entry.getKey())
              .weight(0.6 + 0.4 * (entry.getValue() / (double) max))
              .build())
          .collect(Collectors.toList());
    }

    // 最后降级：基于文本的简易词频
    String[] tokens = content.split("[，。、；：？！\\s]");
    Map<String, Integer> frequency = new HashMap<>();
    for (String token : tokens) {
      if (token.length() < 2) {
        continue;
      }
      frequency.merge(token, 1, Integer::sum);
    }
    int max = frequency.values().stream().max(Integer::compareTo).orElse(1);
    return frequency.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(12)
        .map(entry -> WordCloudItem.builder()
            .label(entry.getKey())
            .weight(0.6 + 0.4 * (entry.getValue() / (double) max))
            .build())
        .collect(Collectors.toList());
  }

  /**
   * 从实体和关系中构建时间线事件
   * 优化：并行分析所有事件的历史影响，提高处理速度
   * 改进：即使没有EVENT实体，也强制调用LLM分析文章提取时间轴
   */
  private List<TimelineEvent> buildTimelineFromEntities(
      TextDocument text,
      List<EntityAnnotation> entities,
      List<RelationAnnotation> relations) {

    String category = text.getCategory();
    String content = text.getContent();

    // 1. 从EVENT类型的实体中提取事件
    List<EntityAnnotation> eventEntities = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.EVENT)
        .collect(Collectors.toList());

    log.debug("Building timeline for category '{}': found {} EVENT entities", category, eventEntities.size());

    if (eventEntities.isEmpty()) {
      // 没有EVENT实体时，强制调用LLM分析文章提取时间轴
      log.info("没有EVENT实体，调用LLM分析文章提取时间轴");
      return buildTimelineFromLLM(content, category);
    }

    // 2. 并行分析所有事件的影响
    log.info("开始并行分析{}个事件的历史影响", eventEntities.size());
    List<CompletableFuture<TimelineEvent>> futureEvents = eventEntities.stream()
        .map(event -> CompletableFuture.supplyAsync(() -> {
          try {
            // 提取事件基本信息
            String eventText = extractText(content, event.getStartOffset(), event.getEndOffset());
            String eventType = determineEventType(event, relations, category);

            // 查找相关人物
            List<String> participants = findEventParticipants(event, relations, entities, category);
            if (participants == null || participants.isEmpty()) {
              participants = List.of("无");
            }

            // 查找相关地点
            List<String> locations = findRelatedEntities(event, relations, entities, EntityCategory.LOCATION);
            String location = locations.isEmpty() ? null : locations.get(0);

            // 提取时间标签
            String dateLabel = extractDateLabel(event, relations, entities, content);

            // 计算重要度
            Integer significance = calculateSignificance(event, relations);

            // 分析历史影响（可能调用LLM API）
            String impact = analyzeEventImpactWithRetry(event, eventText, content, category);
            if (impact == null || impact.trim().isEmpty()) {
              impact = "无";
            }

            return TimelineEvent.builder()
                .title(event.getLabel())
                .description(eventText)
                .dateLabel(dateLabel)
                .significance(significance)
                .eventType(eventType)
                .location(location)
                .participants(participants)
                .impact(impact)
                .entityId(event.getId())
                .startOffset(event.getStartOffset())
                .endOffset(event.getEndOffset())
                .build();
          } catch (Exception e) {
            log.error("Failed to build timeline event for '{}'", event.getLabel(), e);
            // 返回一个基本的事件对象作为降级
            return TimelineEvent.builder()
                .title(event.getLabel())
                .description("处理失败")
                .significance(5)
                .eventType("default")
                .participants(List.of("无"))
                .impact("无")
                .entityId(event.getId())
                .startOffset(event.getStartOffset())
                .endOffset(event.getEndOffset())
                .build();
          }
        }, analysisTaskExecutor))
        .collect(Collectors.toList());

    // 3. 收集所有并行任务的结果（设置30秒超时）
    List<TimelineEvent> events = new ArrayList<>();
    try {
      for (CompletableFuture<TimelineEvent> future : futureEvents) {
        events.add(future.get(30, TimeUnit.SECONDS));
      }
      log.info("所有事件影响分析完成，共{}个事件", events.size());
    } catch (TimeoutException e) {
      log.warn("部分事件影响分析超时，使用已完成的结果", e);
      // 收集已完成的结果
      for (CompletableFuture<TimelineEvent> future : futureEvents) {
        if (future.isDone()) {
          try {
            events.add(future.join());
          } catch (Exception ex) {
            log.debug("Failed to join completed future", ex);
          }
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      log.error("事件影响分析失败", e);
      // 即使失败也尝试收集已完成的结果
      for (CompletableFuture<TimelineEvent> future : futureEvents) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
          try {
            events.add(future.join());
          } catch (Exception ex) {
            log.debug("Failed to join completed future", ex);
          }
        }
      }
    }

    // 4. 如果没有成功构建任何事件，调用LLM作为降级
    if (events.isEmpty()) {
      log.warn("基于实体构建时间轴失败，调用LLM降级");
      return buildTimelineFromLLM(content, category);
    }

    return events;
  }

  /**
   * 使用LLM从文章中提取时间轴事件
   */
  private List<TimelineEvent> buildTimelineFromLLM(String content, String category) {
    try {
      JsonNode result = siliconFlowClient.analyzeTimeline(content, category, null);
      if (result != null && result.has("timeline") && result.get("timeline").isArray()) {
        List<TimelineEvent> events = new ArrayList<>();
        result.get("timeline").forEach(item -> {
          // 解析participants
          List<String> participants = new ArrayList<>();
          if (item.has("participants") && item.get("participants").isArray()) {
            item.get("participants").forEach(p -> participants.add(p.asText()));
          }
          if (participants.isEmpty()) {
            participants.add("无");
          }

          events.add(TimelineEvent.builder()
              .title(item.path("title").asText(""))
              .description(item.path("description").asText(""))
              .dateLabel(item.path("dateLabel").asText(""))
              .significance(item.path("significance").asInt(5))
              .eventType(item.path("eventType").asText("default"))
              .location(item.path("location").asText(null))
              .participants(participants)
              .impact(item.path("impact").asText("无"))
              .build());
        });
        if (!events.isEmpty()) {
          log.info("LLM时间轴分析完成：返回{}个事件", events.size());
          return events;
        }
      }
    } catch (Exception ex) {
      log.error("LLM时间轴分析失败: {}", ex.getMessage());
    }

    // 所有方法都失败，返回空列表而不是默认数据
    log.warn("无法为文章生成时间轴，返回空列表");
    return Collections.emptyList();
  }

  /**
   * 从关系中构建时间线（当没有明确的EVENT实体时）
   */
  private List<TimelineEvent> buildTimelineFromRelations(
      List<RelationAnnotation> relations,
      List<EntityAnnotation> entities,
      String content,
      String category) {

    List<TimelineEvent> events = new ArrayList<>();

    // 根据分类选择合适的关系类型
    List<RelationType> relevantTypes = switch (category) {
      case "travelogue" -> List.of(RelationType.TRAVEL, RelationType.TEMPORAL);
      case "warfare" -> List.of(RelationType.CONFLICT, RelationType.TEMPORAL);
      case "biography" -> List.of(RelationType.FAMILY, RelationType.TEMPORAL);
      default -> List.of(RelationType.TEMPORAL, RelationType.CUSTOM);
    };

    // 从相关关系中提取事件
    for (RelationAnnotation relation : relations) {
      if (relevantTypes.contains(relation.getRelationType())) {
        EntityAnnotation source = relation.getSource();
        EntityAnnotation target = relation.getTarget();

        if (source != null && target != null) {
          String title = source.getLabel() + " → " + target.getLabel();
          String description = relation.getEvidence() != null
              ? relation.getEvidence()
              : "关联：" + source.getLabel() + " 与 " + target.getLabel();

          String eventType = determineEventTypeFromRelation(relation, category);

          // 跳过历史影响分析以加快速度
          String impact = generateBasicImpact(source, description, category);
          if (impact == null || impact.trim().isEmpty()) {
            impact = "无";
          }

          // 查找相关人物
          List<String> participants;
          if (source.getCategory() == EntityCategory.PERSON) {
            participants = List.of(source.getLabel());
          } else if (target.getCategory() == EntityCategory.PERSON) {
            participants = List.of(target.getLabel());
          } else {
            // 尝试在附近查找人物
            participants = entities.stream()
                .filter(e -> e.getCategory() == EntityCategory.PERSON)
                .filter(e -> Math.abs(e.getStartOffset() - source.getStartOffset()) < 100)
                .limit(2)
                .map(EntityAnnotation::getLabel)
                .collect(Collectors.toList());
          }
          if (participants.isEmpty()) {
            participants = List.of("无");
          }

          events.add(TimelineEvent.builder()
              .title(title)
              .description(description)
              .dateLabel(null)
              .significance(5)
              .eventType(eventType)
              .location(target.getCategory() == EntityCategory.LOCATION ? target.getLabel() : null)
              .participants(participants)
              .impact(impact)
              .entityId(source.getId())
              .startOffset(source.getStartOffset())
              .endOffset(source.getEndOffset())
              .build());
        }
      }
    }

    return events;
  }

  /**
   * 确定事件类型
   */
  private String determineEventType(EntityAnnotation event, List<RelationAnnotation> relations, String category) {
    String label = event.getLabel().toLowerCase();

    // 根据事件标签判断类型
    if (label.contains("出生") || label.contains("诞")) return "birth";
    if (label.contains("逝世") || label.contains("卒") || label.contains("殁")) return "death";
    if (label.contains("任") || label.contains("官") || label.contains("授")) return "official";
    if (label.contains("战") || label.contains("役") || label.contains("攻")) return "battle";
    if (label.contains("行") || label.contains("游") || label.contains("至")) return "travel";
    if (label.contains("成就") || label.contains("功")) return "achievement";

    // 根据文本分类设置默认类型
    return switch (category) {
      case "biography" -> "life";
      case "travelogue" -> "travel";
      case "warfare" -> "military";
      default -> "default";
    };
  }

  /**
   * 从关系类型确定事件类型
   */
  private String determineEventTypeFromRelation(RelationAnnotation relation, String category) {
    return switch (relation.getRelationType()) {
      case TRAVEL -> "travel";
      case CONFLICT -> "battle";
      case FAMILY -> "life";
      case SUPPORT -> "achievement";
      default -> switch (category) {
        case "biography" -> "life";
        case "travelogue" -> "travel";
        case "warfare" -> "military";
        default -> "default";
      };
    };
  }

  /**
   * 查找与实体相关的其他实体
   */
  private List<String> findRelatedEntities(
      EntityAnnotation entity,
      List<RelationAnnotation> relations,
      List<EntityAnnotation> allEntities,
      EntityCategory targetCategory) {

    Set<String> relatedNames = new LinkedHashSet<>();

    for (RelationAnnotation relation : relations) {
      EntityAnnotation related = null;

      if (relation.getSource() != null && relation.getSource().getId().equals(entity.getId())) {
        related = relation.getTarget();
      } else if (relation.getTarget() != null && relation.getTarget().getId().equals(entity.getId())) {
        related = relation.getSource();
      }

      if (related != null && related.getCategory() == targetCategory) {
        relatedNames.add(related.getLabel());
      }
    }

    return new ArrayList<>(relatedNames);
  }

  /**
   * 查找事件的参与人物（改进版）
   * 对于传记类文本，会更宽泛地查找人物
   */
  private List<String> findEventParticipants(
      EntityAnnotation event,
      List<RelationAnnotation> relations,
      List<EntityAnnotation> allEntities,
      String category) {

    // 1. 首先尝试查找与事件直接关联的人物
    List<String> directParticipants = findRelatedEntities(event, relations, allEntities, EntityCategory.PERSON);

    if (!directParticipants.isEmpty()) {
      return directParticipants.stream().limit(3).collect(Collectors.toList());
    }

    // 2. 查找与事件在文本中位置接近的人物（前后150字符内）
    List<String> nearbyPersons = allEntities.stream()
        .filter(e -> e.getCategory() == EntityCategory.PERSON)
        .filter(e -> Math.abs(e.getStartOffset() - event.getStartOffset()) < 150)
        .limit(3)
        .map(EntityAnnotation::getLabel)
        .collect(Collectors.toList());

    if (!nearbyPersons.isEmpty()) {
      return nearbyPersons;
    }

    // 3. 如果还没有人物，且是传记类，返回文本中的主要人物
    if ("biography".equals(category)) {
      List<String> mainPersons = allEntities.stream()
          .filter(e -> e.getCategory() == EntityCategory.PERSON)
          .sorted((a, b) -> Double.compare(b.getConfidence() != null ? b.getConfidence() : 0.0,
                                           a.getConfidence() != null ? a.getConfidence() : 0.0))
          .limit(2)  // 取置信度最高的2个人物
          .map(EntityAnnotation::getLabel)
          .collect(Collectors.toList());

      if (!mainPersons.isEmpty()) {
        return mainPersons;
      }
    }

    // 4. 最后尝试查找文本中任意人物
    List<String> anyPersons = allEntities.stream()
        .filter(e -> e.getCategory() == EntityCategory.PERSON)
        .limit(2)
        .map(EntityAnnotation::getLabel)
        .collect(Collectors.toList());

    return anyPersons.isEmpty() ? List.of() : anyPersons;
  }

  /**
   * 使用大模型分析事件影响（带重试和优化）
   */
  private String analyzeEventImpactWithRetry(
      EntityAnnotation event,
      String eventText,
      String content,
      String category) {

    try {
      String contextText = extractContext(content, event.getStartOffset(), event.getEndOffset(), 150);
      String impact = siliconFlowClient.analyzeEventImpact(
          event.getLabel(),
          eventText,
          contextText,
          category
      );

      // 如果大模型返回空或太短，尝试生成一个基本描述
      if (impact == null || impact.trim().isEmpty()) {
        impact = generateBasicImpact(event, eventText, category);
      }

      return impact;
    } catch (Exception ex) {
      log.warn("Failed to analyze event impact for '{}': {}", event.getLabel(), ex.getMessage());
      // 失败时返回一个基本描述
      return generateBasicImpact(event, eventText, category);
    }
  }

  /**
   * 生成基本的历史影响描述（当大模型调用失败或返回空时使用）
   */
  private String generateBasicImpact(EntityAnnotation event, String eventText, String category) {
    String label = event.getLabel();

    // 根据事件类型生成基本描述
    if (label.contains("出生") || label.contains("诞")) {
      return null; // 出生事件不显示影响
    }
    if (label.contains("逝世") || label.contains("卒") || label.contains("殁")) {
      return "标志着一个时代的结束";
    }
    if (label.contains("任") || label.contains("官") || label.contains("授")) {
      return "影响其仕途发展";
    }
    if (label.contains("战") || label.contains("役") || label.contains("攻")) {
      return "对当时军事格局产生影响";
    }
    if (label.contains("著") || label.contains("书") || label.contains("作")) {
      return "对后世文化产生影响";
    }

    // 根据分类生成通用描述
    return switch (category) {
      case "biography" -> "记录人物生平的重要时刻";
      case "warfare" -> "对战局产生一定影响";
      case "travelogue" -> "记录旅程中的见闻";
      default -> null; // 不确定的不显示
    };
  }

  /**
   * 提取时间标签
   */
  private String extractDateLabel(
      EntityAnnotation event,
      List<RelationAnnotation> relations,
      List<EntityAnnotation> entities,
      String content) {

    // 尝试从附近的文本中提取时间表达
    int start = Math.max(0, event.getStartOffset() - 20);
    int end = Math.min(content.length(), event.getEndOffset() + 20);
    String context = content.substring(start, end);

    // 匹配常见的古文时间表达
    Pattern timePattern = Pattern.compile(
        "(春|夏|秋|冬|元|初|仲|暮|上|中|下|朔|望|晦|" +
        "甲子|乙丑|丙寅|丁卯|戊辰|己巳|庚午|辛未|壬申|癸酉|甲戌|乙亥|" +
        "\\d+年|\\d+月|\\d+日)");
    Matcher matcher = timePattern.matcher(context);

    if (matcher.find()) {
      return matcher.group(1);
    }

    return null;
  }

  /**
   * 计算事件的重要度
   */
  private Integer calculateSignificance(EntityAnnotation event, List<RelationAnnotation> relations) {
    // 基于关系数量计算重要度
    long relationCount = relations.stream()
        .filter(r ->
            (r.getSource() != null && r.getSource().getId().equals(event.getId())) ||
            (r.getTarget() != null && r.getTarget().getId().equals(event.getId()))
        )
        .count();

    // 基于置信度
    int confidenceScore = event.getConfidence() != null
        ? (int) (event.getConfidence() * 5)
        : 5;

    // 综合计算，范围1-10
    return Math.min(10, Math.max(1, confidenceScore + (int) relationCount));
  }

  /**
   * 提取文本片段
   */
  private String extractText(String content, int start, int end) {
    if (content == null || start < 0 || end > content.length() || start >= end) {
      return "";
    }
    String text = content.substring(start, end);
    // 限制长度
    return text.length() > 100 ? text.substring(0, 97) + "..." : text;
  }

  /**
   * 提取事件周围的上下文文本
   */
  private String extractContext(String content, int start, int end, int contextSize) {
    if (content == null || start < 0 || end > content.length() || start >= end) {
      return "";
    }
    int contextStart = Math.max(0, start - contextSize);
    int contextEnd = Math.min(content.length(), end + contextSize);
    return content.substring(contextStart, contextEnd);
  }

  /**
   * 构建默认时间线（回退方案）
   */
  private List<TimelineEvent> buildDefaultTimelineForCategory(String category) {
    List<TimelineEvent> events = new ArrayList<>();
    switch (category) {
      case "travelogue" -> events.addAll(List.of(
          TimelineEvent.builder().title("启程").description("作者离开京师，溯江而上").dateLabel("初春")
              .significance(6).eventType("travel").impact("开启了一段探访名山大川的文化之旅").participants(List.of("无")).build(),
          TimelineEvent.builder().title("抵达名山").description("记述山川胜景与碑刻").dateLabel("暮春")
              .significance(8).eventType("scenery").impact("记录了山川地理与人文古迹，丰富了地理文献").participants(List.of("无")).build(),
          TimelineEvent.builder().title("归程总结").description("整理沿途见闻与思考").dateLabel("初夏")
              .significance(7).eventType("travel").impact("形成了系统的游历记录，为后人了解古代地理提供资料").participants(List.of("无")).build()
      ));
      case "biography" -> events.addAll(List.of(
          TimelineEvent.builder().title("少有奇志").description("幼年立志求学").dateLabel("少年")
              .significance(5).eventType("life").impact("奠定了日后成就的思想基础").participants(List.of("无")).build(),
          TimelineEvent.builder().title("入仕任官").description("受知于名臣，踏入仕途").dateLabel("壬午")
              .significance(8).eventType("official").impact("开启了政治生涯，为后续发展铺平道路").participants(List.of("无")).build(),
          TimelineEvent.builder().title("升迁与谪居").description("仕途沉浮，辗转多地").dateLabel("癸未")
              .significance(7).eventType("official").impact("在政治历练中积累了丰富的治理经验").participants(List.of("无")).build()
      ));
      default -> events.addAll(List.of(
          TimelineEvent.builder().title("部队集结").description("调兵遣将，整训兵甲").dateLabel("初旬")
              .significance(6).eventType("military").impact("为后续战役奠定了军事基础").participants(List.of("无")).build(),
          TimelineEvent.builder().title("决战节点").description("火攻突袭，扭转战局").dateLabel("望日")
              .significance(10).eventType("battle").impact("决定性战役，彻底改变了双方力量对比").participants(List.of("无")).build(),
          TimelineEvent.builder().title("善后与盟约").description("划定疆界，订立盟约").dateLabel("下旬")
              .significance(7).eventType("military").impact("确立了新的地缘政治格局，影响深远").participants(List.of("无")).build()
      ));
    }
    return events;
  }

  private List<MapPathPoint> buildMapPointsFromEntities(Long textId, List<EntityAnnotation> entities) {
    // 筛选出地点类型的实体
    List<EntityAnnotation> locationEntities = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.LOCATION)
        .toList();

    if (locationEntities.isEmpty()) {
      log.info("No LOCATION entities found for text {}", textId);
      return Collections.emptyList();
    }

    log.info("Found {} LOCATION entities for text {}, calling GeoService to get coordinates",
        locationEntities.size(), textId);

    // 构建 GeoLocateRequest
    GeoLocateRequest request = new GeoLocateRequest();
    request.setTextId(textId);
    request.setEntities(locationEntities.stream()
        .map(e -> {
          GeoLocateRequest.EntityDto dto = new GeoLocateRequest.EntityDto();
          dto.setId(e.getId());
          dto.setLabel(e.getLabel());
          dto.setCategory(e.getCategory() != null ? e.getCategory().name() : null);
          return dto;
        })
        .toList());

    // 调用 GeoService 获取坐标
    List<GeoPointDto> geoPoints = geoService.locate(request);
    log.info("GeoService returned {} points for text {}", geoPoints.size(), textId);

    // 转换为 MapPathPoint
    List<MapPathPoint> mapPoints = new ArrayList<>();
    int seq = 1;
    for (GeoPointDto p : geoPoints) {
      mapPoints.add(MapPathPoint.builder()
          .label(p.getLabel())
          .latitude(p.getLatitude())
          .longitude(p.getLongitude())
          .sequence(seq++)
          .build());
    }
    return mapPoints;
  }

  private List<BattleEvent> buildBattleTimeline(String category, String content) {
    if (!"warfare".equals(category)) {
      return Collections.emptyList();
    }

    // 使用LLM分析战役时间轴（强制调用API）
    try {
      JsonNode result = siliconFlowClient.analyzeBattleTimeline(content, category, null);
      if (result != null && result.has("battles") && result.get("battles").isArray()) {
        List<BattleEvent> events = new ArrayList<>();
        result.get("battles").forEach(item -> {
          events.add(BattleEvent.builder()
              .phase(item.path("phase").asText())
              .description(item.path("description").asText())
              .intensity(item.path("intensity").asInt(5))
              .opponent(item.path("opponent").asText(""))
              .build());
        });
        if (!events.isEmpty()) {
          log.info("战役时间轴构建完成：LLM返回{}个阶段", events.size());
          return events;
        }
      }
    } catch (Exception ex) {
      log.warn("LLM战役分析失败: {}", ex.getMessage());
    }

    // 失败时返回空列表，不返回默认数据
    log.info("战役时间轴构建失败，返回空列表");
    return Collections.emptyList();
  }

  /**
   * 从大模型分析的实体和关系中构建家族树
   */
  private List<FamilyNode> buildFamilyTree(String category, List<EntityAnnotation> entities, List<RelationAnnotation> relations) {
    if (!"biography".equals(category)) {
      return Collections.emptyList();
    }

    // 1. 找出所有人物实体
    List<EntityAnnotation> persons = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.PERSON)
        .collect(Collectors.toList());

    if (persons.isEmpty()) {
      return Collections.emptyList();
    }

    // 2. 找出所有家族关系
    List<RelationAnnotation> familyRelations = relations.stream()
        .filter(r -> r.getRelationType() == RelationType.FAMILY)
        .collect(Collectors.toList());

    if (familyRelations.isEmpty()) {
      // 如果没有家族关系，不返回任何节点（而不是显示"相关人物"）
      return Collections.emptyList();
    }

    // 3. 构建家族关系图
    Map<String, FamilyNodeBuilder> nodeMap = new HashMap<>();

    // 初始化人物节点（仅包含有家族关系的人物）
    Set<String> involvedPersons = new HashSet<>();
    for (RelationAnnotation relation : familyRelations) {
      if (relation.getSource() != null) {
        involvedPersons.add(relation.getSource().getLabel());
      }
      if (relation.getTarget() != null) {
        involvedPersons.add(relation.getTarget().getLabel());
      }
    }

    for (String personName : involvedPersons) {
      nodeMap.put(personName, new FamilyNodeBuilder(personName));
    }

    // 4. 处理家族关系
    for (RelationAnnotation relation : familyRelations) {
      if (relation.getSource() == null || relation.getTarget() == null) {
        continue;
      }

      String sourceName = relation.getSource().getLabel();
      String targetName = relation.getTarget().getLabel();
      String relationDesc = relation.getEvidence();

      if (nodeMap.containsKey(sourceName) && nodeMap.containsKey(targetName)) {
        FamilyNodeBuilder sourceNode = nodeMap.get(sourceName);
        FamilyNodeBuilder targetNode = nodeMap.get(targetName);

        // 根据关系描述判断亲属关系
        String relationType = inferRelationType(relationDesc, sourceName, targetName);

        // 只有明确的家族关系才构建树
        if (relationType.equals("相关")) {
          continue; // 跳过不明确的关系
        }

        // 添加父子关系
        if (relationType.contains("父") || relationType.contains("母") || relationType.contains("祖")) {
          sourceNode.addChild(targetNode, getChildRelation(relationType));
          targetNode.markAsChild();
        } else if (relationType.contains("子") || relationType.contains("女")) {
          targetNode.addChild(sourceNode, relationType);
          sourceNode.markAsChild();
        }
      }
    }

    // 5. 找出根节点（没有被标记为子节点的节点）
    List<FamilyNode> roots = new ArrayList<>();
    for (FamilyNodeBuilder builder : nodeMap.values()) {
      if (!builder.isChild()) {
        FamilyNode node = builder.build();
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
          roots.add(node);
        }
      }
    }

    return roots;
  }

  /**
   * 根据父辈关系推断子辈关系
   */
  private String getChildRelation(String parentRelation) {
    if (parentRelation.contains("父")) return "子";
    if (parentRelation.contains("母")) return "子";
    if (parentRelation.contains("祖父")) return "孙";
    if (parentRelation.contains("祖母")) return "孙";
    return "后代";
  }

  /**
   * 推断家族关系类型
   */
  private String inferRelationType(String description, String source, String target) {
    if (description == null) {
      return "相关";
    }

    String desc = description.toLowerCase();
    if (desc.contains("父") || desc.contains("father")) return "父";
    if (desc.contains("母") || desc.contains("mother")) return "母";
    if (desc.contains("子") || desc.contains("son")) return "子";
    if (desc.contains("女") || desc.contains("daughter")) return "女";
    if (desc.contains("兄") || desc.contains("brother")) return "兄";
    if (desc.contains("弟")) return "弟";
    if (desc.contains("姐") || desc.contains("sister")) return "姐";
    if (desc.contains("妹")) return "妹";
    if (desc.contains("妻") || desc.contains("wife")) return "妻";
    if (desc.contains("夫") || desc.contains("husband")) return "夫";
    if (desc.contains("祖父") || desc.contains("grandfather")) return "祖父";
    if (desc.contains("祖母") || desc.contains("grandmother")) return "祖母";
    if (desc.contains("孙") || desc.contains("grandson")) return "孙";

    return "相关";
  }

  /**
   * 家族节点构建器（用于构建树结构）
   */
  private static class FamilyNodeBuilder {
    private final String name;
    private String relation = "本人";
    private final List<FamilyNodeBuilder> children = new ArrayList<>();
    private boolean isChildNode = false;

    public FamilyNodeBuilder(String name) {
      this.name = name;
    }

    public void setRelation(String relation) {
      this.relation = relation;
    }

    public void addChild(FamilyNodeBuilder child, String childRelation) {
      child.setRelation(childRelation);
      children.add(child);
    }

    public List<FamilyNodeBuilder> getChildren() {
      return children;
    }

    public void markAsChild() {
      this.isChildNode = true;
    }

    public boolean isChild() {
      return isChildNode;
    }

    public FamilyNode build() {
      return FamilyNode.builder()
          .name(name)
          .relation(relation)
          .children(children.stream()
              .map(FamilyNodeBuilder::build)
              .collect(Collectors.toList()))
          .build();
    }
  }

  private List<String> buildRecommendedViews(String category) {
    return switch (category) {
      case "travelogue" -> List.of("地图", "时间轴", "词云");
      case "biography" -> List.of("时间轴", "亲情树", "知识图谱");
      case "official" -> List.of("官职树", "知识图谱", "时间轴");
      case "agriculture", "crafts" -> List.of("流程周期", "知识图谱", "词云");
      default -> List.of("知识图谱", "对抗视图", "统计图表");
    };
  }

  private String buildAnalysisSummary(TextDocument text, Stats stats) {
    String categoryLabel = CATEGORY_LABELS.getOrDefault(text.getCategory(), "综合");
    return String.format(Locale.CHINA,
        "模型建议该文本归类为「%s」，已完成 %d 个实体、%d 条关系。建议优先查看 %s 视图以洞察关键信息。",
        categoryLabel,
        stats.getEntityCount(),
        stats.getRelationCount(),
        String.join(" / ", buildRecommendedViews(text.getCategory())));
  }

  /**
   * 构建官职体系树状图
   * 使用大模型分析文本中的官职信息
   */
  private List<OfficialNode> buildOfficialTree(String category, String content, List<EntityAnnotation> entities, List<RelationAnnotation> relations) {
    if (!"official".equals(category)) {
      return Collections.emptyList();
    }

    // 1. 筛选出人物实体
    List<EntityAnnotation> persons = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.PERSON)
        .collect(Collectors.toList());

    if (persons.isEmpty()) {
      return Collections.emptyList();
    }

    // 2. 使用大模型分析官职信息
    List<String> personNames = persons.stream()
        .map(EntityAnnotation::getLabel)
        .collect(Collectors.toList());

    com.fasterxml.jackson.databind.JsonNode analysisResult = null;
    try {
      analysisResult = siliconFlowClient.analyzeOfficialPositions(content, personNames, null);
    } catch (Exception ex) {
      log.warn("Failed to analyze official positions with LLM: {}", ex.getMessage());
    }

    // 3. 构建官职节点映射
    Map<String, OfficialNodeBuilder> nodeMap = new HashMap<>();

    // 从大模型结果中提取官职信息
    if (analysisResult != null && analysisResult.has("officials") && analysisResult.get("officials").isArray()) {
      analysisResult.get("officials").forEach(official -> {
        String name = official.path("name").asText("");
        String position = official.path("position").asText("");
        String level = official.path("level").asText("未定品");
        String department = official.path("department").asText("中央机构");
        String description = official.path("description").asText("");
        String superior = official.path("superior").asText(null);

        // 只添加有明确官职的人物
        if (!name.isEmpty() && !position.isEmpty() && !position.equals("未知官职")) {
          OfficialNodeBuilder builder = new OfficialNodeBuilder(name, position, level, department);
          builder.setDescription(description);
          if (superior != null && !superior.isEmpty()) {
            builder.setSuperiorName(superior);
          }
          nodeMap.put(name, builder);
        }
      });
    }

    // 如果大模型没有返回结果，使用启发式方法
    if (nodeMap.isEmpty()) {
      for (EntityAnnotation person : persons) {
        String name = person.getLabel();
        String position = extractOfficialPosition(person, relations);

        // 过滤掉"未知官职"
        if (position != null && !position.equals("未知官职") && !position.equals("部门")) {
          String level = determineOfficialLevel(position);
          String department = extractDepartment(position);
          nodeMap.put(name, new OfficialNodeBuilder(name, position, level, department));
        }
      }
    }

    if (nodeMap.isEmpty()) {
      return Collections.emptyList();
    }

    // 4. 构建上下级关系
    // 首先处理大模型识别的上下级关系
    for (OfficialNodeBuilder builder : nodeMap.values()) {
      String superiorName = builder.getSuperiorName();
      if (superiorName != null && nodeMap.containsKey(superiorName)) {
        OfficialNodeBuilder superior = nodeMap.get(superiorName);
        superior.addSubordinate(builder);
        builder.markAsSubordinate();
      }
    }

    // 然后处理从关系中识别的上下级关系（PART_OF关系）
    for (RelationAnnotation relation : relations) {
      if (relation.getRelationType() == RelationType.PART_OF &&
          relation.getSource() != null && relation.getTarget() != null) {

        String subordinateName = relation.getSource().getLabel();
        String superiorName = relation.getTarget().getLabel();

        if (nodeMap.containsKey(subordinateName) && nodeMap.containsKey(superiorName)) {
          OfficialNodeBuilder superior = nodeMap.get(superiorName);
          OfficialNodeBuilder subordinate = nodeMap.get(subordinateName);
          if (!subordinate.isSubordinate()) {  // 避免重复添加
            superior.addSubordinate(subordinate);
            subordinate.markAsSubordinate();
          }
        }
      }
    }

    // 5. 找出根节点（高级官员）
    List<OfficialNode> roots = new ArrayList<>();
    for (OfficialNodeBuilder builder : nodeMap.values()) {
      if (!builder.isSubordinate()) {
        roots.add(builder.build());
      }
    }

    // 6. 如果没有明确的层级关系，按官职等级排序返回
    if (roots.isEmpty() || roots.stream().allMatch(n -> n.getSubordinates() == null || n.getSubordinates().isEmpty())) {
      return nodeMap.values().stream()
          .sorted((a, b) -> compareOfficialLevel(a.getLevel(), b.getLevel()))
          .limit(10)
          .map(OfficialNodeBuilder::build)
          .collect(Collectors.toList());
    }

    return roots;
  }

  /**
   * 官职节点构建器
   */
  private static class OfficialNodeBuilder {
    private final String name;
    private final String position;
    private final String level;
    private final String department;
    private String description;
    private String superiorName;
    private final List<OfficialNodeBuilder> subordinates = new ArrayList<>();
    private boolean isSubordinateNode = false;

    public OfficialNodeBuilder(String name, String position, String level, String department) {
      this.name = name;
      this.position = position;
      this.level = level;
      this.department = department;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public void setSuperiorName(String superiorName) {
      this.superiorName = superiorName;
    }

    public String getSuperiorName() {
      return superiorName;
    }

    public void addSubordinate(OfficialNodeBuilder subordinate) {
      subordinates.add(subordinate);
    }

    public void markAsSubordinate() {
      this.isSubordinateNode = true;
    }

    public boolean isSubordinate() {
      return isSubordinateNode;
    }

    public String getLevel() {
      return level;
    }

    public OfficialNode build() {
      String finalDescription = (description != null && !description.isEmpty())
          ? description
          : String.format("%s任%s，隶属%s", name, position, department);

      return OfficialNode.builder()
          .name(name)
          .position(position)
          .level(level)
          .department(department)
          .subordinates(subordinates.stream()
              .map(OfficialNodeBuilder::build)
              .collect(Collectors.toList()))
          .description(finalDescription)
          .build();
    }
  }

  /**
   * 从实体和关系中提取官职
   */
  private String extractOfficialPosition(EntityAnnotation entity, List<RelationAnnotation> relations) {
    // 从关系描述中提取官职信息
    for (RelationAnnotation relation : relations) {
      if ((relation.getSource() != null && relation.getSource().getId().equals(entity.getId())) ||
          (relation.getTarget() != null && relation.getTarget().getId().equals(entity.getId()))) {

        String evidence = relation.getEvidence();
        if (evidence != null) {
          // 匹配常见官职名称
          Pattern pattern = Pattern.compile("(太守|刺史|尚书|御史|知府|知县|县令|县丞|主簿|郎中|员外郎|侍郎|给事中|翰林|学士|大学士|阁老|宰相|丞相|太师|太傅|太保|司马|司徒|司空|都督|总兵|参将|游击|守备)");
          Matcher matcher = pattern.matcher(evidence);
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
      }
    }

    // 如果没有找到，使用默认值
    return entity.getCategory() == EntityCategory.ORGANIZATION ? "部门" : "未知官职";
  }

  /**
   * 确定官职等级
   */
  private String determineOfficialLevel(String position) {
    if (position.contains("宰相") || position.contains("丞相") || position.contains("太师") ||
        position.contains("太傅") || position.contains("太保") || position.contains("大学士")) {
      return "一品";
    } else if (position.contains("尚书") || position.contains("都督") || position.contains("总兵")) {
      return "二品";
    } else if (position.contains("侍郎") || position.contains("御史") || position.contains("参将")) {
      return "三品";
    } else if (position.contains("郎中") || position.contains("员外郎") || position.contains("游击")) {
      return "四品";
    } else if (position.contains("给事中") || position.contains("主簿") || position.contains("守备")) {
      return "五品";
    } else if (position.contains("知府") || position.contains("刺史")) {
      return "四品";
    } else if (position.contains("知县") || position.contains("县令")) {
      return "七品";
    } else if (position.contains("县丞")) {
      return "八品";
    }
    return "未定品";
  }

  /**
   * 提取部门信息
   */
  private String extractDepartment(String position) {
    if (position.contains("尚书")) return "六部";
    if (position.contains("御史")) return "都察院";
    if (position.contains("翰林") || position.contains("学士")) return "翰林院";
    if (position.contains("府") || position.contains("州") || position.contains("县")) return "地方政府";
    if (position.contains("军") || position.contains("兵") || position.contains("都督") || position.contains("总兵")) return "军事系统";
    return "中央机构";
  }

  /**
   * 比较官职等级
   */
  private int compareOfficialLevel(String level1, String level2) {
    Map<String, Integer> levelMap = Map.of(
        "一品", 1, "二品", 2, "三品", 3, "四品", 4, "五品", 5,
        "六品", 6, "七品", 7, "八品", 8, "九品", 9, "未定品", 10
    );
    return Integer.compare(
        levelMap.getOrDefault(level1, 10),
        levelMap.getOrDefault(level2, 10)
    );
  }

  /**
   * 构建流程周期图（用于农书和工艺类文本）
   */
  private List<ProcessStep> buildProcessCycle(String category, String content, List<EntityAnnotation> entities, List<RelationAnnotation> relations) {
    if (!"agriculture".equals(category) && !"crafts".equals(category)) {
      return Collections.emptyList();
    }

    // 使用LLM分析流程周期（强制调用API）
    String entityList = entities.stream()
        .limit(30)
        .map(EntityAnnotation::getLabel)
        .collect(Collectors.joining("、"));

    try {
      JsonNode result = siliconFlowClient.analyzeProcessCycle(content, category, entityList, null);
      if (result != null && result.has("steps") && result.get("steps").isArray()) {
        List<ProcessStep> steps = new ArrayList<>();
        result.get("steps").forEach(item -> {
          // 解析工具列表
          List<String> tools = new ArrayList<>();
          if (item.has("tools") && item.get("tools").isArray()) {
            item.get("tools").forEach(tool -> tools.add(tool.asText()));
          }
          if (tools.isEmpty()) tools.add("无");

          // 解析材料列表
          List<String> materials = new ArrayList<>();
          if (item.has("materials") && item.get("materials").isArray()) {
            item.get("materials").forEach(material -> materials.add(material.asText()));
          }
          if (materials.isEmpty()) materials.add("无");

          steps.add(ProcessStep.builder()
              .name(item.path("name").asText())
              .description(item.path("description").asText())
              .sequence(item.path("sequence").asInt(0))
              .category(item.path("category").asText(""))
              .tools(tools)
              .materials(materials)
              .output(item.path("output").asText(null))
              .duration(item.path("duration").asInt(1))
              .build());
        });
        if (!steps.isEmpty()) {
          log.info("流程周期构建完成：LLM返回{}个步骤", steps.size());
          return steps;
        }
      }
    } catch (Exception ex) {
      log.warn("LLM流程分析失败，使用降级方案: {}", ex.getMessage());
    }

    // 降级方案：从EVENT类型实体中提取流程步骤
    List<ProcessStep> steps = new ArrayList<>();
    List<EntityAnnotation> eventEntities = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.EVENT)
        .sorted((a, b) -> Integer.compare(a.getStartOffset(), b.getStartOffset()))
        .collect(Collectors.toList());

    int sequence = 1;
    for (EntityAnnotation event : eventEntities) {
      String stepName = event.getLabel();
      String description = extractText(content, event.getStartOffset(), event.getEndOffset());
      String stepCategory = determineStepCategory(stepName, category);

      // 提取相关的工具和材料
      List<String> tools = extractTools(event, relations, entities, content);
      List<String> materials = extractMaterials(event, relations, entities, content);
      String output = extractOutput(event, relations, entities);

      steps.add(ProcessStep.builder()
          .name(stepName)
          .description(description)
          .sequence(sequence++)
          .category(stepCategory)
          .tools(tools)
          .materials(materials)
          .output(output)
          .duration(estimateDuration(stepName, category))
          .build());
    }

    // 如果没有步骤，返回空列表，不返回默认数据
    if (steps.isEmpty()) {
      log.info("流程周期构建失败，返回空列表");
    }

    return steps;
  }

  /**
   * 确定步骤类别
   */
  private String determineStepCategory(String stepName, String category) {
    if ("agriculture".equals(category)) {
      if (stepName.contains("耕") || stepName.contains("翻")) return "整地";
      if (stepName.contains("播") || stepName.contains("种")) return "播种";
      if (stepName.contains("灌") || stepName.contains("浇")) return "灌溉";
      if (stepName.contains("施肥") || stepName.contains("粪")) return "施肥";
      if (stepName.contains("除草") || stepName.contains("锄")) return "田间管理";
      if (stepName.contains("收") || stepName.contains("割")) return "收获";
      return "其他";
    } else if ("crafts".equals(category)) {
      if (stepName.contains("选") || stepName.contains("备")) return "选材";
      if (stepName.contains("切") || stepName.contains("削") || stepName.contains("裁")) return "加工";
      if (stepName.contains("组装") || stepName.contains("拼")) return "组装";
      if (stepName.contains("打磨") || stepName.contains("修")) return "修整";
      if (stepName.contains("涂") || stepName.contains("漆") || stepName.contains("染")) return "装饰";
      if (stepName.contains("烧") || stepName.contains("炼") || stepName.contains("铸")) return "热处理";
      return "制作";
    }
    return "步骤";
  }

  /**
   * 提取工具信息
   */
  private List<String> extractTools(EntityAnnotation event, List<RelationAnnotation> relations, List<EntityAnnotation> entities, String content) {
    List<String> tools = new ArrayList<>();

    // 从关系中查找工具
    for (RelationAnnotation relation : relations) {
      if (relation.getSource() != null && relation.getSource().getId().equals(event.getId())) {
        EntityAnnotation target = relation.getTarget();
        if (target != null && target.getCategory() == EntityCategory.OBJECT) {
          tools.add(target.getLabel());
        }
      }
    }

    // 从周围文本中提取常见工具名称
    String context = extractContext(content, event.getStartOffset(), event.getEndOffset(), 50);
    Pattern toolPattern = Pattern.compile("(锄|犁|镰|耙|锹|铲|斧|锯|锤|凿|刨|钻|针|剪|刀|笔|砚)");
    Matcher matcher = toolPattern.matcher(context);
    while (matcher.find() && tools.size() < 3) {
      String tool = matcher.group(1);
      if (!tools.contains(tool)) {
        tools.add(tool);
      }
    }

    return tools.isEmpty() ? List.of("无") : tools;
  }

  /**
   * 提取材料信息
   */
  private List<String> extractMaterials(EntityAnnotation event, List<RelationAnnotation> relations, List<EntityAnnotation> entities, String content) {
    List<String> materials = new ArrayList<>();

    String context = extractContext(content, event.getStartOffset(), event.getEndOffset(), 50);

    // 农业材料
    Pattern agriPattern = Pattern.compile("(种子|秧苗|粪肥|水|土|稻|麦|粟|豆|菜)");
    Matcher agriMatcher = agriPattern.matcher(context);
    while (agriMatcher.find() && materials.size() < 3) {
      String material = agriMatcher.group(1);
      if (!materials.contains(material)) {
        materials.add(material);
      }
    }

    // 工艺材料
    Pattern craftPattern = Pattern.compile("(木|竹|石|铁|铜|布|丝|纸|泥|陶|瓷)");
    Matcher craftMatcher = craftPattern.matcher(context);
    while (craftMatcher.find() && materials.size() < 3) {
      String material = craftMatcher.group(1);
      if (!materials.contains(material)) {
        materials.add(material);
      }
    }

    return materials.isEmpty() ? List.of("无") : materials;
  }

  /**
   * 提取输出产品
   */
  private String extractOutput(EntityAnnotation event, List<RelationAnnotation> relations, List<EntityAnnotation> entities) {
    // 从关系中查找输出
    for (RelationAnnotation relation : relations) {
      if (relation.getSource() != null && relation.getSource().getId().equals(event.getId()) &&
          relation.getRelationType() == RelationType.CAUSE) {
        EntityAnnotation target = relation.getTarget();
        if (target != null) {
          return target.getLabel();
        }
      }
    }
    return null;
  }

  /**
   * 估算步骤耗时（天数）
   */
  private Integer estimateDuration(String stepName, String category) {
    if ("agriculture".equals(category)) {
      if (stepName.contains("播种")) return 3;
      if (stepName.contains("耕地")) return 5;
      if (stepName.contains("收获")) return 7;
      if (stepName.contains("施肥")) return 2;
      return 1;
    } else if ("crafts".equals(category)) {
      if (stepName.contains("选材")) return 1;
      if (stepName.contains("加工")) return 3;
      if (stepName.contains("打磨")) return 2;
      if (stepName.contains("装饰")) return 2;
      return 1;
    }
    return 1;
  }

  /**
   * 构建默认流程步骤
   */
  private List<ProcessStep> buildDefaultProcessSteps(String category) {
    if ("agriculture".equals(category)) {
      return List.of(
          ProcessStep.builder().name("整地").description("翻耕土地，清除杂草").sequence(1)
              .category("整地").tools(List.of("犁", "耙")).materials(List.of("土")).duration(5).build(),
          ProcessStep.builder().name("播种").description("选良种，按节气播种").sequence(2)
              .category("播种").tools(List.of("锄")).materials(List.of("种子")).output("秧苗").duration(3).build(),
          ProcessStep.builder().name("田间管理").description("灌溉、施肥、除草").sequence(3)
              .category("田间管理").tools(List.of("锄", "桶")).materials(List.of("水", "肥")).duration(30).build(),
          ProcessStep.builder().name("收获").description("成熟后收割晾晒").sequence(4)
              .category("收获").tools(List.of("镰刀")).output("谷物").duration(7).build()
      );
    } else if ("crafts".equals(category)) {
      return List.of(
          ProcessStep.builder().name("选材").description("挑选优质原料").sequence(1)
              .category("选材").materials(List.of("木材")).duration(1).build(),
          ProcessStep.builder().name("粗加工").description("裁切、刨削成型").sequence(2)
              .category("加工").tools(List.of("锯", "刨")).materials(List.of("木材")).duration(3).build(),
          ProcessStep.builder().name("精修").description("打磨光滑，修整细节").sequence(3)
              .category("修整").tools(List.of("砂纸", "锉")).duration(2).build(),
          ProcessStep.builder().name("装饰").description("涂漆或雕花装饰").sequence(4)
              .category("装饰").tools(List.of("刷", "刀")).materials(List.of("漆")).output("成品").duration(2).build()
      );
    }
    return Collections.emptyList();
  }

  private List<AnnotationEntity> buildHeuristicEntities(String content) {
    if (content == null || content.isBlank()) {
      return Collections.emptyList();
    }
    Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,3}");
    Matcher matcher = pattern.matcher(content);
    Set<String> labels = new LinkedHashSet<>();
    while (matcher.find() && labels.size() < 12) {
      String token = matcher.group();
      if (token.length() >= 2) {
        labels.add(token);
      }
    }
    return labels.stream()
        .limit(12)
        .map(label -> AnnotationEntity.builder()
            .label(label)
            .category("PERSON")
            .startOffset(0)
            .endOffset(0)
            .confidence(0.5)
            .build())
        .collect(Collectors.toList());
  }
}
