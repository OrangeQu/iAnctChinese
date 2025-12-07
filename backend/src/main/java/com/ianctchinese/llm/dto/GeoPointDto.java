package com.ianctchinese.llm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoPointDto {
  private Long entityId;
  private String label;
  private Double latitude;
  private Double longitude;
  private String source;
  private String note;
}
