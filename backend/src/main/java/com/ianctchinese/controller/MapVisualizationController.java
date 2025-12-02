package com.ianctchinese.controller;

import com.ianctchinese.model.EntitySpatialData;
import com.ianctchinese.model.MapInfo;
import com.ianctchinese.repository.EntitySpatialDataRepository;
import com.ianctchinese.service.MapVisualizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visualization")
public class MapVisualizationController {

    @Autowired
    private MapVisualizationService mapService;

    @Autowired
    private EntitySpatialDataRepository spatialRepository;

    @GetMapping("/maps")
    public List<MapInfo> getMaps() {
        return mapService.getAllMaps();
    }

    @GetMapping("/markers/{mapId}")
    public List<EntitySpatialData> getMarkers(@PathVariable Long mapId) {
        return mapService.getMarkersByMap(mapId);
    }

    @PostMapping("/markers")
    public EntitySpatialData saveMarker(@RequestBody EntitySpatialData data) {
        return mapService.saveMarker(data);
    }

    @DeleteMapping("/markers/map/{mapId}")
    public void clearMarkersByMap(@PathVariable Long mapId) {
        spatialRepository.deleteByMapId(mapId);
    }

    // ==========================================
    // ✅ 【新增】处理前端 "自动标注" 的请求
    // ==========================================
    @PostMapping("/markers/auto")
    public List<EntitySpatialData> autoPlaceMarkers(@RequestBody Map<String, Object> payload) {
        // 1. 解析参数
        Integer mapIdInt = (Integer) payload.get("mapId");
        Long mapId = Long.valueOf(mapIdInt);
        
        List<Map<String, Object>> entities = (List<Map<String, Object>>) payload.get("entities");
        
        List<EntitySpatialData> newMarkers = new ArrayList<>();

        // 2. 遍历前端传来的所有实体
        for (Map<String, Object> entity : entities) {
            Long entityId = ((Number) entity.get("id")).longValue();
            String name = (String) (entity.get("label") != null ? entity.get("label") : entity.get("name"));
            String category = (String) entity.get("category");

            // 3. 检查：如果当前地图已经标了这个点，就跳过
            if (spatialRepository.existsByMapIdAndEntityId(mapId, entityId)) {
                continue;
            }

            // 4. 记忆查找：去数据库查一下，这个名字以前在这个地图上标过吗？
            EntitySpatialData history = spatialRepository.findLatestByMapIdAndName(mapId, name);

            if (history != null) {
                // 5. 找到了历史记忆！创建一个新标记复用坐标
                EntitySpatialData newMarker = new EntitySpatialData();
                newMarker.setMapId(mapId);
                newMarker.setEntityId(entityId);
                newMarker.setEntityName(name);
                newMarker.setX(history.getX());
                newMarker.setY(history.getY());
                newMarker.setYear(history.getYear()); // 复用年份
                newMarker.setDescription("自动记忆标注");
                
                // 保存并添加到返回列表
                newMarkers.add(spatialRepository.save(newMarker));
            } else {
                // (可选) 这里可以加预设硬编码
                // if (name.equals("长安")) { ... setX(1200); setY(800); ... }
            }
        }

        return newMarkers; // 返回新创建的标记列表，前端会画出来
    }
}