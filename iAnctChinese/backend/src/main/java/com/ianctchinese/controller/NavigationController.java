package com.ianctchinese.controller;

import com.ianctchinese.dto.NavigationTreeResponse;
import com.ianctchinese.dto.NavigationTreeResponse.CategoryNode;
import com.ianctchinese.dto.NavigationTreeResponse.SectionNode;
import com.ianctchinese.dto.NavigationTreeResponse.TextNode;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.model.TextSection;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
public class NavigationController {

  private final TextDocumentRepository textDocumentRepository;
  private final TextSectionRepository textSectionRepository;

  @GetMapping("/tree")
  public ResponseEntity<NavigationTreeResponse> tree() {
    List<TextDocument> texts = textDocumentRepository.findAll();
    Map<Long, List<TextSection>> sectionMap = texts.stream()
        .collect(Collectors.toMap(TextDocument::getId, text -> textSectionRepository.findByTextDocumentId(text.getId())));
    Map<String, List<TextDocument>> grouped = texts.stream()
        .collect(Collectors.groupingBy(TextDocument::getCategory));

    List<CategoryNode> categories = grouped.entrySet().stream()
        .map(entry -> CategoryNode.builder()
            .category(entry.getKey())
            .label(resolveCategoryLabel(entry.getKey()))
            .texts(entry.getValue().stream()
                .map(text -> TextNode.builder()
                    .id(text.getId())
                    .title(text.getTitle())
                    .author(text.getAuthor())
                    .era(text.getEra())
                    .sections(sectionMap.getOrDefault(text.getId(), List.of()).stream()
                        .map(section -> SectionNode.builder()
                            .id(section.getId())
                            .sequenceIndex(section.getSequenceIndex())
                            .summary(section.getSummary())
                            .build())
                        .toList())
                    .build())
                .toList())
            .build())
        .toList();

    NavigationTreeResponse response = NavigationTreeResponse.builder()
        .projectName("iAnctChinese")
        .categories(categories)
        .build();
    return ResponseEntity.ok(response);
  }

  private String resolveCategoryLabel(String category) {
    return switch (category) {
      case "warfare" -> "战争纪实";
      case "travelogue" -> "游记地理";
      case "biography" -> "人物传记";
      default -> "综合文本";
    };
  }
}
