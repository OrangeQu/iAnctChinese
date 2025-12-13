package com.ianctchinese.repository;

import com.ianctchinese.model.GeoMarker;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GeoMarkerRepository extends JpaRepository<GeoMarker, Long> {
    List<GeoMarker> findByTextIdOrderByOrderIndexAsc(Long textId);
    
    Optional<GeoMarker> findByTextIdAndEntityId(Long textId, Long entityId);
    
    void deleteByTextIdAndEntityId(Long textId, Long entityId);
    
    void deleteByTextId(Long textId);
}

