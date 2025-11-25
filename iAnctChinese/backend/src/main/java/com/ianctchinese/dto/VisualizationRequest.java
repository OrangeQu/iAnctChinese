package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VisualizationRequest {

  @NotNull
  private Long textId;

  @NotBlank
  private String textCategory;

  @NotBlank
  private String viewType;

  private String configJson;
}
