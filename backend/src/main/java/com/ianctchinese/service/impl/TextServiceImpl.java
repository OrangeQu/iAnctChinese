package com.ianctchinese.service.impl;

import com.ianctchinese.dto.TextUploadRequest;
import com.ianctchinese.dto.TextUpdateRequest;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.ModelJobRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import com.ianctchinese.service.TextService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TextServiceImpl implements TextService {

  private final TextDocumentRepository textDocumentRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final TextSectionRepository textSectionRepository;
  private final ModelJobRepository modelJobRepository;

  @Override
  @Transactional
  public TextDocument createText(TextUploadRequest request) {
    String resolvedCategory = Optional.ofNullable(request.getCategory())
        .filter(value -> !value.isBlank())
        .orElse("unknown");
    String resolvedAuthor = Optional.ofNullable(request.getAuthor())
        .filter(value -> !value.isBlank())
        .orElse("未填写");
    String resolvedContent = Optional.ofNullable(request.getContent())
        .orElse("");
    String resolvedDescription = Optional.ofNullable(request.getDescription())
        .orElse("");
    TextDocument textDocument = TextDocument.builder()
        .title(request.getTitle())
        .content(resolvedContent)
        .description(resolvedDescription)
        .category(resolvedCategory)
        .author(resolvedAuthor)
        .era(request.getEra())
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    return textDocumentRepository.save(textDocument);
  }

  @Override
  public List<TextDocument> listTexts(String category) {
    if (category == null || category.isBlank()) {
      return textDocumentRepository.findActive();
    }
    return textDocumentRepository.findActiveByCategory(category);
  }

  @Override
  public TextDocument getText(Long id) {
    TextDocument doc = textDocumentRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Text not found: " + id));
    if (Boolean.TRUE.equals(doc.getIsDeleted())) {
      throw new IllegalArgumentException("Text has been deleted: " + id);
    }
    return doc;
  }

  @Override
  @Transactional
  public TextDocument updateCategory(Long id, String category) {
    TextDocument document = getText(id);
    document.setCategory(category);
    document.setUpdatedAt(LocalDateTime.now());
    return textDocumentRepository.save(document);
  }

  @Override
  public List<TextDocument> searchTexts(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    return textDocumentRepository.searchByKeyword(keyword.trim());
  }

  @Override
  @Transactional
  public void deleteText(Long id) {
    TextDocument doc = getText(id);
    doc.setIsDeleted(true);
    textDocumentRepository.save(doc);
  }

  @Override
  @Transactional
  public TextDocument updateText(Long id, TextUpdateRequest request) {
    TextDocument doc = getText(id);
    doc.setTitle(Optional.ofNullable(request.getTitle()).orElse(doc.getTitle()));
    doc.setContent(Optional.ofNullable(request.getContent()).orElse(doc.getContent()));
    doc.setAuthor(Optional.ofNullable(request.getAuthor()).orElse(doc.getAuthor()));
    doc.setEra(request.getEra());
    doc.setCategory(Optional.ofNullable(request.getCategory()).orElse(doc.getCategory()));
    doc.setUpdatedAt(LocalDateTime.now());
    return textDocumentRepository.save(doc);
  }
}
