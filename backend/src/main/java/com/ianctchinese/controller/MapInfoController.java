package com.ianctchinese.controller;

import com.ianctchinese.model.MapInfo;
import com.ianctchinese.repository.MapInfoRepository;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maps")
@RequiredArgsConstructor
public class MapInfoController {

  private final MapInfoRepository mapInfoRepository;

  @GetMapping
  public ResponseEntity<List<MapInfo>> listMaps() {
    return ResponseEntity.ok(mapInfoRepository.findAll());
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody MapInfo request) {
    if (request.getId() == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "id is required"));
    }
    if (mapInfoRepository.existsById(request.getId())) {
      return ResponseEntity.badRequest().body(Map.of("message", "id already exists"));
    }
    return ResponseEntity.ok(mapInfoRepository.save(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody MapInfo request) {
    MapInfo existing = mapInfoRepository.findById(id)
        .orElse(null);
    if (existing == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "map not found"));
    }
    existing.setDynasty(request.getDynasty());
    existing.setFilename(request.getFilename());
    existing.setStartYear(request.getStartYear());
    existing.setEndYear(request.getEndYear());
    existing.setWidth(request.getWidth());
    existing.setHeight(request.getHeight());
    return ResponseEntity.ok(mapInfoRepository.save(existing));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    mapInfoRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
