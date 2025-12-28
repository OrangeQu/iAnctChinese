package com.ianctchinese.dto;

import lombok.Data;

@Data
public class SpatialDataRequest {
  private Long entityId;
  private String entityName;
  private Long mapId;
  private Double x;
  private Double y;
  private Integer year;
  private String description;
}
