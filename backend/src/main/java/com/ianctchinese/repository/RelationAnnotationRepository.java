package com.ianctchinese.repository;

import com.ianctchinese.model.RelationAnnotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RelationAnnotationRepository extends JpaRepository<RelationAnnotation, Long> {

  List<RelationAnnotation> findByTextDocumentId(Long textId);

  @Query("select r from RelationAnnotation r where r.source.section.id = :sectionId or r.target.section.id = :sectionId")
  List<RelationAnnotation> findBySectionId(@Param("sectionId") Long sectionId);

  void deleteBySourceIdOrTargetId(Long sourceId, Long targetId);

  void deleteByTextDocumentId(Long textId);

  long countByTextDocumentIdIn(List<Long> textIds);
}
