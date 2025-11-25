package com.ianctchinese.repository;

import com.ianctchinese.model.TextSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextSectionRepository extends JpaRepository<TextSection, Long> {

  List<TextSection> findByTextDocumentId(Long textId);

  void deleteByTextDocumentId(Long textId);
}
