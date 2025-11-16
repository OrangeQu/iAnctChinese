package com.ianctchinese.service;

import com.ianctchinese.dto.SentenceSegmentRequest;
import com.ianctchinese.dto.SentenceUpdateRequest;
import com.ianctchinese.model.TextSection;
import java.util.List;

public interface TextSectionService {

  List<TextSection> listSections(Long textId);

  List<TextSection> autoSegment(Long textId);

  List<TextSection> replaceSections(Long textId, List<SentenceSegmentRequest> segments);

  TextSection createSection(SentenceSegmentRequest request);

  TextSection updateSection(Long sectionId, SentenceUpdateRequest request);
}
