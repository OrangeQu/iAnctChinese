package com.ianctchinese.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityQualityIssue {
  private Long entityId;
  private Long textId;
  private Long sectionId;
  private Integer sectionIndex;
  private Integer startOffset;
  private Integer endOffset;
  private String label;
  private String category;
  private String issueType;
  private String message;
}
