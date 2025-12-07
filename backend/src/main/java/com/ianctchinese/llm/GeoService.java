package com.ianctchinese.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeoService {

  private final SiliconFlowClient llmClient;

  private static final String SYSTEM_PROMPT = """
      你是地理解析助手。请对输入的实体列表返回今天的真实地理坐标。
      只输出严格的 JSON 数组（不要多余文字），每个元素格式：
      {"entityId":<原始id或null>,"label":"名称","latitude":<float>,"longitude":<float>,"note":""}
      若无法确定坐标，跳过该实体，不要猜测。
      """;

  public List<GeoPointDto> locate(GeoLocateRequest req) {
    List<GeoLocateRequest.EntityDto> entities = Optional.ofNullable(req.getEntities())
        .orElse(List.of());
    if (entities.isEmpty()) {
      log.warn("Geo locate: entities list is empty");
      return List.of();
    }

    StringBuilder userPrompt = new StringBuilder("实体列表：\n");
    entities.forEach(e -> userPrompt
        .append("{\"entityId\":").append(e.getId()).append(",\"label\":\"")
        .append(Optional.ofNullable(e.getLabel()).orElse("").trim()).append("\"}\n"));

    log.info("Geo locate request: model={}, entities={}", req.getModel(), userPrompt);

    String model = Optional.ofNullable(req.getModel()).filter(s -> !s.isBlank()).orElse(null);
    JsonNode node = llmClient.chat(SYSTEM_PROMPT, userPrompt.toString(), model);

    log.info("Geo locate LLM response node type: {}, content: {}",
        node != null ? node.getNodeType() : "null",
        node != null ? (node.toString().length() > 500 ? node.toString().substring(0, 500) : node.toString()) : "null");

    List<GeoPointDto> points = new ArrayList<>();
    if (node != null) {
      if (node.isArray()) {
        log.info("Parsing as JSON array with {} elements", node.size());
        node.forEach(n -> addPoint(points, n));
      } else if (node.has("points") && node.get("points").isArray()) {
        log.info("Parsing 'points' field as JSON array");
        node.get("points").forEach(n -> addPoint(points, n));
      } else if (node.isObject()) {
        log.info("Parsing as single JSON object");
        addPoint(points, node);
      } else {
        log.warn("Geo locate llm response invalid format: {}", node);
      }
    } else {
      log.warn("Geo locate llm response is null");
    }

    log.info("Geo locate result: {} points found", points.size());
    return points;
  }

  private void addPoint(List<GeoPointDto> points, JsonNode n) {
    if (n == null || !n.isObject()) return;
    double lat = n.path("latitude").asDouble(Double.NaN);
    double lng = n.path("longitude").asDouble(Double.NaN);
    String label = n.path("label").asText("");
    if (!Double.isNaN(lat) && !Double.isNaN(lng) && !label.isBlank()) {
      points.add(GeoPointDto.builder()
          .entityId(n.has("entityId") && !n.get("entityId").isNull() ? n.get("entityId").asLong() : null)
          .label(label)
          .latitude(lat)
          .longitude(lng)
          .source("llm")
          .note(n.path("note").asText(""))
          .build());
    }
  }
}
