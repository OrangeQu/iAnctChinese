package com.ianctchinese.dto;

import com.ianctchinese.model.RelationAnnotation.RelationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RelationAnnotationRequest {

  @NotNull
  private Long textId;

  @NotNull
  private Long sourceEntityId;

  @NotNull
  private Long targetEntityId;

  @NotNull
  private RelationType relationType;

  private Double confidence;

  private String evidence;
}
