package com.ianctchinese.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationQualityIssue {
  private Long relationId;
  private Long textId;
  private Long sourceId;
  private Long targetId;
  private Long sourceSectionId;
  private Long targetSectionId;
  private String relationType;
  private String evidence;
  private String issueType;
  private String message;
}
