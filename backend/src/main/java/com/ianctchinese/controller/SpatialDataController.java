package com.ianctchinese.controller;

import com.ianctchinese.dto.SpatialDataRequest;
import com.ianctchinese.model.EntitySpatialData;
import com.ianctchinese.repository.EntitySpatialDataRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spatial")
@RequiredArgsConstructor
public class SpatialDataController {

  private final EntitySpatialDataRepository spatialRepository;

  @GetMapping
  public ResponseEntity<List<EntitySpatialData>> list(
      @RequestParam(name = "mapId", required = false) Long mapId,
      @RequestParam(name = "year", required = false) Integer year) {
    return ResponseEntity.ok(spatialRepository.search(mapId, year));
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody SpatialDataRequest request) {
    if (request.getMapId() == null || request.getEntityId() == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "mapId and entityId are required"));
    }
    EntitySpatialData data = new EntitySpatialData();
    data.setMapId(request.getMapId());
    data.setEntityId(request.getEntityId());
    data.setEntityName(request.getEntityName());
    data.setX(request.getX());
    data.setY(request.getY());
    data.setYear(request.getYear());
    data.setDescription(request.getDescription());
    return ResponseEntity.ok(spatialRepository.save(data));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SpatialDataRequest request) {
    EntitySpatialData existing = spatialRepository.findById(id).orElse(null);
    if (existing == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "spatial data not found"));
    }
    existing.setEntityId(request.getEntityId() == null ? existing.getEntityId() : request.getEntityId());
    existing.setEntityName(request.getEntityName() == null ? existing.getEntityName() : request.getEntityName());
    existing.setMapId(request.getMapId() == null ? existing.getMapId() : request.getMapId());
    existing.setX(request.getX() == null ? existing.getX() : request.getX());
    existing.setY(request.getY() == null ? existing.getY() : request.getY());
    existing.setYear(request.getYear() == null ? existing.getYear() : request.getYear());
    existing.setDescription(request.getDescription() == null ? existing.getDescription() : request.getDescription());
    return ResponseEntity.ok(spatialRepository.save(existing));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    spatialRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
