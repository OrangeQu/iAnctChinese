package com.ianctchinese.service;

import com.ianctchinese.dto.EntityAnnotationRequest;
import com.ianctchinese.dto.RelationAnnotationRequest;
import com.ianctchinese.model.EntityAnnotation;
import com.ianctchinese.model.RelationAnnotation;
import java.util.List;

public interface AnnotationService {

  EntityAnnotation createEntity(EntityAnnotationRequest request);

  RelationAnnotation createRelation(RelationAnnotationRequest request);

  List<EntityAnnotation> getEntities(Long textId);

  List<RelationAnnotation> getRelations(Long textId);
}
