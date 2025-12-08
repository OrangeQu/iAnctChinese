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
    String systemPrompt =
        "You are a classical-Chinese text classifier. Decide whether the text is warfare, travelogue, biography, or other.";
    String userPrompt = """
        Please return ONLY JSON:
        {"category":"warfare|travelogue|biography|other","confidence":0-1,"reasons":["reason1","reason2"]}
        Text:
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
    // 限制文本长度以加快分析
    String limitedContent = textContent.length() > 3000
        ? textContent.substring(0, 3000) + "..."
        : textContent;

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
}
