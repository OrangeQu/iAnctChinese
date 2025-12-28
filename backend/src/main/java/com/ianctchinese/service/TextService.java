package com.ianctchinese.service;

import com.ianctchinese.dto.TextUploadRequest;
import com.ianctchinese.model.TextDocument;
import java.util.List;

public interface TextService {

  TextDocument createText(TextUploadRequest request, String username);

  org.springframework.data.domain.Page<TextDocument> listTexts(
      String category,
      String era,
      String author,
      Long projectId,
      Boolean deleted,
      String username,
      org.springframework.data.domain.Pageable pageable);

  TextDocument getText(Long id);

  TextDocument updateCategory(Long id, String category);

  List<TextDocument> searchTexts(String keyword);

  void deleteText(Long id);

  TextDocument updateText(Long id, com.ianctchinese.dto.TextUpdateRequest request);
}
