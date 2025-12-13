package com.ianctchinese.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.ianctchinese.llm.TencentMapClient.GeocodingResult;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
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
  private final TencentMapClient tencentMapClient;

  private static final String SYSTEM_PROMPT = """
      你是地理解析助手。请将输入的实体映射到“现代中国地图上可搜索到的地名（modernName）”。
      只输出严格的 JSON 数组（不要多余文字），每个元素格式：
      {"entityId":<原始id>,"label":"原始名称","category":"实体类型","modernName":"现代完整地名（省市区+具体地点）"}

      要求：
      1. modernName 必须是现代中国地图上可搜索到的真实地名
      2. 尽量精确到具体景点、山、河、建筑物等，而非仅城市名
      3. 如果是著名景点，请给出完整地址，如"安徽省滁州市琅琊山风景区醉翁亭"
      4. 如果是古代地名，映射到现代对应的地名，如"庐陵"→"江西省吉安市"
      5. 即使实体类型不是 LOCATION（如 PERSON / EVENT / ORGANIZATION），也必须给出一个最相关的地理地点（出生地/活动地/发生地/总部所在地等）
      6. 不允许跳过任何输入实体：输出数组长度必须与输入实体数量一致，且 entityId/label/category 必须原样保留
      7. 如果确实无法判断，modernName 也必须填写一个可搜索的兜底地点（例如"中国"或"北京市"），不要留空

      示例输出：
      [{"entityId":1,"label":"琅琊","category":"LOCATION","modernName":"安徽省滁州市琅琊山风景区"},
       {"entityId":2,"label":"醉翁亭","category":"LOCATION","modernName":"安徽省滁州市琅琊山风景区醉翁亭"},
       {"entityId":3,"label":"欧阳修","category":"PERSON","modernName":"江西省九江市修水县"}]
      """;

  public List<GeoPointDto> locate(GeoLocateRequest req) {
    List<GeoLocateRequest.EntityDto> entities = Optional.ofNullable(req.getEntities())
        .orElse(List.of());
    if (entities.isEmpty()) {
      log.warn("Geo locate: entities list is empty");
      return List.of();
    }

    // 创建 entityId -> category 的映射
    java.util.Map<Long, String> entityCategoryMap = new java.util.HashMap<>();
    entities.forEach(e -> {
      if (e.getId() != null && e.getCategory() != null) {
        entityCategoryMap.put(e.getId(), e.getCategory());
      }
    });

    // 第一步：让 LLM 将古代地名映射到现代地名
    StringBuilder userPrompt = new StringBuilder("实体列表：\n");
    entities.forEach(e -> {
      String label = Optional.ofNullable(e.getLabel()).orElse("").trim();
      String category = Optional.ofNullable(e.getCategory()).orElse("").trim();
      userPrompt
          .append("{\"entityId\":").append(e.getId())
          .append(",\"label\":\"").append(jsonEscape(label)).append("\"")
          .append(",\"category\":\"").append(jsonEscape(category)).append("\"")
          .append("}\n");
    });

    log.info("Geo locate request: model={}, entities={}", req.getModel(), userPrompt);

    String model = Optional.ofNullable(req.getModel()).filter(s -> !s.isBlank()).orElse(null);
    JsonNode node = llmClient.chat(SYSTEM_PROMPT, userPrompt.toString(), model);

    log.info("Geo locate LLM response: {}",
        node != null ? (node.toString().length() > 500 ? node.toString().substring(0, 500) + "..." : node.toString()) : "null");

    // 第二步：解析 LLM 返回的 modernName，补齐缺失项，并调用腾讯地图 API 获取坐标
    java.util.Map<Long, String> modernNameById = new java.util.HashMap<>();
    if (node != null && node.isArray()) {
      log.info("Processing {} entities from LLM response", node.size());
      for (JsonNode n : node) {
        if (n == null || !n.isObject()) continue;
        Long entityId = n.has("entityId") && !n.get("entityId").isNull() ? n.get("entityId").asLong() : null;
        if (entityId == null) continue;
        String modernName = n.path("modernName").asText("").trim();
        if (!modernName.isBlank()) {
          modernNameById.putIfAbsent(entityId, modernName);
        }
      }
    } else if (node != null && node.isObject()) {
      Long entityId = node.has("entityId") && !node.get("entityId").isNull() ? node.get("entityId").asLong() : null;
      String modernName = node.path("modernName").asText("").trim();
      if (entityId != null && !modernName.isBlank()) {
        modernNameById.putIfAbsent(entityId, modernName);
      }
    } else {
      log.warn("Geo locate LLM response invalid format or null; will fallback to direct geocoding");
    }

    List<GeoPointDto> points = new ArrayList<>();
    double[] fallbackCenter = null;
    for (GeoLocateRequest.EntityDto e : entities) {
      if (e == null || e.getId() == null) continue;
      String label = Optional.ofNullable(e.getLabel()).orElse("").trim();
      String category = Optional.ofNullable(e.getCategory()).orElse("").trim();
      String modernName = Optional.ofNullable(modernNameById.get(e.getId())).orElse("").trim();
      GeoPointDto point = geocodeEntity(e.getId(), label, modernName, category, fallbackCenter);
      if (point != null) {
        points.add(point);
        if (fallbackCenter == null && point.getLatitude() != null && point.getLongitude() != null) {
          fallbackCenter = new double[] {point.getLatitude(), point.getLongitude()};
        }
      }
    }

    log.info("Geo locate result: {} points found", points.size());
    return points;
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

    GeocodingResult result = null;
    String note = null;

    if (!resolvedModernName.isBlank()) {
      log.info("Geocoding: '{}' -> '{}'", resolvedLabel, resolvedModernName);
      result = tencentMapClient.geocode(resolvedModernName);
      note = resolvedModernName;

      if (result == null) {
        String simplifiedName = simplifyAddress(resolvedModernName);
        if (!simplifiedName.equals(resolvedModernName)) {
          log.info("Retrying with simplified address: '{}'", simplifiedName);
          result = tencentMapClient.geocode(simplifiedName);
          note = simplifiedName;
        }
      }
    }

    // LLM 未返回 / 返回为空 / 地理编码失败：直接用 label 尝试一次
    if (result == null && !resolvedLabel.isBlank()) {
      result = tencentMapClient.geocode(resolvedLabel);
      note = resolvedLabel;
      if (result == null) {
        String withCountry = "中国" + resolvedLabel;
        result = tencentMapClient.geocode(withCountry);
        note = withCountry;
      }
    }

    // 兜底：保证每个实体都有可用坐标（用于前端展示）
    if (result == null) {
      GeocodingResult china = tencentMapClient.geocode("中国");
      double baseLat = china != null ? china.latitude() : 35.8617;
      double baseLng = china != null ? china.longitude() : 104.1954;
      if (fallbackCenter != null && fallbackCenter.length == 2) {
        baseLat = fallbackCenter[0];
        baseLng = fallbackCenter[1];
      }
      double[] jittered = jitterAround(baseLat, baseLng, entityId);
      log.warn("Fallback coordinate used for entityId={}, label='{}', modernName='{}'", entityId, resolvedLabel, resolvedModernName);
      return GeoPointDto.builder()
          .entityId(entityId)
          .label(resolvedLabel.isBlank() ? String.valueOf(entityId) : resolvedLabel)
          .latitude(jittered[0])
          .longitude(jittered[1])
          .source("fallback")
          .note(Optional.ofNullable(note).orElse("fallback"))
          .category(category)
          .build();
    }

    return GeoPointDto.builder()
        .entityId(entityId)
        .label(resolvedLabel.isBlank() ? String.valueOf(entityId) : resolvedLabel)
        .latitude(result.latitude())
        .longitude(result.longitude())
        .source("tencent_map")
        .note(Optional.ofNullable(note).orElse(resolvedModernName))
        .category(category)
        .build();
  }

  private static double[] jitterAround(double lat, double lng, Long entityId) {
    long seed = entityId != null ? entityId : 0L;
    java.util.Random r = new java.util.Random(seed ^ 0x9E3779B97F4A7C15L);
    double latOffset = (r.nextDouble() - 0.5) * 0.06; // ~ +/- 0.03 deg
    double lngOffset = (r.nextDouble() - 0.5) * 0.06;
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

  private String simplifyAddress(String address) {
    // 移除"风景区"、"景区"等后缀，简化搜索
    return address
        .replaceAll("风景区$", "")
        .replaceAll("景区$", "")
        .replaceAll("旅游区$", "")
        .trim();
  }
}
