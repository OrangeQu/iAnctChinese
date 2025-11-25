package com.ianctchinese.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NavigationTreeResponse {

  private String projectName;
  private List<CategoryNode> categories;

  @Data
  @Builder
  public static class CategoryNode {

    private String category;
    private String label;
    private List<TextNode> texts;
  }

  @Data
  @Builder
  public static class TextNode {

    private Long id;
    private String title;
    private String author;
    private String era;
    private List<SectionNode> sections;
  }

  @Data
  @Builder
  public static class SectionNode {

    private Long id;
    private Integer sequenceIndex;
    private String summary;
  }
}
