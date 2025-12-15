package com.ianctchinese.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.ianctchinese.llm.TencentMapClient.GeocodingResult;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeoService {

  private final SiliconFlowClient llmClient;
  private final TencentMapClient tencentMapClient;

  /**
   * 更强约束：只输出 JSON 数组、字段固定、长度必须一致、必须保留 entityId/label/category
   * 并要求 modernName 一定为“现代中国可检索地址”，无法判断也要给兜底（中国/北京市等）。
   */
  private static final String SYSTEM_PROMPT = """
你是地理解析助手。你的任务是：将输入的实体映射到“现代中国地图上可搜索到的地名 modernName”。

只能输出严格的 JSON 数组（不要多余文字、不要 markdown、不要代码块）。
输出数组长度必须与输入实体数量一致，顺序也必须一致。

每个元素必须且只能包含以下字段：
{"entityId":<原始id>,"label":"原始名称","category":"实体类型","modernName":"现代完整地名（省市区+具体地点）"}

要求：
1) modernName 必须是现代中国地图上可搜索到的真实地名（可被地图检索）
2) 尽量精确到具体景点/山/河/建筑物等，而非仅城市名
3) 古代地名映射到现代对应地名，例如：庐陵→江西省吉安市
4) 即使 category 不是 LOCATION，也必须给出最相关地点（出生地/活动地/发生地/总部等）
5) 不允许跳过任何输入实体：entityId/label/category 必须原样保留
6) 如果确实无法判断，也必须填写可检索兜底地点（例如：中国 或 北京市），不得留空

注意：只输出 JSON 数组本体。
""";

  public List<GeoPointDto> locate(GeoLocateRequest req) {
    List<GeoLocateRequest.EntityDto> entities = Optional.ofNullable(req.getEntities()).orElse(List.of());
    if (entities.isEmpty()) {
      log.warn("Geo locate: entities list is empty");
      return List.of();
    }

    // 1) 构造更稳的输入：严格 JSON 数组（而不是“每行一个JSON对象”）
    String userPrompt = buildEntitiesJsonArrayPrompt(entities);

    // 避免日志爆炸：只打印前 800 字
    String promptPreview = userPrompt.length() > 800 ? userPrompt.substring(0, 800) + "..." : userPrompt;
    log.info("Geo locate request: model={}, entitiesPromptPreview={}", req.getModel(), promptPreview);

    String model = Optional.ofNullable(req.getModel()).filter(s -> !s.isBlank()).orElse(null);

    // 2) LLM 映射古今地名
    JsonNode node = llmClient.chat(SYSTEM_PROMPT, userPrompt, model);

    if (node == null) {
      log.warn("Geo locate LLM response is null; fallback to direct geocoding");
    } else {
      String nodePreview = node.toString();
      log.info("Geo locate LLM response preview: {}",
          nodePreview.length() > 800 ? nodePreview.substring(0, 800) + "..." : nodePreview);
    }

    // 3) 解析 LLM 输出：必须能对齐到每个 entityId。缺失则兜底为 label/中国
    HashMap<Long, String> modernNameById = parseModernNames(node, entities);

    // 4) 调腾讯地图 API：逐个 geocode，并构造 fallbackCenter（用于后续 jitter）
    List<GeoPointDto> points = new ArrayList<>(entities.size());
    double[] fallbackCenter = null;

    for (GeoLocateRequest.EntityDto e : entities) {
      if (e == null || e.getId() == null) continue;

      String label = Optional.ofNullable(e.getLabel()).orElse("").trim();
      String category = Optional.ofNullable(e.getCategory()).orElse("").trim();
      String modernName = Optional.ofNullable(modernNameById.get(e.getId())).orElse("").trim();

      GeoPointDto point = geocodeEntity(e.getId(), label, modernName, category, fallbackCenter);
      if (point != null) {
        points.add(point);
        if (fallbackCenter == null && point.getLatitude() != null && point.getLongitude() != null
            && !"fallback".equals(point.getSource())) {
          fallbackCenter = new double[] {point.getLatitude(), point.getLongitude()};
        }
      }
    }

    log.info("Geo locate result: {} points returned for {} entities", points.size(), entities.size());
    return points;
  }

  /**
   * 把输入实体拼成“严格 JSON 数组”，减少模型输出漂移
   */
  private static String buildEntitiesJsonArrayPrompt(List<GeoLocateRequest.EntityDto> entities) {
    StringBuilder sb = new StringBuilder();
    sb.append("实体列表（JSON数组）：\n[\n");
    for (int i = 0; i < entities.size(); i++) {
      GeoLocateRequest.EntityDto e = entities.get(i);
      Long id = e == null ? null : e.getId();
      String label = e == null ? "" : Optional.ofNullable(e.getLabel()).orElse("").trim();
      String category = e == null ? "" : Optional.ofNullable(e.getCategory()).orElse("").trim();

      sb.append("  {\"entityId\":")
          .append(id == null ? "null" : id)
          .append(",\"label\":\"").append(jsonEscape(label)).append("\"")
          .append(",\"category\":\"").append(jsonEscape(category)).append("\"")
          .append("}");

      if (i < entities.size() - 1) sb.append(",");
      sb.append("\n");
    }
    sb.append("]\n");
    sb.append("请严格输出同长度JSON数组。\n");
    return sb.toString();
  }

  /**
   * 解析 LLM 输出，保证每个输入 entityId 都有一个 modernName
   */
  private HashMap<Long, String> parseModernNames(JsonNode node, List<GeoLocateRequest.EntityDto> entities) {
    HashMap<Long, String> modernNameById = new HashMap<>();
    Set<Long> inputIds = new HashSet<>();
    for (GeoLocateRequest.EntityDto e : entities) {
      if (e != null && e.getId() != null) inputIds.add(e.getId());
    }

    if (node != null && node.isArray()) {
      for (JsonNode n : node) {
        if (n == null || !n.isObject()) continue;
        Long entityId = n.has("entityId") && !n.get("entityId").isNull() ? n.get("entityId").asLong() : null;
        if (entityId == null || !inputIds.contains(entityId)) continue;

        String modernName = n.path("modernName").asText("").trim();
        if (!modernName.isBlank()) {
          modernNameById.putIfAbsent(entityId, modernName);
        }
      }
    } else if (node != null && node.isObject()) {
      // 少数情况下模型可能返回单对象
      Long entityId = node.has("entityId") && !node.get("entityId").isNull() ? node.get("entityId").asLong() : null;
      String modernName = node.path("modernName").asText("").trim();
      if (entityId != null && inputIds.contains(entityId) && !modernName.isBlank()) {
        modernNameById.putIfAbsent(entityId, modernName);
      }
    }

    // 补齐缺失：优先 label，否则北京市
    for (GeoLocateRequest.EntityDto e : entities) {
      if (e == null || e.getId() == null) continue;
      modernNameById.putIfAbsent(e.getId(), fallbackModernName(e));
    }

    return modernNameById;
  }

  private static String fallbackModernName(GeoLocateRequest.EntityDto e) {
    String label = e == null ? "" : Optional.ofNullable(e.getLabel()).orElse("").trim();
    if (!label.isBlank()) return label;
    return "北京市";
  }

  private GeoPointDto geocodeEntity(
      Long entityId,
      String label,
      String modernName,
      String category,
      double[] fallbackCenter
  ) {
    String resolvedLabel = Optional.ofNullable(label).orElse("").trim();
    String resolvedModernName = Optional.ofNullable(modernName).orElse("").trim();
    String resolvedCategory = Optional.ofNullable(category).orElse("").trim();

    GeocodingResult result = null;
    String note = null;

    // 1) 先用 modernName geocode（LLM 映射结果）
    if (!resolvedModernName.isBlank()) {
      result = tencentMapClient.geocode(resolvedModernName);
      note = resolvedModernName;

      // 1.1 简化后缀重试
      if (result == null) {
        String simplified = simplifyAddress(resolvedModernName);
        if (!simplified.equals(resolvedModernName) && !simplified.isBlank()) {
          result = tencentMapClient.geocode(simplified);
          note = simplified;
        }
      }

      // 1.2 加“中国”前缀重试（对短地名有用）
      if (result == null && !resolvedModernName.startsWith("中国")) {
        String withCountry = "中国" + resolvedModernName;
        result = tencentMapClient.geocode(withCountry);
        note = withCountry;
      }
    }

    // 2) 如果失败，再尝试 label（原始实体名）
    if (result == null && !resolvedLabel.isBlank()) {
      result = tencentMapClient.geocode(resolvedLabel);
      note = resolvedLabel;

      if (result == null && !resolvedLabel.startsWith("中国")) {
        String withCountry = "中国" + resolvedLabel;
        result = tencentMapClient.geocode(withCountry);
        note = withCountry;
      }
    }

    // 3) 仍失败：fallback 坐标（尽量靠近已有中心点）
    if (result == null) {
      double baseLat = 35.8617;
      double baseLng = 104.1954;

      // 优先用已有中心点（更像“同一篇文本的地理分布”）
      if (fallbackCenter != null && fallbackCenter.length == 2) {
        baseLat = fallbackCenter[0];
        baseLng = fallbackCenter[1];
      } else {
        // 取“中国”坐标做兜底（如果地图服务可用）
        GeocodingResult china = tencentMapClient.geocode("中国");
        if (china != null) {
          baseLat = china.latitude();
          baseLng = china.longitude();
        }
      }

      double[] jittered = jitterAround(baseLat, baseLng, entityId);

      log.warn("Geo fallback used: entityId={}, label='{}', modernName='{}'",
          entityId, resolvedLabel, resolvedModernName);

      return GeoPointDto.builder()
          .entityId(entityId)
          .label(resolvedLabel.isBlank() ? String.valueOf(entityId) : resolvedLabel)
          .latitude(jittered[0])
          .longitude(jittered[1])
          .source("fallback")
          .note(Optional.ofNullable(note).orElse("fallback"))
          .category(resolvedCategory)
          .build();
    }

    return GeoPointDto.builder()
        .entityId(entityId)
        .label(resolvedLabel.isBlank() ? String.valueOf(entityId) : resolvedLabel)
        .latitude(result.latitude())
        .longitude(result.longitude())
        .source("tencent_map")
        .note(Optional.ofNullable(note).orElse(resolvedModernName))
        .category(resolvedCategory)
        .build();
  }

  /**
   * jitter 更小一点，避免点散得太离谱（原来 0.06 ~ 0.03deg 可能很分散）
   */
  private static double[] jitterAround(double lat, double lng, Long entityId) {
    long seed = entityId != null ? entityId : 0L;
    java.util.Random r = new java.util.Random(seed ^ 0x9E3779B97F4A7C15L);
    double latOffset = (r.nextDouble() - 0.5) * 0.02; // ~ +/- 0.01 deg
    double lngOffset = (r.nextDouble() - 0.5) * 0.02;
    return new double[] {lat + latOffset, lng + lngOffset};
  }

  private static String jsonEscape(String s) {
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '"' -> out.append("\\\"");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private static String simplifyAddress(String address) {
    if (address == null) return "";
    return address
        .replaceAll("风景区$", "")
        .replaceAll("景区$", "")
        .replaceAll("旅游区$", "")
        .replaceAll("国家公园$", "")
        .replaceAll("自然保护区$", "")
        .trim();
  }
}
