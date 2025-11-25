package com.ianctchinese.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutoAnnotationResponse {

  private Long textId;
  private Integer createdEntities;
  private Integer createdRelations;
  private String message;
}
