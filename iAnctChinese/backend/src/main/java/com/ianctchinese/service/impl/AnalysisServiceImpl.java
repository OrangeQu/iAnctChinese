package com.ianctchinese.service.impl;

import com.ianctchinese.dto.AutoAnnotationResponse;
import com.ianctchinese.dto.ClassificationResponse;
import com.ianctchinese.dto.ModelAnalysisResponse;
import com.ianctchinese.dto.SentenceSegmentRequest;
import com.ianctchinese.dto.TextInsightsResponse;
import com.ianctchinese.dto.TextInsightsResponse.BattleEvent;
import com.ianctchinese.dto.TextInsightsResponse.FamilyNode;
import com.ianctchinese.dto.TextInsightsResponse.MapPathPoint;
import com.ianctchinese.dto.TextInsightsResponse.Stats;
import com.ianctchinese.dto.TextInsightsResponse.TimelineEvent;
import com.ianctchinese.dto.TextInsightsResponse.WordCloudItem;
import com.ianctchinese.llm.SiliconFlowClient;
import com.ianctchinese.llm.dto.AnnotationPayload;
import com.ianctchinese.llm.dto.AnnotationPayload.AnnotationEntity;
import com.ianctchinese.llm.dto.AnnotationPayload.AnnotationRelation;
import com.ianctchinese.llm.dto.ClassificationPayload;
import com.ianctchinese.llm.dto.SentenceSuggestion;
import com.ianctchinese.llm.dto.TimelineEnrichment;
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

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisServiceImpl implements AnalysisService {

  private static final Map<String, String> CATEGORY_LABELS = Map.of(
      "warfare", "战争纪实",
      "travelogue", "游记地理",
      "biography", "人物传记",
      "other", "其他",
      "unknown", "综合待识别"
  );

  private final TextDocumentRepository textDocumentRepository;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;
  private final TextSectionRepository textSectionRepository;
  private final TextSectionService textSectionService;
  private final SiliconFlowClient siliconFlowClient;

  @Value("${analysis.timeline.enable-llm-enrichment:false}")
  private boolean enableLlmEnrichment;

  @Value("${analysis.timeline.llm-enrichment-limit:3}")
  private int llmEnrichmentLimit;

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
  public TextInsightsResponse buildInsights(Long textId) {
    TextDocument text = loadText(textId);
    List<EntityAnnotation> entities = entityAnnotationRepository.findByTextDocumentId(textId);
    List<RelationAnnotation> relations = relationAnnotationRepository.findByTextDocumentId(textId);
    List<TextSection> sections = textSectionRepository.findByTextDocumentId(textId);

    Stats stats = Stats.builder()
        .entityCount(entities.size())
        .relationCount(relations.size())
        .punctuationProgress(calculatePunctuationProgress(sections))
        .build();

    List<WordCloudItem> wordCloud = buildWordCloud(entities, text.getContent());
    List<TimelineEvent> timeline = buildTimelineForCategory(text, entities, relations);
    List<MapPathPoint> mapPoints = buildMapPointsForCategory(text.getCategory());
    List<String> recommendedViews = buildRecommendedViews(text.getCategory());
    List<BattleEvent> battleTimeline = buildBattleTimeline(text.getCategory());
    List<FamilyNode> familyTree = buildFamilyTree(text.getCategory());

    return TextInsightsResponse.builder()
        .textId(textId)
        .category(text.getCategory())
        .stats(stats)
        .wordCloud(wordCloud)
        .timeline(timeline)
        .mapPoints(mapPoints)
        .battleTimeline(battleTimeline)
        .familyTree(familyTree)
        .recommendedViews(recommendedViews)
        .analysisSummary(buildAnalysisSummary(text, stats))
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
    TextDocument document = loadText(textId);
    ClassificationPayload clsPayload = classifyWithFallback(document.getContent(), model);
    String normalizedCategory = normalizeCategory(clsPayload.getCategory(), document.getCategory());
    document.setCategory(normalizedCategory);
    textDocumentRepository.save(document);

    AnnotationPayload annPayload = siliconFlowClient.annotateText(document.getContent(), model);
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

    TextInsightsResponse insights = buildInsights(textId);
    List<TextSection> sections = textSectionRepository.findByTextDocumentId(textId);
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
      case "warfare", "travelogue", "biography", "other" -> normalized;
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
      case "CONFLICT" -> RelationType.CONFLICT;
      case "SUPPORT" -> RelationType.SUPPORT;
      case "TRAVEL" -> RelationType.TRAVEL;
      case "FAMILY" -> RelationType.FAMILY;
      case "TEMPORAL" -> RelationType.TEMPORAL;
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

    // 回退到基于文本的简易词频
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

  private List<TimelineEvent> buildTimelineForCategory(TextDocument text,
      List<EntityAnnotation> entities, List<RelationAnnotation> relations) {

    List<TimelineEvent> events = new ArrayList<>();

    // 1. 优先使用EVENT类型的实体
    List<EntityAnnotation> eventEntities = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.EVENT)
        .sorted(Comparator.comparing(EntityAnnotation::getStartOffset))
        .collect(Collectors.toList());

    // 从EVENT实体构建时间轴
    for (EntityAnnotation event : eventEntities) {
      events.add(buildTimelineEventFromEntity(event, entities, relations, text));
    }

    // 2. 如果EVENT实体少于3个，补充PERSON和LOCATION实体
    if (events.size() < 3) {
      List<EntityAnnotation> supplementaryEntities = entities.stream()
          .filter(e -> e.getCategory() == EntityCategory.PERSON ||
                       e.getCategory() == EntityCategory.LOCATION ||
                       e.getCategory() == EntityCategory.ORGANIZATION)
          .filter(e -> !eventEntities.contains(e))
          .sorted(Comparator.comparing(EntityAnnotation::getStartOffset))
          .limit(5 - events.size())
          .collect(Collectors.toList());

      for (EntityAnnotation entity : supplementaryEntities) {
        events.add(buildTimelineEventFromEntity(entity, entities, relations, text));
      }
    }

    // 3. 如果还是太少（少于2个），使用默认数据
    if (events.size() < 2) {
      events = buildDefaultTimelineForCategory(text.getCategory());
    }

    // 4. 按照startOffset排序
    events.sort(Comparator.comparing(e -> e.getStartOffset() != null ? e.getStartOffset() : 0));

    // 5. 确保时间标签唯一性和智能性
    assignUniqueTimeLabels(events, text.getContent());

    // 6. 可选：对重要事件进行LLM增强（混合方案）
    if (enableLlmEnrichment && !events.isEmpty()) {
      enrichEventsWithLlm(events, text, entities, relations);
    }

    return events;
  }

  /**
   * 从实体构建时间轴事件
   */
  private TimelineEvent buildTimelineEventFromEntity(EntityAnnotation entity,
      List<EntityAnnotation> entities, List<RelationAnnotation> relations, TextDocument text) {

    // TODO: LLM增强分析暂时禁用以提升性能
    // 未来可以考虑异步调用、批量处理或缓存策略
    /*
    String context = extractExtendedContext(entity, text.getContent(), 150);
    TimelineEnrichment enrichment = siliconFlowClient.enrichTimelineEvent(
        entity.getLabel(), context, text.getCategory(), null
    );
    */

    // 使用快速的规则方法
    String location = findRelatedLocation(entity, entities, relations);
    List<String> participants = findRelatedPersons(entity, entities, relations);
    String impact = generateImpact(entity, relations);
    String description = extractDescription(entity, text.getContent());

    // 根据文章类型决定时间轴节点的标注方式
    String dateLabel = extractLabelByCategory(entity, text.getCategory(), text.getContent(), location);

    Integer significance = calculateSignificance(entity, relations);
    List<String> relatedEvents = findRelatedEvents(entity, entities, relations);

    return TimelineEvent.builder()
        .title(entity.getLabel())
        .description(description)
        .dateLabel(dateLabel)
        .significance(significance)
        .eventType(determineEventType(entity, text.getCategory()))
        .location(location != null ? location : "未注明")
        .participants(participants)
        .impact(impact)
        .relatedEvents(relatedEvents)
        .entityId(entity.getId())
        .startOffset(entity.getStartOffset())
        .endOffset(entity.getEndOffset())
        .build();
  }

  /**
   * 根据文章类型提取时间轴节点的标注
   */
  private String extractLabelByCategory(EntityAnnotation entity, String category, String content, String location) {
    return switch (category) {
      case "biography" -> {
        // 传记：优先使用时间标注
        String timeLabel = extractDateLabel(entity, content);
        yield timeLabel;
      }
      case "travelogue" -> {
        // 游记：优先使用地点标注
        if (location != null && !location.isBlank() && !"未注明".equals(location)) {
          yield location;
        }
        // 回退到时间
        String timeLabel = extractDateLabel(entity, content);
        yield timeLabel;
      }
      case "warfare" -> {
        // 战争：优先使用战事阶段，其次时间
        String phase = extractWarfarePhase(entity, content);
        if (phase != null && !phase.isBlank()) {
          yield phase;
        }
        String timeLabel = extractDateLabel(entity, content);
        yield timeLabel;
      }
      default -> {
        // 其他类型：使用时间
        String timeLabel = extractDateLabel(entity, content);
        yield timeLabel;
      }
    };
  }

  /**
   * 提取战事阶段关键词
   */
  private String extractWarfarePhase(EntityAnnotation entity, String content) {
    int start = Math.max(0, entity.getStartOffset() - 50);
    int end = Math.min(content.length(), entity.getEndOffset() + 50);
    String context = content.substring(start, end);

    // 战事阶段关键词
    String[] phaseKeywords = {
        "集结", "起兵", "出征", "备战", "列阵", "对峙",
        "交战", "激战", "鏖战", "血战", "决战", "冲锋",
        "溃败", "撤退", "败北", "收兵", "班师", "凯旋",
        "议和", "休战", "结盟", "投降"
    };

    for (String keyword : phaseKeywords) {
      if (context.contains(keyword)) {
        return keyword;
      }
    }

    // 检查实体标签本身
    String label = entity.getLabel();
    if (label != null) {
      for (String keyword : phaseKeywords) {
        if (label.contains(keyword)) {
          return keyword;
        }
      }
    }

    return null;
  }

  /**
   * 提取扩展上下文（暂未使用，为LLM增强预留）
   */
  @SuppressWarnings("unused")
  private String extractExtendedContext(EntityAnnotation entity, String content, int range) {
    int start = Math.max(0, entity.getStartOffset() - range);
    int end = Math.min(content.length(), entity.getEndOffset() + range);
    return content.substring(start, end);
  }

  /**
   * 为时间轴事件生成唯一的时间标签（改进版）
   */
  private void assignUniqueTimeLabels(List<TimelineEvent> events, String content) {
    Set<String> usedLabels = new HashSet<>();
    Map<String, Integer> labelCount = new HashMap<>();

    for (int i = 0; i < events.size(); i++) {
      TimelineEvent event = events.get(i);
      String originalLabel = event.getDateLabel();

      // 如果时间标签已被使用或为"未注明"，尝试生成新的
      if (usedLabels.contains(originalLabel) || "未注明".equals(originalLabel)) {
        String newLabel = generateAlternativeTimeLabel(event, i, events.size(), content, usedLabels);

        // 确保新标签也不重复
        while (usedLabels.contains(newLabel)) {
          int count = labelCount.getOrDefault(newLabel, 1) + 1;
          labelCount.put(newLabel, count);
          newLabel = newLabel + "·" + count;
        }

        event.setDateLabel(newLabel);
      }

      usedLabels.add(event.getDateLabel());
    }
  }

  /**
   * 生成替代时间标签（改进版）
   */
  private String generateAlternativeTimeLabel(TimelineEvent event, int index, int total,
      String content, Set<String> usedLabels) {

    // 1. 尝试使用事件标题本身（如果它简短且有意义）
    String title = event.getTitle();
    if (title != null && title.length() >= 2 && title.length() <= 6 && !usedLabels.contains(title)) {
      // 检查标题是否包含时间词汇
      if (containsDatePattern(title)) {
        return title;
      }
      // 或者包含阶段性词汇
      if (title.matches(".*(初|始|终|末|前|后|中).+")) {
        return title;
      }
    }

    // 2. 尝试从上下文找相对时间词（扩大范围到150字符）
    if (event.getStartOffset() != null && event.getEndOffset() != null) {
      int start = Math.max(0, event.getStartOffset() - 150);
      int end = Math.min(content.length(), event.getEndOffset() + 150);
      String context = content.substring(start, end);

      // 查找相对时间词（优先级排序）
      String[] highPriorityTimeWords = {
          "元年", "初年", "末年", "翌年", "明年", "来年", "是年", "是岁"
      };
      for (String word : highPriorityTimeWords) {
        if (context.contains(word) && !usedLabels.contains(word)) {
          return word;
        }
      }

      String[] mediumPriorityTimeWords = {
          "岁首", "岁末", "年初", "年中", "年终",
          "初春", "仲春", "暮春", "初夏", "仲夏", "暮夏",
          "初秋", "仲秋", "暮秋", "初冬", "仲冬", "暮冬"
      };
      for (String word : mediumPriorityTimeWords) {
        if (context.contains(word) && !usedLabels.contains(word)) {
          return word;
        }
      }

      String[] relativeTimeWords = {
          "既而", "其后", "后", "先", "前", "次", "再",
          "终", "末", "久之", "俄而", "少顷", "翌日",
          "当时", "时", "既", "初", "始"
      };
      for (String word : relativeTimeWords) {
        if (context.contains(word) && !usedLabels.contains(word)) {
          return word;
        }
      }
    }

    // 3. 使用改进的序数表达（更符合古文习惯）
    String[] ordinals = {"起", "次", "又", "复", "再", "继", "后", "末"};
    if (index < ordinals.length && !usedLabels.contains(ordinals[index])) {
      return ordinals[index];
    }

    // 4. 使用阶段表达（基于位置）
    double position = total > 1 ? (double) index / (total - 1) : 0.5;
    if (position < 0.25) {
      return !usedLabels.contains("前期") ? "前期" : "序";
    } else if (position < 0.5) {
      return !usedLabels.contains("中前期") ? "中前期" : "承";
    } else if (position < 0.75) {
      return !usedLabels.contains("中后期") ? "中后期" : "转";
    } else {
      return !usedLabels.contains("后期") ? "后期" : "合";
    }
  }

  private List<MapPathPoint> buildMapPointsForCategory(String category) {
    if (!"travelogue".equals(category)) {
      return Collections.emptyList();
    }
    return List.of(
        MapPathPoint.builder().label("长安").latitude(34.3416).longitude(108.9398).sequence(1).build(),
        MapPathPoint.builder().label("襄阳").latitude(32.065).longitude(112.153).sequence(2).build(),
        MapPathPoint.builder().label("武夷山").latitude(27.728).longitude(118.035).sequence(3).build(),
        MapPathPoint.builder().label("建康").latitude(32.058).longitude(118.796).sequence(4).build()
    );
  }

  private List<BattleEvent> buildBattleTimeline(String category) {
    if (!"warfare".equals(category)) {
      return Collections.emptyList();
    }
    return List.of(
        BattleEvent.builder()
            .phase("先声夺人")
            .description("奇兵夜袭，对手措手不及")
            .intensity(6)
            .opponent("北军")
            .build(),
        BattleEvent.builder()
            .phase("火攻突袭")
            .description("顺风纵火，焚毁敌营")
            .intensity(9)
            .opponent("曹营")
            .build(),
        BattleEvent.builder()
            .phase("追击与议和")
            .description("乘胜北伐，并商议城下之盟")
            .intensity(7)
            .opponent("东路军")
            .build()
    );
  }

  private List<FamilyNode> buildFamilyTree(String category) {
    if (!"biography".equals(category)) {
      return Collections.emptyList();
    }
    return List.of(
        FamilyNode.builder()
            .name("祖父")
            .relation("祖")
            .children(List.of(
                FamilyNode.builder()
                    .name("父亲")
                    .relation("父")
                    .children(List.of(
                        FamilyNode.builder()
                            .name("主人公")
                            .relation("本人")
                            .children(List.of(
                                FamilyNode.builder().name("长子").relation("子").children(List.of()).build(),
                                FamilyNode.builder().name("次女").relation("女").children(List.of()).build()
                            ))
                            .build()
                    ))
                    .build()
            ))
            .build()
    );
  }

  private List<String> buildRecommendedViews(String category) {
    return switch (category) {
      case "travelogue" -> List.of("地图", "时间轴", "词云");
      case "biography" -> List.of("时间轴", "亲情树", "知识图谱");
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

  /**
   * 查找与事件相关的地点
   */
  private String findRelatedLocation(EntityAnnotation event,
      List<EntityAnnotation> entities, List<RelationAnnotation> relations) {

    // 1. 从关系中查找关联的地点
    String locationFromRelation = relations.stream()
        .filter(r -> r.getSource().getId().equals(event.getId()) ||
                     r.getTarget().getId().equals(event.getId()))
        .flatMap(r -> Stream.of(r.getSource(), r.getTarget()))
        .filter(e -> e.getCategory() == EntityCategory.LOCATION)
        .map(EntityAnnotation::getLabel)
        .findFirst()
        .orElse(null);

    if (locationFromRelation != null) {
      return locationFromRelation;
    }

    // 2. 从事件标签本身提取地点
    String label = event.getLabel();
    if (label != null) {
      for (EntityAnnotation entity : entities) {
        if (entity.getCategory() == EntityCategory.LOCATION &&
            label.contains(entity.getLabel())) {
          return entity.getLabel();
        }
      }
    }

    // 3. 从上下文查找最近的地点实体
    int eventPosition = event.getStartOffset();
    EntityAnnotation nearestLocation = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.LOCATION)
        .filter(e -> Math.abs(e.getStartOffset() - eventPosition) < 100)
        .min((a, b) -> Integer.compare(
            Math.abs(a.getStartOffset() - eventPosition),
            Math.abs(b.getStartOffset() - eventPosition)
        ))
        .orElse(null);

    if (nearestLocation != null) {
      return nearestLocation.getLabel();
    }

    return null;
  }

  /**
   * 查找与事件相关的人物
   */
  private List<String> findRelatedPersons(EntityAnnotation event,
      List<EntityAnnotation> entities, List<RelationAnnotation> relations) {
    return relations.stream()
        .filter(r -> r.getSource().getId().equals(event.getId()) ||
                     r.getTarget().getId().equals(event.getId()))
        .flatMap(r -> Stream.of(r.getSource(), r.getTarget()))
        .filter(e -> e.getCategory() == EntityCategory.PERSON)
        .map(EntityAnnotation::getLabel)
        .distinct()
        .limit(5)
        .collect(Collectors.toList());
  }

  /**
   * 从事件上下文中提取时间标签（增强版）
   */
  private String extractDateLabel(EntityAnnotation event, String content) {
    // 先从实体标签本身提取
    String label = event.getLabel();
    if (label != null && containsDatePattern(label)) {
      String date = extractDateFromText(label);
      if (date != null) {
        return date;
      }
    }

    // 从上下文中提取（扩大窗口到100字符）
    int start = Math.max(0, event.getStartOffset() - 100);
    int end = Math.min(content.length(), event.getEndOffset() + 100);
    String context = content.substring(start, end);

    String dateFromContext = extractDateFromText(context);
    if (dateFromContext != null) {
      return dateFromContext;
    }

    // 尝试从更大的上下文提取（200字符）
    start = Math.max(0, event.getStartOffset() - 200);
    end = Math.min(content.length(), event.getEndOffset() + 200);
    String widerContext = content.substring(start, end);

    String dateFromWiderContext = extractDateFromText(widerContext);
    if (dateFromWiderContext != null) {
      return dateFromWiderContext;
    }

    return "未注明";
  }

  /**
   * 检查文本是否包含时间模式（增强版）
   */
  private boolean containsDatePattern(String text) {
    if (text == null) return false;
    // 扩展的时间模式
    Pattern pattern = Pattern.compile(
        "(\\d+年|[一二三四五六七八九十百千万]+年|" +
        "初春|仲春|暮春|季春|孟春|" +
        "初夏|仲夏|暮夏|季夏|孟夏|" +
        "初秋|仲秋|暮秋|季秋|孟秋|" +
        "初冬|仲冬|暮冬|季冬|孟冬|" +
        "[正二三四五六七八九十冬腊]+月|" +
        "望日|朔日|晦日|上旬|中旬|下旬|" +
        "元年|[二三四五六七八九十百]+年|" +
        "春夏秋冬|岁首|岁末|年初|年终|" +
        "甲子|乙丑|丙寅|丁卯|戊辰|己巳|庚午|辛未|壬申|癸酉|甲戌|乙亥|丙子|丁丑|戊寅|己卯|庚辰|辛巳|壬午|癸未|甲申|乙酉|丙戌|丁亥|戊子|己丑|庚寅|辛卯|壬辰|癸巳|甲午|乙未|丙申|丁酉|戊戌|己亥|庚子|辛丑|壬寅|癸卯|甲辰|乙巳|丙午|丁未|戊申|己酉|庚戌|辛亥|壬子|癸丑|" +
        "初|既而|其后|后|先|前|次|再|终|末|久之|俄而|少顷|翌日|翌年|明年|来年|是年|是岁|当时|时|既|" +
        "建安|延熹|永平|光和|初平|兴平|建宁|熹平|中平|黄初|太和|青龙|景初|正始|嘉平|正元|景元|咸熙|" +
        "泰始|咸宁|太康|太熙|永熙|永嘉|建兴|太兴|永昌|" +
        "贞观|开元|天宝|至德|乾元|永泰|大历|建中|兴元|贞元|元和|长庆|宝历|太和|开成|会昌|大中|" +
        "洪武|永乐|宣德|正统|景泰|天顺|成化|弘治|正德|嘉靖|隆庆|万历|泰昌|天启|崇祯|" +
        "顺治|康熙|雍正|乾隆|嘉庆|道光|咸丰|同治|光绪|宣统)"
    );
    return pattern.matcher(text).find();
  }

  /**
   * 从文本中提取时间表达（增强版）
   */
  private String extractDateFromText(String text) {
    // 优先匹配具体年份和年号
    Pattern specificPattern = Pattern.compile(
        "((?:建安|延熹|永平|光和|初平|兴平|建宁|熹平|中平|黄初|太和|青龙|景初|正始|嘉平|正元|景元|咸熙|" +
        "泰始|咸宁|太康|太熙|永熙|永嘉|建兴|太兴|永昌|" +
        "贞观|开元|天宝|至德|乾元|永泰|大历|建中|兴元|贞元|元和|长庆|宝历|太和|开成|会昌|大中|" +
        "洪武|永乐|宣德|正统|景泰|天顺|成化|弘治|正德|嘉靖|隆庆|万历|泰昌|天启|崇祯|" +
        "顺治|康熙|雍正|乾隆|嘉庆|道光|咸丰|同治|光绪|宣统)[一二三四五六七八九十百千万元]?[十]?[一二三四五六七八九]?年)|" +
        "(\\d+年)|([一二三四五六七八九十百千万]+年)|" +
        "([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥]年?)|" +
        "(元年)"
    );
    Matcher specificMatcher = specificPattern.matcher(text);
    if (specificMatcher.find()) {
      return specificMatcher.group(0);
    }

    // 匹配季节和月份
    Pattern seasonPattern = Pattern.compile(
        "(初春|仲春|暮春|季春|孟春|初夏|仲夏|暮夏|季夏|孟夏|" +
        "初秋|仲秋|暮秋|季秋|孟秋|初冬|仲冬|暮冬|季冬|孟冬|" +
        "[正二三四五六七八九十冬腊]+月|春夏秋冬)"
    );
    Matcher seasonMatcher = seasonPattern.matcher(text);
    if (seasonMatcher.find()) {
      return seasonMatcher.group(1);
    }

    // 匹配具体日期
    Pattern dayPattern = Pattern.compile("(望日|朔日|晦日|上旬|中旬|下旬)");
    Matcher dayMatcher = dayPattern.matcher(text);
    if (dayMatcher.find()) {
      return dayMatcher.group(1);
    }

    // 匹配相对时间词
    Pattern relativePattern = Pattern.compile(
        "(岁首|岁末|年初|年终|初|既而|其后|翌日|翌年|明年|来年|是年|是岁)"
    );
    Matcher relativeMatcher = relativePattern.matcher(text);
    if (relativeMatcher.find()) {
      return relativeMatcher.group(1);
    }

    return null;
  }

  /**
   * 计算事件的重要程度（1-10）
   */
  private Integer calculateSignificance(EntityAnnotation event,
      List<RelationAnnotation> relations) {
    long relatedCount = relations.stream()
        .filter(r -> r.getSource().getId().equals(event.getId()) ||
                     r.getTarget().getId().equals(event.getId()))
        .count();

    // 根据关联数量计算重要程度
    return Math.min(10, Math.max(3, (int) (relatedCount + 3)));
  }

  /**
   * 查找关联事件
   */
  private List<String> findRelatedEvents(EntityAnnotation event,
      List<EntityAnnotation> entities, List<RelationAnnotation> relations) {
    return relations.stream()
        .filter(r -> r.getSource().getId().equals(event.getId()) ||
                     r.getTarget().getId().equals(event.getId()))
        .filter(r -> r.getRelationType() == RelationType.TEMPORAL)
        .flatMap(r -> Stream.of(r.getSource(), r.getTarget()))
        .filter(e -> e.getCategory() == EntityCategory.EVENT &&
                     !e.getId().equals(event.getId()))
        .map(EntityAnnotation::getLabel)
        .distinct()
        .limit(3)
        .collect(Collectors.toList());
  }

  /**
   * 生成事件影响描述（改进版：基于多个维度生成更多样化的描述）
   */
  private String generateImpact(EntityAnnotation event, List<RelationAnnotation> relations) {
    // 统计相关关系
    List<RelationAnnotation> relatedRelations = relations.stream()
        .filter(r -> r.getSource().getId().equals(event.getId()) ||
                     r.getTarget().getId().equals(event.getId()))
        .collect(Collectors.toList());

    long conflictCount = relatedRelations.stream()
        .filter(r -> r.getRelationType() == RelationType.CONFLICT)
        .count();

    long supportCount = relatedRelations.stream()
        .filter(r -> r.getRelationType() == RelationType.SUPPORT)
        .count();

    long familyCount = relatedRelations.stream()
        .filter(r -> r.getRelationType() == RelationType.FAMILY)
        .count();

    long travelCount = relatedRelations.stream()
        .filter(r -> r.getRelationType() == RelationType.TRAVEL)
        .count();

    long temporalCount = relatedRelations.stream()
        .filter(r -> r.getRelationType() == RelationType.TEMPORAL)
        .count();

    int totalRelations = relatedRelations.size();
    String label = event.getLabel().toLowerCase();
    EntityCategory category = event.getCategory();

    // 基于关系类型和数量生成描述
    if (conflictCount > 2) {
      return "此事件引发激烈冲突，深刻影响各方势力格局，成为历史转折的关键节点";
    } else if (conflictCount > 0) {
      return "此事件涉及多方势力的对抗，对当时局势产生重要影响";
    }

    if (supportCount > 2) {
      return "此事件促成多方结盟协作，为后续发展奠定坚实基础，影响深远";
    } else if (supportCount > 0) {
      return "此事件加强了各方联系，推动了合作关系的建立";
    }

    if (familyCount > 1) {
      return "此事件关系到家族兴衰，对宗族传承和血脉延续具有重要意义";
    }

    if (travelCount > 1) {
      return "此次游历留下珍贵的地理人文记录，丰富了对山川形胜的认知";
    }

    if (temporalCount > 2) {
      return "此事件与前后诸多事件紧密关联，在历史进程中起到承上启下的作用";
    }

    // 基于实体类别生成描述
    if (category == EntityCategory.EVENT) {
      if (totalRelations > 3) {
        return "此事件牵涉广泛，影响深远，是理解当时历史背景的重要线索";
      } else if (totalRelations > 1) {
        return "此事件具有一定影响力，在当时引起关注";
      } else {
        return "此事件在历史记载中占有一席之地，具有参考价值";
      }
    }

    if (category == EntityCategory.PERSON) {
      if (label.contains("生") || label.contains("诞")) {
        return "其人出生于斯，少有奇志，为日后成就埋下伏笔";
      } else if (label.contains("卒") || label.contains("殁") || label.contains("逝")) {
        return "其人去世，时人惋惜，一生功业留待后人评说";
      } else if (label.contains("官") || label.contains("仕")) {
        return "入仕为官，开启仕途生涯，影响政局走向";
      } else {
        return "此人在历史叙事中扮演重要角色，值得关注";
      }
    }

    if (category == EntityCategory.LOCATION) {
      if (label.contains("战") || label.contains("攻") || label.contains("守")) {
        return "此地成为军事要冲，战略位置显著，影响战局发展";
      } else {
        return "此地在历史地理中占有重要位置，具有研究价值";
      }
    }

    // 基于标签关键词生成描述
    if (label.contains("战") || label.contains("攻") || label.contains("破")) {
      return "此战影响战局走向，双方实力对比发生变化";
    }

    if (label.contains("会") || label.contains("盟") || label.contains("约")) {
      return "此次会面促成重要协议，对后续发展产生深远影响";
    }

    if (label.contains("建") || label.contains("立") || label.contains("创")) {
      return "此举开创新局，为后世留下重要遗产";
    }

    if (label.contains("变") || label.contains("革") || label.contains("改")) {
      return "此次变革触动既有格局，推动历史进程向前发展";
    }

    // 默认描述（根据关系数量分级）
    if (totalRelations > 4) {
      return "此事件关联广泛，在历史脉络中具有重要地位";
    } else if (totalRelations > 2) {
      return "此事件与诸多要素相关，对理解历史全貌有所助益";
    } else if (totalRelations > 0) {
      return "此事件在历史记述中有所体现，具有一定参考意义";
    } else {
      return "此事件虽关联有限，但仍为历史拼图的一部分";
    }
  }

  /**
   * 从事件周围提取描述文本
   */
  private String extractDescription(EntityAnnotation event, String content) {
    int start = Math.max(0, event.getStartOffset() - 20);
    int end = Math.min(content.length(), event.getEndOffset() + 40);
    String context = content.substring(start, end);

    // 清理并返回上下文
    return context.replaceAll("\\s+", "").substring(0, Math.min(context.length(), 60)) + "...";
  }

  /**
   * 当没有EVENT实体时的备用时间轴构建
   */
  private List<TimelineEvent> buildFallbackTimeline(List<EntityAnnotation> entities,
      List<RelationAnnotation> relations, String content, String category) {

    // 使用PERSON或LOCATION实体构建时间轴
    List<EntityAnnotation> candidateEntities = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.PERSON ||
                     e.getCategory() == EntityCategory.LOCATION)
        .sorted(Comparator.comparing(EntityAnnotation::getStartOffset))
        .limit(8)
        .collect(Collectors.toList());

    List<TimelineEvent> events = new ArrayList<>();
    for (EntityAnnotation entity : candidateEntities) {
      String dateLabel = extractDateLabel(entity, content);
      String location = entity.getCategory() == EntityCategory.LOCATION ?
          entity.getLabel() : findRelatedLocation(entity, entities, relations);

      events.add(TimelineEvent.builder()
          .title(entity.getLabel())
          .description(extractDescription(entity, content))
          .dateLabel(dateLabel)
          .significance(calculateSignificance(entity, relations))
          .eventType(determineEventType(entity, category))
          .location(location != null ? location : "未注明")
          .participants(findRelatedPersons(entity, entities, relations))
          .impact(generateImpact(entity, relations))
          .relatedEvents(Collections.emptyList())
          .entityId(entity.getId())
          .startOffset(entity.getStartOffset())
          .endOffset(entity.getEndOffset())
          .build());
    }

    // 如果还是为空，使用类别默认数据
    if (events.isEmpty()) {
      return buildDefaultTimelineForCategory(category);
    }

    return events;
  }

  /**
   * 构建默认时间轴（当完全没有实体时）
   */
  private List<TimelineEvent> buildDefaultTimelineForCategory(String category) {
    List<TimelineEvent> events = new ArrayList<>();
    switch (category) {
      case "travelogue" -> events.addAll(List.of(
          TimelineEvent.builder()
              .title("启程")
              .description("作者离开京师，溯江而上")
              .dateLabel("初春")
              .significance(6)
              .eventType("travel")
              .location("京师")
              .participants(Collections.emptyList())
              .impact("开启了一段重要的游历")
              .relatedEvents(Collections.emptyList())
              .build(),
          TimelineEvent.builder()
              .title("抵达名山")
              .description("记述山川胜景与碑刻")
              .dateLabel("暮春")
              .significance(8)
              .eventType("travel")
              .location("名山")
              .participants(Collections.emptyList())
              .impact("留下珍贵的地理记录")
              .relatedEvents(Collections.emptyList())
              .build()
      ));
      case "biography" -> events.addAll(List.of(
          TimelineEvent.builder()
              .title("少有奇志")
              .description("幼年立志求学")
              .dateLabel("少年")
              .significance(5)
              .eventType("birth")
              .location("故乡")
              .participants(Collections.emptyList())
              .impact("奠定人生志向")
              .relatedEvents(Collections.emptyList())
              .build(),
          TimelineEvent.builder()
              .title("入仕任官")
              .description("受知于名臣，踏入仕途")
              .dateLabel("壬午")
              .significance(8)
              .eventType("official")
              .location("京城")
              .participants(Collections.emptyList())
              .impact("开启仕宦生涯")
              .relatedEvents(Collections.emptyList())
              .build()
      ));
      default -> events.addAll(List.of(
          TimelineEvent.builder()
              .title("部队集结")
              .description("调兵遣将，整训兵甲")
              .dateLabel("初旬")
              .significance(6)
              .eventType("battle")
              .location("军营")
              .participants(Collections.emptyList())
              .impact("为后续战事做准备")
              .relatedEvents(Collections.emptyList())
              .build(),
          TimelineEvent.builder()
              .title("决战节点")
              .description("火攻突袭，扭转战局")
              .dateLabel("望日")
              .significance(10)
              .eventType("battle")
              .location("战场")
              .participants(Collections.emptyList())
              .impact("扭转战局，影响深远")
              .relatedEvents(Collections.emptyList())
              .build()
      ));
    }
    return events;
  }

  /**
   * 根据实体和文本类型确定事件类型
   */
  private String determineEventType(EntityAnnotation entity, String category) {
    String label = entity.getLabel().toLowerCase();

    // 根据文本类型推断事件类型
    if ("biography".equals(category)) {
      if (label.contains("生") || label.contains("诞") || label.contains("年少")) {
        return "birth";
      } else if (label.contains("仕") || label.contains("官") || label.contains("任") || label.contains("入")) {
        return "official";
      } else if (label.contains("卒") || label.contains("殁") || label.contains("逝")) {
        return "death";
      } else if (label.contains("功") || label.contains("成就") || label.contains("著")) {
        return "achievement";
      }
      return "life";
    } else if ("travelogue".equals(category)) {
      if (label.contains("行") || label.contains("至") || label.contains("抵达")) {
        return "travel";
      } else if (label.contains("山") || label.contains("水") || label.contains("景")) {
        return "scenery";
      }
      return "travel";
    } else if ("warfare".equals(category)) {
      if (label.contains("战") || label.contains("攻") || label.contains("守")) {
        return "battle";
      } else if (label.contains("集") || label.contains("兵") || label.contains("军")) {
        return "military";
      }
      return "battle";
    }

    return "default";
  }

  /**
   * 使用LLM增强重要事件的历史影响描述（混合方案的第二步）
   */
  private void enrichEventsWithLlm(List<TimelineEvent> events, TextDocument text,
      List<EntityAnnotation> entities, List<RelationAnnotation> relations) {
    try {
      // 选择最重要的事件进行LLM增强（基于significance分数）
      List<TimelineEvent> importantEvents = events.stream()
          .sorted(Comparator.comparing(TimelineEvent::getSignificance).reversed())
          .limit(llmEnrichmentLimit)
          .collect(Collectors.toList());

      for (TimelineEvent event : importantEvents) {
        try {
          // 提取事件的扩展上下文
          String context = "";
          if (event.getStartOffset() != null && event.getEndOffset() != null) {
            int start = Math.max(0, event.getStartOffset() - 150);
            int end = Math.min(text.getContent().length(), event.getEndOffset() + 150);
            context = text.getContent().substring(start, end);
          } else {
            // 如果没有偏移量，使用描述作为上下文
            context = event.getDescription();
          }

          // 调用LLM增强
          TimelineEnrichment enrichment = siliconFlowClient.enrichTimelineEvent(
              event.getTitle(),
              context,
              text.getCategory(),
              null
          );

          // 更新事件的历史影响（如果LLM返回了更好的描述）
          if (enrichment != null && enrichment.getImpact() != null && !enrichment.getImpact().isBlank()) {
            event.setImpact(enrichment.getImpact());
          }

          // 可选：也可以更新其他字段
          if (enrichment != null) {
            if (enrichment.getLocation() != null && !enrichment.getLocation().isBlank()) {
              event.setLocation(enrichment.getLocation());
            }
            if (enrichment.getParticipants() != null && !enrichment.getParticipants().isEmpty()) {
              event.setParticipants(enrichment.getParticipants());
            }
          }

        } catch (Exception ex) {
          // 单个事件LLM增强失败不影响整体流程，继续处理下一个
          log.warn("LLM enrichment failed for event: {}, error: {}", event.getTitle(), ex.getMessage());
        }
      }
    } catch (Exception ex) {
      // LLM增强失败不影响整体流程，使用规则生成的结果
      log.warn("Timeline LLM enrichment failed: {}", ex.getMessage());
    }
  }
}
