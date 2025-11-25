package com.ianctchinese.repository;

import com.ianctchinese.model.EntityAnnotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityAnnotationRepository extends JpaRepository<EntityAnnotation, Long> {

  List<EntityAnnotation> findByTextDocumentId(Long textId);

  void deleteByTextDocumentId(Long textId);
}
