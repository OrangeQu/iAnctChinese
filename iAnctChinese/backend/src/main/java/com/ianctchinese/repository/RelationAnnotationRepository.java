package com.ianctchinese.repository;

import com.ianctchinese.model.RelationAnnotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationAnnotationRepository extends JpaRepository<RelationAnnotation, Long> {

  List<RelationAnnotation> findByTextDocumentId(Long textId);

  void deleteByTextDocumentId(Long textId);
}
