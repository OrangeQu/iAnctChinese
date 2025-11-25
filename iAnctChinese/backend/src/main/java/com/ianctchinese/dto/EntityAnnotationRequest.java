package com.ianctchinese.dto;

import com.ianctchinese.model.EntityAnnotation.EntityCategory;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EntityAnnotationRequest {

  @NotNull
  private Long textId;

  private Long sectionId;

  @NotNull
  private Integer startOffset;

  @NotNull
  private Integer endOffset;

  @NotNull
  private String label;

  @NotNull
  private EntityCategory category;

  private Double confidence;

  private String color;
}
