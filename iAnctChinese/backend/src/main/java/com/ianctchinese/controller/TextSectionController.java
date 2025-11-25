package com.ianctchinese.controller;

import com.ianctchinese.dto.SentenceSegmentRequest;
import com.ianctchinese.dto.SentenceUpdateRequest;
import com.ianctchinese.model.TextSection;
import com.ianctchinese.service.TextSectionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TextSectionController {

  private final TextSectionService textSectionService;

  @GetMapping("/texts/{textId}/sections")
  public ResponseEntity<List<TextSection>> listSections(@PathVariable Long textId) {
    return ResponseEntity.ok(textSectionService.listSections(textId));
  }

  @PostMapping("/texts/{textId}/sections/auto")
  public ResponseEntity<List<TextSection>> autoSegment(@PathVariable Long textId) {
    return ResponseEntity.ok(textSectionService.autoSegment(textId));
  }

  @PostMapping("/texts/{textId}/sections")
  public ResponseEntity<TextSection> createSection(@PathVariable Long textId,
      @Valid @RequestBody SentenceSegmentRequest request) {
    request.setTextId(textId);
    return ResponseEntity.ok(textSectionService.createSection(request));
  }

  @PatchMapping("/sections/{sectionId}")
  public ResponseEntity<TextSection> updateSection(@PathVariable Long sectionId,
      @Valid @RequestBody SentenceUpdateRequest request) {
    return ResponseEntity.ok(textSectionService.updateSection(sectionId, request));
  }
}
