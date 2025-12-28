package com.ianctchinese.controller;

import com.ianctchinese.dto.SentenceSegmentRequest;
import com.ianctchinese.dto.SentenceUpdateRequest;
import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.GeoMarker;
import com.ianctchinese.model.RelationAnnotation;
import com.ianctchinese.model.TextSection;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.GeoMarkerRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.service.TextSectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TextSectionController {

  private final TextSectionService textSectionService;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;
  private final GeoMarkerRepository geoMarkerRepository;

  @GetMapping("/texts/{textId}/sections")
  public ResponseEntity<Page<TextSection>> listSections(
      @PathVariable Long textId,
      Pageable pageable) {
    return ResponseEntity.ok(textSectionService.listSections(textId, pageable));
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

  @PutMapping("/sections/{sectionId}")
  public ResponseEntity<TextSection> replaceSection(@PathVariable Long sectionId,
      @Valid @RequestBody SentenceUpdateRequest request) {
    return ResponseEntity.ok(textSectionService.updateSection(sectionId, request));
  }

  @GetMapping("/sections/{sectionId}/entities")
  public ResponseEntity<List<EntityAnnotation>> listSectionEntities(@PathVariable Long sectionId) {
    return ResponseEntity.ok(entityAnnotationRepository.findBySectionIdOrderByStartOffset(sectionId));
  }

  @GetMapping("/sections/{sectionId}/relations")
  public ResponseEntity<List<RelationAnnotation>> listSectionRelations(@PathVariable Long sectionId) {
    return ResponseEntity.ok(relationAnnotationRepository.findBySectionId(sectionId));
  }

  @GetMapping("/sections/{sectionId}/markers")
  public ResponseEntity<List<GeoMarker>> listSectionMarkers(@PathVariable Long sectionId) {
    List<Long> entityIds = entityAnnotationRepository.findBySectionIdOrderByStartOffset(sectionId)
        .stream()
        .map(EntityAnnotation::getId)
        .collect(Collectors.toList());
    if (entityIds.isEmpty()) {
      return ResponseEntity.ok(List.of());
    }
    return ResponseEntity.ok(geoMarkerRepository.findByEntityIdIn(entityIds));
  }
}
