package com.ianctchinese.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ianctchinese.llm.dto.AnnotationPayload;
import com.ianctchinese.llm.dto.AnnotationPayload.AnnotationEntity;
import com.ianctchinese.llm.dto.AnnotationPayload.AnnotationRelation;
import com.ianctchinese.llm.dto.AnnotationPayload.WordCloudItem;
import com.ianctchinese.llm.dto.ClassificationPayload;
import com.ianctchinese.llm.dto.SentenceSuggestion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class SiliconFlowClient {

  private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";

  private final RestTemplateBuilder restTemplateBuilder;
  private final ObjectMapper objectMapper;
  private RestTemplate restTemplate;

  @Value("${siliconflow.api-key:}")
  private String apiKey;

  @Value("${siliconflow.default-model:deepseek-ai/DeepSeek-V3}")
  private String defaultModel;

  private RestTemplate restTemplate() {
    if (restTemplate == null) {
      restTemplate = restTemplateBuilder
          .setConnectTimeout(Duration.ofSeconds(15))
          .setReadTimeout(Duration.ofSeconds(60))
          .build();
    }
    return restTemplate;
  }

  public ClassificationPayload classifyText(String textContent, String modelName) {
    String systemPrompt = """
        你是一个文言文体裁判定助手，只能输出下列类别之一：
        warfare（战争纪实）、travelogue（游记地理）、biography（人物传记）、
        official（官职体系）、agriculture（农书类）、crafts（工艺技术）、other（其他/无法归类）。
        如果不确定，也必须在上述类别中选最可能的一个，不要输出 unknown。
        输出需包含类别、置信度和2条中文理由说明。
        """;
    String userPrompt = """
        仅输出 JSON（不要多余文字）：
        {"category":"warfare|travelogue|biography|official|agriculture|crafts|other","confidence":0-1,"reasons":["理由1","理由2"]}
        文本：
        %s
        """.formatted(textContent);
    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      if (node == null) {
        return defaultClassification();
      }
      List<String> reasons = new ArrayList<>();
      if (node.has("reasons") && node.get("reasons").isArray()) {
        node.get("reasons").forEach(reason -> reasons.add(reason.asText("model reasoning")));
      }
      return ClassificationPayload.builder()
          .category(node.path("category").asText("unknown"))
          .confidence(node.path("confidence").asDouble(0.65))
          .reasons(reasons)
          .build();
    } catch (Exception ex) {
      log.warn("SiliconFlow classifyText error: {}", ex.getMessage());
      return defaultClassification();
    }
  }

  public AnnotationPayload annotateText(String textContent, String modelName) {
    // 之前截断到 3000 字会导致只分析开头几句；这里放宽到 12000 字，超长时按段落截断，避免全局丢失
    String limitedContent = textContent;
    if (textContent != null && textContent.length() > 12000) {
      int cut = Math.min(textContent.length(), 12000);
      // 尝试在接近 cut 的位置按句断开，减少语义断裂
      int softCut = textContent.lastIndexOf("。", cut);
      if (softCut < 0 || softCut < 6000) {
        softCut = cut;
      }
      limitedContent = textContent.substring(0, softCut);
    }

    String systemPrompt = """
        You are a classical-Chinese IE assistant. Extract entities and relations.
        Entities: PERSON, LOCATION, EVENT, ORGANIZATION, OBJECT, CUSTOM.
        Relations: FAMILY, ALLY, SUPPORT, RIVAL, CONFLICT, MENTOR, INFLUENCE, LOCATION_OF, PART_OF, CAUSE, TEMPORAL, TRAVEL, CUSTOM.
        """;
    String userPrompt = """
        Output JSON:
        {
          "entities":[{"label":"","category":"PERSON|LOCATION|EVENT|ORGANIZATION|OBJECT|CUSTOM","startOffset":0,"endOffset":0,"confidence":0.8}],
          "relations":[{"sourceLabel":"","targetLabel":"","relationType":"FAMILY|ALLY|SUPPORT|RIVAL|CONFLICT|MENTOR|INFLUENCE|LOCATION_OF|PART_OF|CAUSE|TEMPORAL|TRAVEL|CUSTOM","confidence":0.7,"description":""}],
          "sentences":[{"original":"","punctuated":"","summary":""}],
          "wordCloud":[{"label":"","weight":0.8}]
        }
        Text:
        %s
        """.formatted(limitedContent);
    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      if (node == null) {
        return AnnotationPayload.builder().build();
      }
      List<AnnotationEntity> entities = new ArrayList<>();
      if (node.has("entities") && node.get("entities").isArray()) {
        node.get("entities").forEach(item -> entities.add(
            AnnotationEntity.builder()
                .label(item.path("label").asText())
                .category(item.path("category").asText("CUSTOM"))
                .startOffset(item.path("startOffset").asInt(0))
                .endOffset(item.path("endOffset").asInt(0))
                .confidence(item.path("confidence").asDouble(0.7))
                .build()));
      }
      List<AnnotationRelation> relations = new ArrayList<>();
      if (node.has("relations") && node.get("relations").isArray()) {
        node.get("relations").forEach(item -> relations.add(
            AnnotationRelation.builder()
                .sourceLabel(item.path("sourceLabel").asText())
                .targetLabel(item.path("targetLabel").asText())
                // 兼容模型可能返回的 type/RelationType 字段，缺省时默认 CUSTOM
                .relationType(
                    item.hasNonNull("relationType")
                        ? item.path("relationType").asText("CUSTOM")
                        : item.path("type").asText("CUSTOM")
                )
                .confidence(item.path("confidence").asDouble(0.6))
                .description(item.path("description").asText(""))
                .build()));
      }
      List<SentenceSuggestion> sentences = new ArrayList<>();
      if (node.has("sentences") && node.get("sentences").isArray()) {
        node.get("sentences").forEach(item -> sentences.add(
            SentenceSuggestion.builder()
                .original(item.path("original").asText())
                .punctuated(item.path("punctuated").asText())
                .summary(item.path("summary").asText())
                .build()));
      }
      List<WordCloudItem> wordCloudItems = new ArrayList<>();
      if (node.has("wordCloud") && node.get("wordCloud").isArray()) {
        node.get("wordCloud").forEach(item -> wordCloudItems.add(
            WordCloudItem.builder()
                .label(item.path("label").asText())
                .weight(item.path("weight").asDouble(0.4))
                .build()));
      }
      return AnnotationPayload.builder()
          .entities(entities)
          .relations(relations)
          .sentences(sentences)
          .wordCloud(wordCloudItems)
          .build();
    } catch (Exception ex) {
      log.warn("SiliconFlow annotateText error: {}", ex.getMessage());
      return AnnotationPayload.builder().build();
    }
  }

  /**
   * 让大模型分析事件的历史影响（优化版，更快）
   * @param eventTitle 事件标题
   * @param eventDescription 事件描述
   * @param context 事件上下文
   * @param category 文本类型（biography/travelogue/warfare等）
   * @return 历史影响分析结果（简短，不超过80字）
   */
  public String analyzeEventImpact(String eventTitle, String eventDescription, String context, String category) {
    String systemPrompt = "你是历史分析专家。用50-80字分析事件的历史影响。只返回JSON。";

    String categoryHint = switch (category) {
      case "biography" -> "分析对人物仕途或声望的影响";
      case "warfare" -> "分析战略意义和政治后果";
      case "travelogue" -> "分析文化地理意义";
      default -> "分析历史意义";
    };

    // 缩短上下文以加快分析
    String shortContext = (context != null && context.length() > 100)
        ? context.substring(0, 100)
        : context;

    String userPrompt = """
        事件：%s
        %s

        返回：{"impact":"50-80字分析"}
        """.formatted(eventTitle, categoryHint);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, null);

      if (node != null && node.has("impact")) {
        String impact = node.path("impact").asText("").trim();
        if (!impact.isEmpty()) {
          return impact;
        }
      }
      return null;
    } catch (Exception ex) {
      log.debug("Event impact analysis skipped for '{}': {}", eventTitle, ex.getMessage());
      return null;
    }
  }

  private ClassificationPayload defaultClassification() {
    return ClassificationPayload.builder()
        .category("unknown")
        .confidence(0.5)
        .reasons(Collections.singletonList("fallback"))
        .build();
  }

  private record ChatCompletionRequest(String model, List<Message> messages, double temperature,
                                       int max_tokens) {

    static ChatCompletionRequest of(String systemPrompt, String userPrompt, String modelName,
        String defaultModel) {
      String resolvedModel = Optional.ofNullable(modelName)
          .filter(name -> !name.isBlank())
          .orElse(Optional.ofNullable(defaultModel).filter(name -> !name.isBlank())
              .orElse("deepseek-ai/DeepSeek-V3"));
      return new ChatCompletionRequest(
          resolvedModel,
          List.of(
              new Message("system", systemPrompt),
              new Message("user", userPrompt)
          ),
          0.2,
          4096
      );
    }
  }

  private record Message(String role, String content) {
  }

  private static class ChatCompletionResponse {

    private List<Choice> choices;

    public List<Choice> getChoices() {
      return choices;
    }

    public void setChoices(List<Choice> choices) {
      this.choices = choices;
    }
  }

  private static class Choice {

    private Message message;

    public Message getMessage() {
      return message;
    }

    public void setMessage(Message message) {
      this.message = message;
    }
  }

  private JsonNode sendAndParse(String systemPrompt, String userPrompt, String modelName)
      throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(Optional.ofNullable(apiKey).orElse(""));

    ChatCompletionRequest request = ChatCompletionRequest.of(systemPrompt, userPrompt, modelName,
        defaultModel);
    HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);
    ResponseEntity<ChatCompletionResponse> response = restTemplate()
        .postForEntity(API_URL, entity, ChatCompletionResponse.class);

    if (response.getBody() == null
        || response.getBody().getChoices() == null
        || response.getBody().getChoices().isEmpty()) {
      log.warn("SiliconFlow empty response, status={}", response.getStatusCode());
      return null;
    }
    String content = response.getBody().getChoices().get(0).getMessage().content();
    return parseJson(content);
  }

  private JsonNode parseJson(String content) throws Exception {
    if (content == null || content.isBlank()) {
      return null;
    }
    // 支持 JSON 对象 {...} 和 JSON 数组 [...]
    int objStart = content.indexOf('{');
    int arrStart = content.indexOf('[');
    int objEnd = content.lastIndexOf('}');
    int arrEnd = content.lastIndexOf(']');

    String json;
    if (arrStart >= 0 && (objStart < 0 || arrStart < objStart) && arrEnd > arrStart) {
      // JSON 数组在前或只有数组
      json = content.substring(arrStart, arrEnd + 1);
    } else if (objStart >= 0 && objEnd > objStart) {
      // JSON 对象
      json = content.substring(objStart, objEnd + 1);
    } else {
      json = content;
    }
    log.debug("Parsed JSON: {}", json.length() > 500 ? json.substring(0, 500) + "..." : json);
    return objectMapper.readTree(json);
  }

  /**
   * 分析文本中的官职体系（优化版，更快）
   * @param textContent 文本内容
   * @param persons 人物列表
   * @param modelName 模型名称
   * @return 官职分析结果的JSON对象
   */
  public JsonNode analyzeOfficialPositions(String textContent, List<String> persons, String modelName) {
    String systemPrompt = "你是古代官职分析专家。分析文本中人物的官职、品级、部门。只返回JSON，不要额外解释。";

    String personList = persons != null && !persons.isEmpty()
        ? String.join("、", persons.subList(0, Math.min(persons.size(), 10)))
        : "（文中人物）";

    // 缩短文本长度以加快分析
    String shortContent = textContent.length() > 1000 ? textContent.substring(0, 1000) : textContent;

    String userPrompt = """
        文本：%s
        人物：%s

        JSON格式：
        {"officials":[{"name":"姓名","position":"官职","level":"品级","department":"部门","superior":"上级（可选）","description":"描述"}]}

        品级：一品/二品/.../九品
        部门：六部/都察院/翰林院/地方政府/军事系统/中央机构
        """.formatted(shortContent, personList);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      log.debug("Official positions analysis result: {}", node);
      return node;
    } catch (Exception ex) {
      log.warn("SiliconFlow analyzeOfficialPositions error: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * 通用对话入口：返回原始 JsonNode，供地理编码等灵活场景使用。
   */
  public JsonNode chat(String systemPrompt, String userPrompt, String modelName) {
    try {
      return sendAndParse(systemPrompt, userPrompt, modelName);
    } catch (Exception e) {
      log.warn("SiliconFlow chat error: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 使用LLM分析文本生成词云
   * @param textContent 文本内容
   * @param entities 实体列表（用于辅助分析）
   * @param modelName 模型名称
   * @return 词云数据的JSON对象
   */
  public JsonNode analyzeWordCloud(String textContent, String entities, String modelName) {
    String systemPrompt = "你是文本分析专家。分析古文关键词并生成词云数据。只返回JSON，不要额外解释。";

    // 缩短文本长度以加快分析
    String shortContent = textContent.length() > 2000 ? textContent.substring(0, 2000) : textContent;

    String userPrompt = """
        文本：%s

        已识别实体：%s

        请提取10-15个最重要的关键词（人物、地点、事件、概念等），并给出权重（0.4-1.0）。
        权重越高表示该词越重要。

        JSON格式：
        {"wordCloud":[{"label":"关键词","weight":0.8}]}
        """.formatted(shortContent, entities);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      log.debug("Word cloud analysis result: {}", node);
      return node;
    } catch (Exception ex) {
      log.warn("SiliconFlow analyzeWordCloud error: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * 使用LLM分析文本提取通用时间轴事件
   * @param textContent 文本内容
   * @param category 文本类型
   * @param modelName 模型名称
   * @return 时间轴事件的JSON对象
   */
  public JsonNode analyzeTimeline(String textContent, String category, String modelName) {
    String systemPrompt = "你是古文分析专家。从文本中提取时间线上的关键事件。只返回JSON，不要额外解释。";

    // 缩短文本长度以加快分析
    String shortContent = textContent.length() > 3000 ? textContent.substring(0, 3000) : textContent;

    String categoryHint = switch (category) {
      case "warfare" -> "战争事件（如：集结、战役、议和等）";
      case "biography" -> "人物生平事件（如：出生、入仕、升迁、谪居等）";
      case "travelogue" -> "游历事件（如：启程、途经、抵达、归程等）";
      default -> "重要事件（按时间顺序）";
    };

    String userPrompt = """
        文本：%s
        文本类型：%s

        请从文本中提取3-8个关键事件（按时间顺序），包括：
        - title: 事件标题（简短，5-10字）
        - description: 事件描述（从文本中提取，30-50字）
        - dateLabel: 时间标签（如：初春、壬午年、正月等，从文本中提取）
        - significance: 重要度（1-10）
        - eventType: 事件类型（military、official、life、travel、battle、scenery等）
        - participants: 参与人物列表（数组，从文本中提取）
        - location: 地点（如果有）
        - impact: 历史影响（30-50字，分析该事件的意义）

        JSON格式：
        {"timeline":[{"title":"","description":"","dateLabel":"","significance":8,"eventType":"","participants":[""],"location":"","impact":""}]}
        """.formatted(shortContent, categoryHint);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      log.debug("Timeline analysis result: {}", node);
      return node;
    } catch (Exception ex) {
      log.warn("SiliconFlow analyzeTimeline error: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * 使用LLM分析战役时间轴
   * @param textContent 文本内容
   * @param category 文本类型
   * @param modelName 模型名称
   * @return 战役时间轴的JSON对象
   */
  public JsonNode analyzeBattleTimeline(String textContent, String category, String modelName) {
    String systemPrompt = "你是军事历史专家。分析文本中的战役发展过程。只返回JSON，不要额外解释。";

    // 缩短文本长度以加快分析
    String shortContent = textContent.length() > 3000 ? textContent.substring(0, 3000) : textContent;

    String userPrompt = """
        文本：%s
        文本类型：%s

        请分析战役的关键阶段（3-5个），包括：
        - phase: 阶段名称（如：集结备战、初战告捷、决战时刻、战后善后等）
        - description: 阶段描述（30-50字）
        - intensity: 战斗激烈程度（1-10）
        - opponent: 对手名称

        JSON格式：
        {"battles":[{"phase":"阶段","description":"描述","intensity":8,"opponent":"对手"}]}
        """.formatted(shortContent, category);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      log.debug("Battle timeline analysis result: {}", node);
      return node;
    } catch (Exception ex) {
      log.warn("SiliconFlow analyzeBattleTimeline error: {}", ex.getMessage());
      return null;
    }
  }

  /**
   * 使用LLM分析工艺流程周期
   * @param textContent 文本内容
   * @param category 文本类型
   * @param entities 实体列表
   * @param modelName 模型名称
   * @return 流程周期的JSON对象
   */
  public JsonNode analyzeProcessCycle(String textContent, String category, String entities, String modelName) {
    String systemPrompt = "你是农业技术和工艺制作专家。分析文本中的工艺流程或农业周期。只返回JSON，不要额外解释。";

    // 缩短文本长度以加快分析
    String shortContent = textContent.length() > 2000 ? textContent.substring(0, 2000) : textContent;

    String categoryHint = category.equals("agriculture")
        ? "农业生产（如：整地、播种、灌溉、施肥、收获等）"
        : "工艺制作（如：选材、加工、组装、修整、装饰等）";

    String userPrompt = """
        文本：%s
        文本类型：%s（%s）
        已识别实体：%s

        请提取4-7个关键流程步骤，包括：
        - name: 步骤名称
        - description: 步骤描述（30-50字）
        - sequence: 顺序编号（1,2,3...）
        - category: 步骤类别
        - tools: 使用的工具列表
        - materials: 使用的材料列表
        - output: 产出物（可选）
        - duration: 耗时（天数，估算值）

        JSON格式：
        {"steps":[{"name":"步骤名","description":"描述","sequence":1,"category":"类别","tools":["工具"],"materials":["材料"],"output":"产出","duration":3}]}
        """.formatted(shortContent, category, categoryHint, entities);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelName);
      log.debug("Process cycle analysis result: {}", node);
      return node;
    } catch (Exception ex) {
      log.warn("SiliconFlow analyzeProcessCycle error: {}", ex.getMessage());
      return null;
    }
  }
}
