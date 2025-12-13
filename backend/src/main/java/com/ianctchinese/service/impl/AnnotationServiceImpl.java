package com.ianctchinese.service.impl;

import com.ianctchinese.dto.EntityAnnotationRequest;
import com.ianctchinese.dto.RelationAnnotationRequest;
import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.RelationAnnotation;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import com.ianctchinese.service.AnnotationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

  private final EntityAnnotationRepository entityRepository;
  private final RelationAnnotationRepository relationRepository;
  private final TextDocumentRepository textDocumentRepository;
  private final TextSectionRepository textSectionRepository;

  @Override
  @Transactional
  public EntityAnnotation createEntity(EntityAnnotationRequest request) {
    EntityAnnotation entity = EntityAnnotation.builder()
        .textDocument(textDocumentRepository.findById(request.getTextId())
            .orElseThrow(() -> new IllegalArgumentException("Text not found: " + request.getTextId())))
        .section(request.getSectionId() == null ? null :
            textSectionRepository.findById(request.getSectionId())
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + request.getSectionId())))
        .startOffset(request.getStartOffset())
        .endOffset(request.getEndOffset())
        .label(request.getLabel())
        .category(request.getCategory())
        .confidence(request.getConfidence())
        .color(request.getColor())
        .build();
    return entityRepository.save(entity);
  }

  @Override
  @Transactional
  public RelationAnnotation createRelation(RelationAnnotationRequest request) {
    RelationAnnotation relation = RelationAnnotation.builder()
        .textDocument(textDocumentRepository.findById(request.getTextId())
            .orElseThrow(() -> new IllegalArgumentException("Text not found: " + request.getTextId())))
        .source(entityRepository.findById(request.getSourceEntityId())
            .orElseThrow(() -> new IllegalArgumentException("Source entity not found: " + request.getSourceEntityId())))
        .target(entityRepository.findById(request.getTargetEntityId())
            .orElseThrow(() -> new IllegalArgumentException("Target entity not found: " + request.getTargetEntityId())))
        .relationType(request.getRelationType())
        .confidence(request.getConfidence())
        .evidence(request.getEvidence())
        .build();
    return relationRepository.save(relation);
  }

  @Override
  public List<EntityAnnotation> getEntities(Long textId) {
    return entityRepository.findByTextDocumentId(textId);
  }

  @Override
  public List<RelationAnnotation> getRelations(Long textId) {
    return relationRepository.findByTextDocumentId(textId);
  }

  @Override
  @Transactional
  public void deleteEntity(Long entityId) {
    EntityAnnotation entity = entityRepository.findById(entityId)
        .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + entityId));
    // 先删关系，再删实体，避免外键约束
    relationRepository.deleteByEntityId(entityId);
    entityRepository.delete(entity);
  }
}
