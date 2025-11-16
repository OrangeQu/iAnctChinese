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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class HuggingFaceClient {

  private static final String API_BASE = "https://api-inference.huggingface.co/models/";

  private final RestTemplateBuilder restTemplateBuilder;
  private final ObjectMapper objectMapper;

  private RestTemplate restTemplate;

  @Value("${huggingface.api-token:}")
  private String apiToken;

  @Value("${huggingface.model:Qwen/Qwen-0.6B}")
  private String model;

  private RestTemplate restTemplate() {
    if (restTemplate == null) {
      restTemplate = restTemplateBuilder
          .setConnectTimeout(Duration.ofSeconds(30))
          .setReadTimeout(Duration.ofSeconds(90))
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
      log.warn("HuggingFace classifyText error: {}", ex.getMessage());
      return defaultClassification();
    }
  }

  public AnnotationPayload annotateText(String textContent) {
    String systemPrompt = "你是一名古籍标注助手，需要同时完成实体抽取、关系抽取、句读建议及词频统计。请输出严格 JSON，并保证关系中的实体在实体列表出现。";
    String userPrompt = """
        请只输出 JSON，格式如下：
        {
          "entities":[{"label":"","category":"","startOffset":0,"endOffset":0,"confidence":0.8}],
          "relations":[{"sourceLabel":"","targetLabel":"","relationType":"","confidence":0.7,"description":""}],
          "sentences":[{"original":"","punctuated":"","summary":""}],
          "wordCloud":[{"label":"","weight":0.8}]
        }
        要求：
        1) 词云词条保持 2-6 字，weight 介于 0.3-1；
        2) 关系类型可用 family/friend/rival/location/event/depend/custom；
        3) JSON 必须严格闭合；
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
      log.warn("HuggingFace annotateText error: {}", ex.getMessage());
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
    String prompt = "System:\n" + systemPrompt + "\nUser:\n" + userPrompt;
    Map<String, Object> payload = new HashMap<>();
    payload.put("inputs", prompt);

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("temperature", 0.2);
    parameters.put("max_new_tokens", 768);
    payload.put("parameters", parameters);
    payload.put("options", Map.of("wait_for_model", true));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(Optional.ofNullable(apiToken).orElse(""));

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
    ResponseEntity<String> response =
        restTemplate().postForEntity(API_BASE + model, entity, String.class);

    if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
      log.warn("HuggingFace 无返回内容，status={}", response.getStatusCode());
      return null;
    }
    return parseJsonFromBody(response.getBody());
  }

  private JsonNode parseJsonFromBody(String body) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    if (root.has("error")) {
      log.warn("HuggingFace inference error: {}", root.get("error").asText());
      return null;
    }
    String content = null;
    if (root.isArray() && root.size() > 0) {
      content = root.get(0).path("generated_text").asText();
    } else if (root.has("generated_text")) {
      content = root.path("generated_text").asText();
    } else {
      content = root.toString();
    }
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
      log.warn("HuggingFace 结果解析失败，尝试兜底: {}", parseEx.getMessage());
      String trimmed = json + (json.endsWith("}") ? "" : "}");
      try {
        return objectMapper.readTree(trimmed);
      } catch (Exception ignored) {
        return null;
      }
    }
  }
}
