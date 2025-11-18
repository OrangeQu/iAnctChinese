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
import com.ianctchinese.llm.UnifiedLLMClient;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
  private final UnifiedLLMClient unifiedLLMClient;

  @Override
  @Transactional
  public ClassificationResponse classifyText(Long textId) {
    TextDocument document = loadText(textId);
    // 默认使用 DeepSeek-V3.2-Exp 作为基础模型
    ClassificationPayload payload = classifyWithFallback(document.getContent(), "deepseek-ai/DeepSeek-V3.2-Exp");
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
    List<TimelineEvent> timeline = buildTimelineForCategory(text.getCategory());
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
    // 默认使用 DeepSeek-V3.2-Exp 作为基础模型
    AnnotationPayload payload = unifiedLLMClient.annotateText(document.getContent(), "deepseek-ai/DeepSeek-V3.2-Exp");
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
  public ModelAnalysisResponse runFullAnalysis(Long textId, String modelKey) {
    String effectiveModelKey = (modelKey != null && !modelKey.isBlank())
        ? modelKey
        : "deepseek-ai/DeepSeek-V3.2-Exp";

    TextDocument document = loadText(textId);
    
    // 原始逻辑：先用大模型分类，再用大模型进行标注
    ClassificationPayload clsPayload = classifyWithFallback(document.getContent(), effectiveModelKey);
    String normalizedCategory = normalizeCategory(clsPayload.getCategory(), document.getCategory());
    document.setCategory(normalizedCategory);
    textDocumentRepository.save(document);

    AnnotationPayload annPayload = unifiedLLMClient.annotateText(document.getContent(), effectiveModelKey);
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
   * 调用大模型分类；如返回为空或"unknown"，使用关键词启发式兜底，至少给出一个可用类别。
   */
  private ClassificationPayload classifyWithFallback(String content, String modelKey) {
    ClassificationPayload payload = null;
    try {
      payload = unifiedLLMClient.classifyText(content, modelKey);
    } catch (Exception ex) {
      // ignore, fallback below
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

  private List<TimelineEvent> buildTimelineForCategory(String category) {
    List<TimelineEvent> events = new ArrayList<>();
    switch (category) {
      case "travelogue" -> events.addAll(List.of(
          TimelineEvent.builder().title("启程").description("作者离开京师，溯江而上").dateLabel("初春").significance(6).build(),
          TimelineEvent.builder().title("抵达名山").description("记述山川胜景与碑刻").dateLabel("暮春").significance(8).build(),
          TimelineEvent.builder().title("归程总结").description("整理沿途见闻与思考").dateLabel("初夏").significance(7).build()
      ));
      case "biography" -> events.addAll(List.of(
          TimelineEvent.builder().title("少有奇志").description("幼年立志求学").dateLabel("少年").significance(5).build(),
          TimelineEvent.builder().title("入仕任官").description("受知于名臣，踏入仕途").dateLabel("壬午").significance(8).build(),
          TimelineEvent.builder().title("升迁与谪居").description("仕途沉浮，辗转多地").dateLabel("癸未").significance(7).build()
      ));
      default -> events.addAll(List.of(
          TimelineEvent.builder().title("部队集结").description("调兵遣将，整训兵甲").dateLabel("初旬").significance(6).build(),
          TimelineEvent.builder().title("决战节点").description("火攻突袭，扭转战局").dateLabel("望日").significance(10).build(),
          TimelineEvent.builder().title("善后与盟约").description("划定疆界，订立盟约").dateLabel("下旬").significance(7).build()
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
}
