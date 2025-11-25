package com.ianctchinese.service;

import com.ianctchinese.dto.TextUploadRequest;
import com.ianctchinese.model.TextDocument;
import java.util.List;

public interface TextService {

  TextDocument createText(TextUploadRequest request);

  List<TextDocument> listTexts(String category);

  TextDocument getText(Long id);

  TextDocument updateCategory(Long id, String category);

  List<TextDocument> searchTexts(String keyword);

  void deleteText(Long id);

  TextDocument updateText(Long id, com.ianctchinese.dto.TextUpdateRequest request);
}
