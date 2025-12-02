package com.ianctchinese.service;

import com.ianctchinese.model.AutoPlaceRequest;
import com.ianctchinese.model.EntitySpatialData;
import com.ianctchinese.model.MapInfo;
import com.ianctchinese.repository.EntitySpatialDataRepository;
import com.ianctchinese.repository.MapInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MapVisualizationService {

    @Autowired
    private MapInfoRepository mapInfoRepository;

    @Autowired
    private EntitySpatialDataRepository spatialRepository;

    public List<MapInfo> getAllMaps() {
        return mapInfoRepository.findAll();
    }

    public EntitySpatialData saveMarker(EntitySpatialData data) {
        return spatialRepository.save(data);
    }

    public List<EntitySpatialData> getMarkersByMap(Long mapId) {
        return spatialRepository.findByMapId(mapId);
    }

    /**
     * 粗定位自动标注：将未落点的实体按简单辐射布局分布到地图中心附近，便于用户后续微调。
     */
    public List<EntitySpatialData> autoPlaceMarkers(AutoPlaceRequest payload) {
        if (payload == null || payload.getMapId() == null || payload.getEntities() == null) {
            return Collections.emptyList();
        }
        Long mapId = payload.getMapId();
        Optional<MapInfo> mapOpt = mapInfoRepository.findById(mapId);
        double width = mapOpt.map(m -> m.getWidth() != null ? m.getWidth() : 2000d).orElse(2000d);
        double height = mapOpt.map(m -> m.getHeight() != null ? m.getHeight() : 1400d).orElse(1400d);
        Integer year = mapOpt.map(MapInfo::getStartYear).orElse(null);

        List<EntitySpatialData> existing = spatialRepository.findByMapId(mapId);
        Set<Long> placedIds = new HashSet<>();
        for (EntitySpatialData m : existing) {
            if (m.getEntityId() != null) {
                placedIds.add(m.getEntityId());
            }
        }

        List<AutoPlaceRequest.SimpleEntity> candidates = new ArrayList<>();
        for (AutoPlaceRequest.SimpleEntity e : payload.getEntities()) {
            if (e == null || e.getId() == null) continue;
            if (placedIds.contains(e.getId())) continue; // already mapped
            candidates.add(e);
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double baseRadius = Math.min(width, height) / 4.0;
        List<EntitySpatialData> saved = new ArrayList<>();
        int total = candidates.size();
        for (int i = 0; i < total; i++) {
            AutoPlaceRequest.SimpleEntity e = candidates.get(i);
            double angle = (2 * Math.PI * i) / Math.max(total, 1);
            double radius = baseRadius * (0.55 + 0.15 * (i % 3));
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            // clamp to image bounds
            x = Math.max(20, Math.min(width - 20, x));
            y = Math.max(20, Math.min(height - 20, y));

            EntitySpatialData data = new EntitySpatialData();
            data.setMapId(mapId);
            data.setEntityId(e.getId());
            data.setEntityName(e.getLabel() != null ? e.getLabel() : e.getName());
            data.setX(x);
            data.setY(y);
            data.setYear(year);
            data.setDescription("自动标注（粗定位）");
            saved.add(spatialRepository.save(data));
        }
        return saved;
    }
}
