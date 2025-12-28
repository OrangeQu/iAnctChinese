package com.ianctchinese.controller;

import com.ianctchinese.dto.EntityQualityIssue;
import com.ianctchinese.dto.RelationQualityIssue;
import com.ianctchinese.service.QualityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

  private final QualityService qualityService;

  @GetMapping("/entities")
  public ResponseEntity<List<EntityQualityIssue>> entityIssues(
      @RequestParam("textId") Long textId) {
    return ResponseEntity.ok(qualityService.checkEntityIssues(textId));
  }

  @GetMapping("/relations")
  public ResponseEntity<List<RelationQualityIssue>> relationIssues(
      @RequestParam("textId") Long textId) {
    return ResponseEntity.ok(qualityService.checkRelationIssues(textId));
  }
}
