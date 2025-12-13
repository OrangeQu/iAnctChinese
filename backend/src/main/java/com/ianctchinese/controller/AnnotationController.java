package com.ianctchinese.controller;

import com.ianctchinese.dto.EntityAnnotationRequest;
import com.ianctchinese.dto.RelationAnnotationRequest;
import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.RelationAnnotation;
import com.ianctchinese.service.AnnotationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

  private final AnnotationService annotationService;

  @PostMapping("/entities")
  public ResponseEntity<EntityAnnotation> createEntity(@Valid @RequestBody EntityAnnotationRequest request) {
    return ResponseEntity.ok(annotationService.createEntity(request));
  }

  @DeleteMapping("/entities/{id}")
  public ResponseEntity<Void> deleteEntity(@PathVariable("id") Long id) {
    annotationService.deleteEntity(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/relations")
  public ResponseEntity<RelationAnnotation> createRelation(@Valid @RequestBody RelationAnnotationRequest request) {
    return ResponseEntity.ok(annotationService.createRelation(request));
  }

  @GetMapping("/entities")
  public ResponseEntity<List<EntityAnnotation>> listEntities(@RequestParam("textId") Long textId) {
    return ResponseEntity.ok(annotationService.getEntities(textId));
  }

  @GetMapping("/relations")
  public ResponseEntity<List<RelationAnnotation>> listRelations(@RequestParam("textId") Long textId) {
    return ResponseEntity.ok(annotationService.getRelations(textId));
  }
}
