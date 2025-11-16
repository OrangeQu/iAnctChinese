package com.ianctchinese.service.impl;

import com.ianctchinese.dto.SentenceSegmentRequest;
import com.ianctchinese.dto.SentenceUpdateRequest;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.model.TextSection;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import com.ianctchinese.service.TextSectionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TextSectionServiceImpl implements TextSectionService {

  private final TextDocumentRepository textDocumentRepository;
  private final TextSectionRepository textSectionRepository;

  @Override
  public List<TextSection> listSections(Long textId) {
    return textSectionRepository.findByTextDocumentId(textId).stream()
        .sorted(Comparator.comparing(TextSection::getSequenceIndex))
        .toList();
  }

  @Override
  @Transactional
  public List<TextSection> autoSegment(Long textId) {
    TextDocument document = textDocumentRepository.findById(textId)
        .orElseThrow(() -> new IllegalArgumentException("Text not found: " + textId));
    textSectionRepository.deleteByTextDocumentId(textId);
    String content = document.getContent();
    String[] sentences = content.split("(?<=[。！？])");
    List<TextSection> sections = new ArrayList<>();
    int index = 1;
    for (String sentence : sentences) {
      if (sentence == null || sentence.isBlank()) {
        continue;
      }
      String trimmed = sentence.trim();
      TextSection section = TextSection.builder()
          .textDocument(document)
          .sequenceIndex(index++)
          .originalText(trimmed)
          .punctuatedText(trimmed)
          .summary(trimmed.length() > 20 ? trimmed.substring(0, 20) + "…" : trimmed)
          .build();
      sections.add(section);
    }
    return textSectionRepository.saveAll(sections);
  }

  @Override
  @Transactional
  public List<TextSection> replaceSections(Long textId, List<SentenceSegmentRequest> segments) {
    textSectionRepository.deleteByTextDocumentId(textId);
    TextDocument document = textDocumentRepository.findById(textId)
        .orElseThrow(() -> new IllegalArgumentException("Text not found: " + textId));
    List<TextSection> sections = new ArrayList<>();
    int index = 1;
    for (SentenceSegmentRequest segment : segments) {
      if (segment.getOriginalText() == null || segment.getOriginalText().isBlank()) {
        continue;
      }
      TextSection section = TextSection.builder()
          .textDocument(document)
          .sequenceIndex(Optional.ofNullable(segment.getSequenceIndex()).orElse(index))
          .originalText(segment.getOriginalText())
          .punctuatedText(segment.getPunctuatedText())
          .summary(segment.getSummary())
          .build();
      sections.add(section);
      index++;
    }
    return textSectionRepository.saveAll(sections);
  }

  @Override
  @Transactional
  public TextSection createSection(SentenceSegmentRequest request) {
    TextDocument document = textDocumentRepository.findById(request.getTextId())
        .orElseThrow(() -> new IllegalArgumentException("Text not found: " + request.getTextId()));
    int nextIndex = request.getSequenceIndex() != null ? request.getSequenceIndex()
        : listSections(request.getTextId()).size() + 1;
    TextSection section = TextSection.builder()
        .textDocument(document)
        .sequenceIndex(nextIndex)
        .originalText(request.getOriginalText())
        .punctuatedText(request.getPunctuatedText())
        .summary(request.getSummary())
        .build();
    return textSectionRepository.save(section);
  }

  @Override
  @Transactional
  public TextSection updateSection(Long sectionId, SentenceUpdateRequest request) {
    TextSection section = textSectionRepository.findById(sectionId)
        .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
    section.setOriginalText(request.getOriginalText());
    section.setPunctuatedText(request.getPunctuatedText());
    section.setSummary(request.getSummary());
    return textSectionRepository.save(section);
  }
}
