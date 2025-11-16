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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeepSeekClient {

  private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
  private final RestTemplateBuilder restTemplateBuilder;
  private final ObjectMapper objectMapper;

  private RestTemplate restTemplate;

  @Value("${deepseek.api-key:}")
  private String apiKey;

  private RestTemplate restTemplate() {
    if (restTemplate == null) {
      restTemplate = restTemplateBuilder
          .setConnectTimeout(Duration.ofSeconds(20))
          .setReadTimeout(Duration.ofSeconds(60))
          .build();
    }
    return restTemplate;
  }

  public ClassificationPayload classifyText(String textContent) {
    String systemPrompt = "你是一名古汉语文本分类助手，需要根据输入文本判断其属于战争纪实（warfare）、游记地理（travelogue）、人物传记（biography）或其他类型（other）。";
    String userPrompt = """
        请只输出 JSON，格式如下：
        {"category":"warfare|travelogue|biography|other","confidence":0-1,"reasons":["原因1","原因2"]}
        文本：
        %s
        """.formatted(textContent);
    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt);
      if (node == null) {
        return defaultClassification();
      }
      List<String> reasons = new ArrayList<>();
      if (node.has("reasons") && node.get("reasons").isArray()) {
        node.get("reasons").forEach(reason -> reasons.add(reason.asText("模型判断依据")));
      }
      return ClassificationPayload.builder()
          .category(node.path("category").asText("unknown"))
          .confidence(node.path("confidence").asDouble(0.65))
          .reasons(reasons)
          .build();
    } catch (Exception ex) {
      log.warn("DeepSeek classifyText error: {}", ex.getMessage());
      return defaultClassification();
    }
  }

  public AnnotationPayload annotateText(String textContent) {
    String systemPrompt = "你是一名古籍标注助手，需要同时完成实体抽取、关系抽取、句读建议及词频统计。请务必输出至少1-3条关系（如果文本确实没有明确关系，可给出空数组，但尽量挖掘人物/地点/事件之间的连接），并保证关系的 sourceLabel/targetLabel 必须出现在 entities 的 label 中。词频词云请输出简短词条（2-6字），避免整句。";
    String userPrompt = """
        请只输出 JSON，格式如下：
        {
          "entities":[{"label":"","category":"","startOffset":0,"endOffset":0,"confidence":0.8}],
          "relations":[{"sourceLabel":"","targetLabel":"","relationType":"","confidence":0.7,"description":""}],
          "sentences":[{"original":"","punctuated":"","summary":""}],
          "wordCloud":[{"label":"","weight":0.8}]
        }
        要求：
        1) 词云词条保持在2-6字，weight 介于0.3-1。
        2) 关系关系类型可用 family/friend/rival/location/event/depend 等，描述简要说明。
        3) 保持 JSON 严格闭合。
        文本：
        %s
        """.formatted(textContent);
    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt);
      if (node == null) {
        return AnnotationPayload.builder().build();
      }
      List<AnnotationEntity> entities = new ArrayList<>();
      if (node.has("entities")) {
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
      if (node.has("relations")) {
        node.get("relations").forEach(item -> relations.add(
            AnnotationRelation.builder()
                .sourceLabel(item.path("sourceLabel").asText())
                .targetLabel(item.path("targetLabel").asText())
                .relationType(item.path("relationType").asText("CUSTOM"))
                .confidence(item.path("confidence").asDouble(0.6))
                .description(item.path("description").asText(""))
                .build()));
      }
      List<SentenceSuggestion> sentences = new ArrayList<>();
      if (node.has("sentences")) {
        node.get("sentences").forEach(item -> sentences.add(
            SentenceSuggestion.builder()
                .original(item.path("original").asText())
                .punctuated(item.path("punctuated").asText())
                .summary(item.path("summary").asText())
                .build()));
      }
      List<WordCloudItem> wordCloudItems = new ArrayList<>();
      if (node.has("wordCloud")) {
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
      log.warn("DeepSeek annotateText error: {}", ex.getMessage());
      return AnnotationPayload.builder().build();
    }
  }

  private ClassificationPayload defaultClassification() {
    return ClassificationPayload.builder()
        .category("unknown")
        .confidence(0.5)
        .reasons(Collections.singletonList("使用本地启发式判定"))
        .build();
  }

  private JsonNode sendAndParse(String systemPrompt, String userPrompt) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(Optional.ofNullable(apiKey).orElse(""));

    DeepSeekRequest request = DeepSeekRequest.of(systemPrompt, userPrompt);
    HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(request, headers);
    ResponseEntity<DeepSeekResponse> response = restTemplate().postForEntity(API_URL, entity,
        DeepSeekResponse.class);
    if (response.getBody() == null
        || response.getBody().getChoices() == null
        || response.getBody().getChoices().isEmpty()) {
      log.warn("DeepSeek 无返回内容，status={}", response.getStatusCode());
      return null;
    }
    String content = response.getBody().getChoices().get(0).getMessage().content();
    return parseJson(content);
  }

  private JsonNode parseJson(String content) {
    if (content == null || content.isBlank()) {
      return null;
    }
    int start = content.indexOf('{');
    int end = content.lastIndexOf('}');
    String json = (start >= 0 && end >= start) ? content.substring(start, end + 1) : content;
    try {
      return objectMapper.readTree(json);
    } catch (Exception parseEx) {
      log.warn("DeepSeek 结果解析失败，尝试忽略尾部噪声: {}", parseEx.getMessage());
      String trimmed = json + (json.endsWith("}") ? "" : "}");
      try {
        return objectMapper.readTree(trimmed);
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private record DeepSeekRequest(String model, List<Message> messages, double temperature,
                                 int max_tokens) {

    static DeepSeekRequest of(String systemPrompt, String userPrompt) {
      return new DeepSeekRequest(
          "deepseek-chat",
          List.of(
              new Message("system", systemPrompt),
              new Message("user", userPrompt)
          ),
          0.2,
          2048
      );
    }
  }

  private record Message(String role, String content) {
  }

  private static class DeepSeekResponse {

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
}
