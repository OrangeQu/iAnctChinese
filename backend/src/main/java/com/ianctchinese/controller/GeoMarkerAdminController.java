package com.ianctchinese.controller;

import com.ianctchinese.dto.GeoMarkerUpdateRequest;
import com.ianctchinese.model.GeoMarker;
import com.ianctchinese.repository.GeoMarkerRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo-markers")
@RequiredArgsConstructor
public class GeoMarkerAdminController {

  private final GeoMarkerRepository geoMarkerRepository;

  @GetMapping
  public ResponseEntity<List<GeoMarker>> listMarkers(
      @RequestParam(value = "textId", required = false) Long textId,
      @RequestParam(value = "category", required = false) String category,
      @RequestParam(value = "source", required = false) String source,
      @RequestParam(value = "missingCoords", required = false) Boolean missingCoords,
      @RequestParam(value = "invalidCoords", required = false) Boolean invalidCoords) {
    List<GeoMarker> markers = textId == null
        ? geoMarkerRepository.findAll()
        : geoMarkerRepository.findByTextIdOrderByOrderIndexAsc(textId);

    if (category != null && !category.isBlank()) {
      String needle = category.trim().toLowerCase();
      markers = markers.stream()
          .filter(m -> m.getCategory() != null && m.getCategory().toLowerCase().contains(needle))
          .collect(Collectors.toList());
    }
    if (source != null && !source.isBlank()) {
      String needle = source.trim().toLowerCase();
      markers = markers.stream()
          .filter(m -> m.getSource() != null && m.getSource().toLowerCase().contains(needle))
          .collect(Collectors.toList());
    }
    if (Boolean.TRUE.equals(missingCoords)) {
      markers = markers.stream()
          .filter(m -> m.getLatitude() == null || m.getLongitude() == null)
          .collect(Collectors.toList());
    }
    if (Boolean.TRUE.equals(invalidCoords)) {
      markers = markers.stream()
          .filter(m -> m.getLatitude() != null && (m.getLatitude() < -90 || m.getLatitude() > 90)
              || m.getLongitude() != null && (m.getLongitude() < -180 || m.getLongitude() > 180))
          .collect(Collectors.toList());
    }
    return ResponseEntity.ok(markers);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateMarker(
      @PathVariable Long id,
      @RequestBody GeoMarkerUpdateRequest request) {
    GeoMarker marker = geoMarkerRepository.findById(id).orElse(null);
    if (marker == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "marker not found"));
    }
    if (request.getLatitude() != null) {
      marker.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      marker.setLongitude(request.getLongitude());
    }
    return ResponseEntity.ok(geoMarkerRepository.save(marker));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteMarker(@PathVariable Long id) {
    geoMarkerRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
