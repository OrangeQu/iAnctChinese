package com.ianctchinese.controller;

import com.ianctchinese.dto.PresetRequest;
import com.ianctchinese.model.VisualizationPreset;
import com.ianctchinese.service.VisualizationService;
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
@RequestMapping("/api/presets")
@RequiredArgsConstructor
public class PresetController {

  private final VisualizationService visualizationService;

  @GetMapping
  public ResponseEntity<List<VisualizationPreset>> listPresets(
      @RequestParam(name = "category", required = false) String category) {
    return ResponseEntity.ok(visualizationService.listPresets(category));
  }

  @PostMapping
  public ResponseEntity<?> createPreset(@RequestBody PresetRequest request) {
    if (request.getLabel() == null || request.getLabel().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "label is required"));
    }
    if (request.getTextCategory() == null || request.getTextCategory().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "textCategory is required"));
    }
    return ResponseEntity.ok(visualizationService.createPreset(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updatePreset(@PathVariable Long id, @RequestBody PresetRequest request) {
    VisualizationPreset preset = visualizationService.updatePreset(id, request);
    if (preset == null) {
      return ResponseEntity.badRequest().body(Map.of("message", "preset not found"));
    }
    return ResponseEntity.ok(preset);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePreset(@PathVariable Long id) {
    visualizationService.deletePreset(id);
    return ResponseEntity.noContent().build();
  }
}
