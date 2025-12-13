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
      你是地理解析助手。请将输入的古代地名/景点映射到现代可搜索的地名。
      只输出严格的 JSON 数组（不要多余文字），每个元素格式：
      {"entityId":<原始id>,"label":"原始名称","modernName":"现代完整地名（省市区+具体地点）"}

      要求：
      1. modernName 必须是现代中国地图上可搜索到的真实地名
      2. 尽量精确到具体景点、山、河、建筑物等，而非仅城市名
      3. 如果是著名景点，请给出完整地址，如"安徽省滁州市琅琊山风景区醉翁亭"
      4. 如果是古代地名，映射到现代对应的地名，如"庐陵"→"江西省吉安市"
      5. 若无法确定现代地名，跳过该实体

      示例输出：
      [{"entityId":1,"label":"琅琊","modernName":"安徽省滁州市琅琊山风景区"},
       {"entityId":2,"label":"醉翁亭","modernName":"安徽省滁州市琅琊山风景区醉翁亭"}]
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
    entities.forEach(e -> userPrompt
        .append("{\"entityId\":").append(e.getId()).append(",\"label\":\"")
        .append(Optional.ofNullable(e.getLabel()).orElse("").trim()).append("\"}\n"));

    log.info("Geo locate request: model={}, entities={}", req.getModel(), userPrompt);

    String model = Optional.ofNullable(req.getModel()).filter(s -> !s.isBlank()).orElse(null);
    JsonNode node = llmClient.chat(SYSTEM_PROMPT, userPrompt.toString(), model);

    log.info("Geo locate LLM response: {}",
        node != null ? (node.toString().length() > 500 ? node.toString().substring(0, 500) + "..." : node.toString()) : "null");

    // 第二步：解析 LLM 返回的现代地名，调用腾讯地图 API 获取精确坐标
    List<GeoPointDto> points = new ArrayList<>();
    if (node != null && node.isArray()) {
      log.info("Processing {} entities from LLM response", node.size());
      for (JsonNode n : node) {
        GeoPointDto point = geocodeEntity(n, entityCategoryMap);
        if (point != null) {
          points.add(point);
        }
      }
    } else if (node != null && node.isObject()) {
      GeoPointDto point = geocodeEntity(node, entityCategoryMap);
      if (point != null) {
        points.add(point);
      }
    } else {
      log.warn("Geo locate LLM response invalid format or null");
    }

    log.info("Geo locate result: {} points found", points.size());
    return points;
  }

  private GeoPointDto geocodeEntity(JsonNode n, java.util.Map<Long, String> entityCategoryMap) {
    if (n == null || !n.isObject()) return null;

    Long entityId = n.has("entityId") && !n.get("entityId").isNull() ? n.get("entityId").asLong() : null;
    String label = n.path("label").asText("");
    String modernName = n.path("modernName").asText("");

    if (label.isBlank() || modernName.isBlank()) {
      log.debug("Skipping entity: label='{}', modernName='{}'", label, modernName);
      return null;
    }

    log.info("Geocoding: '{}' -> '{}'", label, modernName);

    // 调用腾讯地图地理编码 API
    GeocodingResult result = tencentMapClient.geocode(modernName);

    if (result == null) {
      // 如果精确地名失败，尝试简化搜索
      String simplifiedName = simplifyAddress(modernName);
      if (!simplifiedName.equals(modernName)) {
        log.info("Retrying with simplified address: '{}'", simplifiedName);
        result = tencentMapClient.geocode(simplifiedName);
      }
    }

    if (result == null) {
      log.warn("Failed to geocode '{}' (modernName: '{}')", label, modernName);
      return null;
    }

    // 从映射中获取 category
    String category = entityId != null ? entityCategoryMap.get(entityId) : null;

    return GeoPointDto.builder()
        .entityId(entityId)
        .label(label)
        .latitude(result.latitude())
        .longitude(result.longitude())
        .source("tencent_map")
        .note(modernName)
        .category(category)
        .build();
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
