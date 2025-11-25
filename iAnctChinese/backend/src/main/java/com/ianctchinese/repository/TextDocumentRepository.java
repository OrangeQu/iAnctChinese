package com.ianctchinese.repository;

import com.ianctchinese.model.TextDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TextDocumentRepository extends JpaRepository<TextDocument, Long> {

  @Query("SELECT t FROM TextDocument t WHERE t.isDeleted = false OR t.isDeleted IS NULL")
  List<TextDocument> findActive();

  @Query("SELECT t FROM TextDocument t WHERE (t.isDeleted = false OR t.isDeleted IS NULL) AND t.category = :category")
  List<TextDocument> findActiveByCategory(@Param("category") String category);

  @Query("SELECT t FROM TextDocument t WHERE (t.isDeleted = false OR t.isDeleted IS NULL) AND (" +
      "LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
      "LOWER(t.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
      "LOWER(t.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  List<TextDocument> searchByKeyword(@Param("keyword") String keyword);
}
