package com.ianctchinese.controller;

import com.ianctchinese.model.HiddenGeoMarker;
import com.ianctchinese.repository.HiddenGeoMarkerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo-hides")
@RequiredArgsConstructor
public class GeoHideAdminController {

  private final HiddenGeoMarkerRepository hiddenGeoMarkerRepository;

  @GetMapping
  public ResponseEntity<List<HiddenGeoMarker>> listHidden(@RequestParam("textId") Long textId) {
    return ResponseEntity.ok(hiddenGeoMarkerRepository.findByTextId(textId));
  }

  @DeleteMapping
  public ResponseEntity<Void> clearHidden(@RequestParam("textId") Long textId) {
    hiddenGeoMarkerRepository.deleteByTextId(textId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> restoreHidden(@PathVariable Long id) {
    hiddenGeoMarkerRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
