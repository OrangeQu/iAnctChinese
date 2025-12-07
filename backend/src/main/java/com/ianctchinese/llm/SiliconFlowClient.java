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
          .setConnectTimeout(Duration.ofSeconds(30))
          .setReadTimeout(Duration.ofSeconds(90))
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
    String systemPrompt = """
        You are a classical-Chinese IE assistant. First infer the coarse genre (warfare / travelogue / biography / essay-or-other) and then emphasize the corresponding schema, but output entities/relations ONLY with these categories:
        Entities: PERSON, LOCATION, EVENT, ORGANIZATION, OBJECT, CUSTOM.
        Relations: FAMILY, ALLY, SUPPORT, RIVAL, CONFLICT, MENTOR, INFLUENCE, LOCATION_OF, PART_OF, CAUSE, TEMPORAL, TRAVEL, CUSTOM.

        Genre emphasis (guide the model to extract more, without changing categories):
        - warfare: prioritize battles, marches, sieges, commanders, armies, strongholds, decrees, alliances. Extract more relations of RIVAL/ALLY/PART_OF/LOCATION_OF/COMMAND-like (map to existing types).
        - travelogue: prioritize routes, places visited, landmarks, scenery, guides/hosts; relations of ROUTE/LOCATION_OF/DESCRIBES (map to LOCATION_OF/INFLUENCE).
        - biography: prioritize offices/appointments, patrons, rivals, praise/criticism, major life events; relations of MENTOR/FAMILY/ALLY/RIVAL/INFLUENCE/PART_OF/LOCATION_OF.
        - essay/misc: prioritize key people/places/events mentioned, analogies, causes; relations of CAUSE/INFLUENCE/DESCRIBES mapped to available types.

        Requirements:
        1) Every entity must include startOffset/endOffset (UTF-8 character index into the original text). If you cannot locate a span, DO NOT return that entity.
        2) Prefer specific categories; use CUSTOM only when none fits.
        3) Aim for >=12 entities and >=8 relations if the text allows; relations must reference existing entity labels, do not invent labels.
        4) Labels concise (<=10 chars), confidence 0-1.
        5) WordCloud: 12 short tokens (2-6 chars), weight 0.3-1, no full sentences.
        6) Sentences: 6 suggestions with original/punctuated/summary.
        Return STRICT JSON. If unsure of offset, drop the item.
        """;
    String userPrompt = """
        Output ONLY JSON in this shape:
        {
          "entities":[{"label":"","category":"PERSON|LOCATION|EVENT|ORGANIZATION|OBJECT|CUSTOM","startOffset":0,"endOffset":0,"confidence":0.8}],
          "relations":[{"sourceLabel":"","targetLabel":"","relationType":"FAMILY|ALLY|SUPPORT|RIVAL|CONFLICT|MENTOR|INFLUENCE|LOCATION_OF|PART_OF|CAUSE|TEMPORAL|TRAVEL|CUSTOM","confidence":0.7,"description":""}],
          "sentences":[{"original":"","punctuated":"","summary":""}],
          "wordCloud":[{"label":"","weight":0.8}]
        }
        Text:
        %s
        """.formatted(textContent);
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
   * 让大模型分析事件的历史影响
   * @param eventTitle 事件标题
   * @param eventDescription 事件描述
   * @param context 事件上下文
   * @param category 文本类型（biography/travelogue/warfare等）
   * @return 历史影响分析结果（简短，不超过80字）
   */
  public String analyzeEventImpact(String eventTitle, String eventDescription, String context, String category) {
    String systemPrompt = """
        你是一位分析中国古代历史事件的专家。请分析给定事件的历史影响或意义。
        要求：
        1. 回答必须是中文，50-80字
        2. 重点分析该事件对人物、政治、军事或文化的实际影响
        3. 即使是小事件也要尽量分析其意义，不要说"不重要"
        4. 语言简洁、客观
        """;

    String categoryHint = switch (category) {
      case "biography" -> "这是人物传记中的事件。分析该事件如何影响人物的仕途、声望或历史地位。";
      case "warfare" -> "这是军事记录中的事件。分析该战役或军事行动的战略意义和政治后果。";
      case "travelogue" -> "这是游记中的事件。分析该地点或旅程的文化、地理或历史意义。";
      default -> "分析该事件的历史或文化意义。";
    };

    String userPrompt = """
        事件：%s
        描述：%s
        上下文：%s

        %s

        请用50-80字分析该事件的历史影响或意义。
        返回格式：{"impact":"你的分析内容"}
        """.formatted(eventTitle, eventDescription,
                     context != null && context.length() > 200 ? context.substring(0, 200) : context,
                     categoryHint);

    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, null);
      log.debug("Event impact analysis for '{}': node={}", eventTitle, node);

      if (node != null && node.has("impact")) {
        String impact = node.path("impact").asText("").trim();
        if (impact.isEmpty()) {
          log.warn("Event impact for '{}' is empty from model", eventTitle);
          return null;
        }
        log.debug("Event impact for '{}': {}", eventTitle, impact);
        return impact;
      } else {
        log.warn("Event impact for '{}': model response missing 'impact' field. Response: {}",
                 eventTitle, node != null ? node.toString() : "null");
        return null;
      }
    } catch (Exception ex) {
      log.warn("SiliconFlow analyzeEventImpact error for '{}': {}", eventTitle, ex.getMessage(), ex);
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
    int start = content.indexOf('{');
    int end = content.lastIndexOf('}');
    String json = (start >= 0 && end >= start) ? content.substring(start, end + 1) : content;
    return objectMapper.readTree(json);
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
