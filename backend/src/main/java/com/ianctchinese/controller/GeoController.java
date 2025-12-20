package com.ianctchinese.controller;

import com.ianctchinese.llm.GeoService;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
import com.ianctchinese.llm.dto.HideMarkerRequest;
import com.ianctchinese.llm.dto.SaveMarkerRequest;
import com.ianctchinese.model.GeoMarker;
import com.ianctchinese.model.HiddenGeoMarker;
import com.ianctchinese.repository.GeoMarkerRepository;
import com.ianctchinese.repository.HiddenGeoMarkerRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

  private final GeoService geoService;
  private final GeoMarkerRepository geoMarkerRepository;
  private final HiddenGeoMarkerRepository hiddenGeoMarkerRepository;

  @PostMapping("/locate")
  public ResponseEntity<List<GeoPointDto>> locate(@RequestBody GeoLocateRequest request) {
    return ResponseEntity.ok(geoService.locate(request));
  }

  @PostMapping("/marker")
  @Transactional
  public ResponseEntity<GeoMarker> saveMarker(@RequestBody SaveMarkerRequest request) {
    GeoMarker marker = geoMarkerRepository
        .findByTextIdAndEntityId(request.getTextId(), request.getEntityId())
        .orElse(new GeoMarker());

    marker.setTextId(request.getTextId());
    marker.setEntityId(request.getEntityId());
    marker.setEntityLabel(request.getEntityLabel());
    marker.setCategory(request.getCategory());
    marker.setLatitude(request.getLatitude());
    marker.setLongitude(request.getLongitude());
    marker.setSource(request.getSource());
    marker.setOrderIndex(request.getOrderIndex());
    hiddenGeoMarkerRepository.deleteByTextIdAndEntityId(request.getTextId(), request.getEntityId());
    if (request.getEntityLabel() != null && !request.getEntityLabel().isBlank()) {
      hiddenGeoMarkerRepository.deleteByTextIdAndEntityLabel(request.getTextId(), request.getEntityLabel());
    }

    return ResponseEntity.ok(geoMarkerRepository.save(marker));
  }

  @GetMapping("/markers/{textId}")
  public ResponseEntity<List<GeoMarker>> getMarkers(@PathVariable Long textId) {
    List<HiddenGeoMarker> hiddenEntries = hiddenGeoMarkerRepository.findByTextId(textId);
    Set<Long> hiddenEntityIds = hiddenEntries.stream()
        .map(HiddenGeoMarker::getEntityId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    List<GeoMarker> markers = geoMarkerRepository.findByTextIdOrderByOrderIndexAsc(textId);
    if (!hiddenEntityIds.isEmpty()) {
      markers = markers.stream()
          .filter(marker -> marker.getEntityId() == null || !hiddenEntityIds.contains(marker.getEntityId()))
          .toList();
    }
    Set<String> hiddenLabels = hiddenEntries.stream()
        .map(HiddenGeoMarker::getEntityLabel)
        .filter(Objects::nonNull)
        .map(String::trim)
        .collect(Collectors.toSet());
    if (!hiddenLabels.isEmpty()) {
      markers = markers.stream()
          .filter(marker -> marker.getEntityLabel() == null || !hiddenLabels.contains(marker.getEntityLabel().trim()))
          .toList();
    }
    return ResponseEntity.ok(markers);
  }

  @GetMapping("/hidden/{textId}")
  public ResponseEntity<List<HiddenGeoMarker>> getHiddenMarkers(@PathVariable Long textId) {
    return ResponseEntity.ok(hiddenGeoMarkerRepository.findByTextId(textId));
  }

  @PostMapping("/marker/hide")
  @Transactional
  public ResponseEntity<Void> hideMarker(@RequestBody HideMarkerRequest request) {
    recordHiddenMarker(request.getTextId(), request.getEntityId(), request.getEntityLabel());
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/marker/{textId}/{entityId}")
  @Transactional
  public ResponseEntity<Void> deleteMarker(@PathVariable Long textId, @PathVariable Long entityId) {
    geoMarkerRepository.deleteByTextIdAndEntityId(textId, entityId);
    recordHiddenMarker(textId, entityId, null);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/markers/{markerId}")
  @Transactional
  public ResponseEntity<Void> deleteMarkerById(@PathVariable Long markerId) {
    geoMarkerRepository.findById(markerId).ifPresent(marker -> {
      recordHiddenMarker(marker.getTextId(), marker.getEntityId(), marker.getEntityLabel());
      geoMarkerRepository.delete(marker);
    });
    return ResponseEntity.ok().build();
  }

  @Transactional
  private void recordHiddenMarker(Long textId, Long entityId, String entityLabel) {
    if (textId == null) {
      return;
    }
    if (entityId == null && (entityLabel == null || entityLabel.isBlank())) {
      return;
    }
    String trimmedLabel = entityLabel == null ? null : entityLabel.trim();
    if (entityId != null && hiddenGeoMarkerRepository.existsByTextIdAndEntityId(textId, entityId)) {
      return;
    }
    if (entityId == null && trimmedLabel != null && hiddenGeoMarkerRepository.existsByTextIdAndEntityLabel(textId, trimmedLabel)) {
      return;
    }
    HiddenGeoMarker hidden = new HiddenGeoMarker();
    hidden.setTextId(textId);
    hidden.setEntityId(entityId);
    hidden.setEntityLabel(trimmedLabel);
    hiddenGeoMarkerRepository.save(hidden);
  }
}
