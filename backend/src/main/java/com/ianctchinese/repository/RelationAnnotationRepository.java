package com.ianctchinese.repository;

import com.ianctchinese.model.RelationAnnotation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RelationAnnotationRepository extends JpaRepository<RelationAnnotation, Long> {

  List<RelationAnnotation> findByTextDocumentId(Long textId);

  void deleteByTextDocumentId(Long textId);

  @Modifying
  @Query("delete from RelationAnnotation r where r.source.id = :entityId or r.target.id = :entityId")
  void deleteByEntityId(@Param("entityId") Long entityId);
}
