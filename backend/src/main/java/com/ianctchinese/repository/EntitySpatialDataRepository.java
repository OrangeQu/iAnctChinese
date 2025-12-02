package com.ianctchinese.repository;

import com.ianctchinese.model.EntitySpatialData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EntitySpatialDataRepository extends JpaRepository<EntitySpatialData, Long> {
    List<EntitySpatialData> findByMapId(Long mapId);
    
    @Modifying
    @Transactional
    void deleteByMapId(Long mapId);

    // 【新增】查找该实体在该地图上最近一次的标注记录（用于自动记忆）
    @Query(value = "SELECT * FROM entity_spatial_data WHERE map_id = :mapId AND entity_name = :name ORDER BY id DESC LIMIT 1", nativeQuery = true)
    EntitySpatialData findLatestByMapIdAndName(@Param("mapId") Long mapId, @Param("name") String name);
    
    // 【新增】检查当前地图是否已经存在该实体（防止重复自动标注）
    boolean existsByMapIdAndEntityId(Long mapId, Long entityId);
}