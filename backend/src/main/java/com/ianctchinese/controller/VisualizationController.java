package com.ianctchinese.controller;

import com.ianctchinese.dto.VisualizationRequest;
import com.ianctchinese.model.VisualizationPreset;
import com.ianctchinese.service.VisualizationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visualizations")
@RequiredArgsConstructor
public class VisualizationController {

  private final VisualizationService visualizationService;

  @PostMapping
  public ResponseEntity<VisualizationPreset> savePreset(@Valid @RequestBody VisualizationRequest request) {
    return ResponseEntity.ok(visualizationService.savePreset(request));
  }

  @GetMapping
  public ResponseEntity<List<VisualizationPreset>> listPresets(
      @RequestParam(name = "category", required = false) String category) {
    return ResponseEntity.ok(visualizationService.listPresets(category));
  }
}
