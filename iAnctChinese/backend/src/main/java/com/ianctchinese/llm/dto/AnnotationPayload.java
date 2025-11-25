package com.ianctchinese.llm.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnnotationPayload {

  @Builder.Default
  private List<AnnotationEntity> entities = new ArrayList<>();

  @Builder.Default
  private List<AnnotationRelation> relations = new ArrayList<>();

  @Builder.Default
  private List<SentenceSuggestion> sentences = new ArrayList<>();

  @Builder.Default
  private List<WordCloudItem> wordCloud = new ArrayList<>();

  @Data
  @Builder
  public static class AnnotationEntity {

    private String label;
    private String category;
    private Integer startOffset;
    private Integer endOffset;
    private Double confidence;
  }

  @Data
  @Builder
  public static class AnnotationRelation {

    private String sourceLabel;
    private String targetLabel;
    private String relationType;
    private Double confidence;
    private String description;
  }

  @Data
  @Builder
  public static class WordCloudItem {

    private String label;
    private Double weight;
  }
}
