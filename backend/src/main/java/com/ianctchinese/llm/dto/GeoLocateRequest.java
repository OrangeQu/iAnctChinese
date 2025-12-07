package com.ianctchinese.llm.dto;

import java.util.List;
import lombok.Data;

@Data
public class GeoLocateRequest {
  private Long textId;
  private String model;
  private List<EntityDto> entities;

  @Data
  public static class EntityDto {
    private Long id;
    private String label;
    private String category;
  }
}
