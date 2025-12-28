package com.ianctchinese.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStatsResponse {
  private long textCount;
  private long sectionCount;
  private long entityCount;
  private long relationCount;
  private long markerCount;
  private long hiddenCount;
  private long jobCount;
}
