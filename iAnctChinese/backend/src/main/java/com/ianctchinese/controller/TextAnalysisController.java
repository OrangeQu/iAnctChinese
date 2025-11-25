package com.ianctchinese.controller;

import com.ianctchinese.dto.AutoAnnotationResponse;
import com.ianctchinese.dto.ClassificationResponse;
import com.ianctchinese.dto.ModelAnalysisResponse;
import com.ianctchinese.dto.TextInsightsResponse;
import com.ianctchinese.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class TextAnalysisController {

  private final AnalysisService analysisService;

  @PostMapping("/{textId}/classify")
  public ResponseEntity<ClassificationResponse> classify(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.classifyText(textId, model));
  }

  @PostMapping("/{textId}/auto-annotate")
  public ResponseEntity<AutoAnnotationResponse> autoAnnotate(@PathVariable("textId") Long textId) {
    return ResponseEntity.ok(analysisService.autoAnnotate(textId));
  }

  @PostMapping("/{textId}/full")
  public ResponseEntity<ModelAnalysisResponse> fullAnalysis(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.runFullAnalysis(textId, model));
  }

  @GetMapping("/{textId}/insights")
  public ResponseEntity<TextInsightsResponse> insights(@PathVariable("textId") Long textId) {
    return ResponseEntity.ok(analysisService.buildInsights(textId));
  }
}
