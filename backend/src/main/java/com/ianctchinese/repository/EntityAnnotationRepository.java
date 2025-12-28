package com.ianctchinese.repository;

import com.ianctchinese.model.EntityAnnotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityAnnotationRepository extends JpaRepository<EntityAnnotation, Long> {

  List<EntityAnnotation> findByTextDocumentId(Long textId);

  List<EntityAnnotation> findBySectionIdOrderByStartOffset(Long sectionId);

  void deleteByTextDocumentId(Long textId);

  long countByTextDocumentIdIn(List<Long> textIds);

  List<EntityAnnotation> findTop20ByLabelContainingIgnoreCaseOrderByIdDesc(String label);
}
