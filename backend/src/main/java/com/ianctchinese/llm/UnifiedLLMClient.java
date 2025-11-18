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
public class UnifiedLLMClient {

  private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";

  private final RestTemplateBuilder restTemplateBuilder;
  private final ObjectMapper objectMapper;
  private RestTemplate restTemplate;

  @Value("${siliconflow.api-key}")
  private String apiKey;

  private RestTemplate restTemplate() {
    if (restTemplate == null) {
      restTemplate = restTemplateBuilder
          .setConnectTimeout(Duration.ofSeconds(30))
          .setReadTimeout(Duration.ofSeconds(90))
          .build();
    }
    return restTemplate;
  }

  public ClassificationPayload classifyText(String textContent, String modelKey) {
    String systemPrompt = "你是一名古汉语文本分类助手，需要根据输入文本判断其属于战争纪实（warfare）、游记地理（travelogue）、人物传记（biography）或其他类型（other）。";
    String userPrompt = """
        请只输出 JSON，格式如下：
        {"category":"warfare|travelogue|biography|other","confidence":0-1,"reasons":["原因1","原因2"]}
        文本：
        %s
        """.formatted(textContent);
    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelKey);
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
      log.warn("UnifiedLLM classifyText error with model {}: {}", modelKey, ex.getMessage());
      return defaultClassification();
    }
  }

  public AnnotationPayload annotateText(String textContent, String modelKey) {
    String systemPrompt = "你是一名古籍标注助手，需要同时完成实体抽取、关系抽取、句读建议及词频统计。"
        + "实体类别仅使用：PERSON(人物)、LOCATION(地点)、EVENT(事件)、ORGANIZATION(组织/朝廷/部队)、OBJECT(器物/文献)、CUSTOM(其他)。"
        + "关系类型仅使用：FAMILY(亲属)、ALLY(结盟/支持)、RIVAL(对抗/敌对)、MENTOR(师承/同门)、INFLUENCE(影响/启发)、LOCATION_OF(所在)、PART_OF(隶属)、CAUSE(因果)、CUSTOM(其他)。"
        + "要求：至少输出8-12个实体，并保证 label 可读；至少输出5-8条关系，覆盖人物/地点/事件间的联系，关系的 sourceLabel/targetLabel 必须存在于 entities.label。"
        + "词云词条请输出8-12个短词（2-6字），weight 0.3-1，避免整句。句读建议提供3-6条 original/punctuated/summary。"
        + "JSON 必须严格闭合。若偏移难以确定，可用0-0占位。";
    String userPrompt = """
        请只输出 JSON，格式如下：
        {
          "entities":[{"label":"","category":"PERSON|LOCATION|EVENT|ORGANIZATION|OBJECT|CUSTOM","startOffset":0,"endOffset":0,"confidence":0.8}],
          "relations":[{"sourceLabel":"","targetLabel":"","relationType":"FAMILY|ALLY|RIVAL|MENTOR|INFLUENCE|LOCATION_OF|PART_OF|CAUSE|CUSTOM","confidence":0.7,"description":""}],
          "sentences":[{"original":"","punctuated":"","summary":""}],
          "wordCloud":[{"label":"","weight":0.8}]
        }
        文本：
        %s
        """.formatted(textContent);
    try {
      JsonNode node = sendAndParse(systemPrompt, userPrompt, modelKey);
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
      log.warn("UnifiedLLM annotateText error with model {}: {}", modelKey, ex.getMessage());
      return AnnotationPayload.builder().build();
    }
  }

  private ClassificationPayload defaultClassification() {
    return ClassificationPayload.builder()
        .category("unknown")
        .confidence(0.5)
        .reasons(Collections.singletonList("使用本地启发式判断"))
        .build();
  }

  private record ChatRequest(String model, List<Message> messages, double temperature,
                             int max_tokens) {

    static ChatRequest of(String systemPrompt, String userPrompt, String modelKey) {
      return new ChatRequest(
          modelKey,
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

  private static class ChatResponse {

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

  private JsonNode sendAndParse(String systemPrompt, String userPrompt, String modelKey) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    ChatRequest request = ChatRequest.of(systemPrompt, userPrompt, modelKey);
    HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
    ResponseEntity<ChatResponse> response = restTemplate()
        .postForEntity(API_URL, entity, ChatResponse.class);

    if (response.getBody() == null
        || response.getBody().getChoices() == null
        || response.getBody().getChoices().isEmpty()) {
      log.warn("UnifiedLLM 无返回内容，status={}", response.getStatusCode());
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
}
