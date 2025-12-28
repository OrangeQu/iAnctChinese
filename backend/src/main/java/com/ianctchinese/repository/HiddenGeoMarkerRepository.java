package com.ianctchinese.repository;

import com.ianctchinese.model.HiddenGeoMarker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HiddenGeoMarkerRepository extends JpaRepository<HiddenGeoMarker, Long> {

    List<HiddenGeoMarker> findByTextId(Long textId);

    void deleteByTextIdAndEntityId(Long textId, Long entityId);

    void deleteByTextIdAndEntityLabel(Long textId, String entityLabel);

    boolean existsByTextIdAndEntityId(Long textId, Long entityId);

    boolean existsByTextIdAndEntityLabel(Long textId, String entityLabel);

    long countByTextIdIn(List<Long> textIds);

    void deleteByTextId(Long textId);
}
