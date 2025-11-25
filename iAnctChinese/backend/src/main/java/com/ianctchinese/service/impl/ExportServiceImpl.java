package com.ianctchinese.service.impl;

import com.ianctchinese.dto.TextExportBundle;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import com.ianctchinese.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

  private final TextDocumentRepository textDocumentRepository;
  private final TextSectionRepository textSectionRepository;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;

  @Override
  public TextExportBundle buildExport(Long textId) {
    TextDocument document = textDocumentRepository.findById(textId)
        .orElseThrow(() -> new IllegalArgumentException("Text not found: " + textId));
    return TextExportBundle.builder()
        .text(document)
        .sections(textSectionRepository.findByTextDocumentId(textId))
        .entities(entityAnnotationRepository.findByTextDocumentId(textId))
        .relations(relationAnnotationRepository.findByTextDocumentId(textId))
        .build();
  }
}
