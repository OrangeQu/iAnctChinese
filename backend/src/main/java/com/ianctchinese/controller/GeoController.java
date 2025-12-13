package com.ianctchinese.controller;

import com.ianctchinese.llm.GeoService;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
import com.ianctchinese.llm.dto.SaveMarkerRequest;
import com.ianctchinese.model.GeoMarker;
import com.ianctchinese.repository.GeoMarkerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

  private final GeoService geoService;
  private final GeoMarkerRepository geoMarkerRepository;

  @PostMapping("/locate")
  public ResponseEntity<List<GeoPointDto>> locate(@RequestBody GeoLocateRequest request) {
    return ResponseEntity.ok(geoService.locate(request));
  }

  @PostMapping("/marker")
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

    return ResponseEntity.ok(geoMarkerRepository.save(marker));
  }

  @GetMapping("/markers/{textId}")
  public ResponseEntity<List<GeoMarker>> getMarkers(@PathVariable Long textId) {
    return ResponseEntity.ok(geoMarkerRepository.findByTextIdOrderByOrderIndexAsc(textId));
  }

  @DeleteMapping("/marker/{textId}/{entityId}")
  public ResponseEntity<Void> deleteMarker(@PathVariable Long textId, @PathVariable Long entityId) {
    geoMarkerRepository.deleteByTextIdAndEntityId(textId, entityId);
    return ResponseEntity.ok().build();
  }
}
