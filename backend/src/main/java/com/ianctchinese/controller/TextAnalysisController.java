package com.ianctchinese.controller;

import com.ianctchinese.dto.AutoAnnotationResponse;
import com.ianctchinese.dto.ClassificationResponse;
import com.ianctchinese.dto.ModelAnalysisResponse;
import com.ianctchinese.dto.TextInsightsResponse;
import com.ianctchinese.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
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
  public ResponseEntity<AutoAnnotationResponse> autoAnnotate(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.autoAnnotate(textId, model));
  }

  @PostMapping("/{textId}/relations")
  public ResponseEntity<AutoAnnotationResponse> extractRelations(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.extractRelations(textId, model));
  }

  @PostMapping("/{textId}/segments")
  public ResponseEntity<AutoAnnotationResponse> analyzeSegments(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.analyzeSentences(textId, model));
  }

  @PostMapping("/{textId}/full")
  public ResponseEntity<ModelAnalysisResponse> fullAnalysis(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.runFullAnalysis(textId, model));
  }

  @GetMapping("/{textId}/insights")
  public ResponseEntity<TextInsightsResponse> insights(@PathVariable("textId") Long textId,
      @RequestParam(value = "light", defaultValue = "true") boolean light,
      @RequestParam(value = "parts", required = false) String parts) {
    Set<String> partSet = null;
    if (parts != null && !parts.isBlank()) {
      partSet = Arrays.stream(parts.split(","))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .collect(Collectors.toSet());
      if (partSet.isEmpty()) {
        partSet = Collections.emptySet();
      }
    }
    return ResponseEntity.ok(analysisService.buildInsights(textId, light, partSet));
  }

  @GetMapping("/{textId}/word-cloud")
  public ResponseEntity<?> wordCloud(@PathVariable("textId") Long textId,
      @RequestParam(value = "model", required = false) String model) {
    return ResponseEntity.ok(analysisService.analyzeWordCloud(textId, model));
  }
}
