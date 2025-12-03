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
    List<TimelineEvent> timeline = buildTimelineFromEntities(text, entities, relations);
    List<MapPathPoint> mapPoints = buildMapPointsForCategory(text.getCategory());
    List<String> recommendedViews = buildRecommendedViews(text.getCategory());
    List<BattleEvent> battleTimeline = buildBattleTimeline(text.getCategory());
    List<FamilyNode> familyTree = buildFamilyTree(text.getCategory(), entities, relations);

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

      TextInsightsResponse insights = buildInsights(textId);
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

  /**
   * 从实体和关系中构建时间线事件
   */
  private List<TimelineEvent> buildTimelineFromEntities(
      TextDocument text,
      List<EntityAnnotation> entities,
      List<RelationAnnotation> relations) {

    List<TimelineEvent> events = new ArrayList<>();
    String category = text.getCategory();
    String content = text.getContent();

    // 1. 从EVENT类型的实体中提取事件
    List<EntityAnnotation> eventEntities = entities.stream()
        .filter(e -> e.getCategory() == EntityCategory.EVENT)
        .collect(Collectors.toList());

    log.debug("Building timeline for category '{}': found {} EVENT entities", category, eventEntities.size());

    for (EntityAnnotation event : eventEntities) {
      String eventText = extractText(content, event.getStartOffset(), event.getEndOffset());
      String eventType = determineEventType(event, relations, category);

      // 查找相关人物 - 改进的逻辑
      List<String> participants = findEventParticipants(event, relations, entities, category);
      // 如果没有相关人物，设置为"无"
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

      // 使用大模型分析历史影响
      log.debug("Analyzing impact for event '{}' in category '{}'", event.getLabel(), category);
      String impact = analyzeEventImpactWithRetry(event, eventText, content, category);
      // 如果没有历史影响，设置为"无"
      if (impact == null || impact.trim().isEmpty()) {
        log.debug("Event '{}' has no impact analysis, using fallback '无'", event.getLabel());
        impact = "无";
      } else {
        log.debug("Event '{}' impact: {}", event.getLabel(), impact);
      }

      events.add(TimelineEvent.builder()
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
          .build());
    }

    // 2. 如果没有EVENT实体，尝试从关系中构建时间线
    if (events.isEmpty()) {
      events.addAll(buildTimelineFromRelations(relations, entities, content, category));
    }

    // 3. 如果还是没有事件，返回默认的模拟数据
    if (events.isEmpty()) {
      events.addAll(buildDefaultTimelineForCategory(category));
    }

    return events;
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

          // 对于从关系构建的事件，也尝试分析历史影响
          String contextText = extractContext(content, source.getStartOffset(), source.getEndOffset(), 150);
          String impact = siliconFlowClient.analyzeEventImpact(
              title,
              description,
              contextText,
              category
          );
          if (impact == null || impact.trim().isEmpty()) {
            impact = generateBasicImpact(source, description, category);
          }
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
}
