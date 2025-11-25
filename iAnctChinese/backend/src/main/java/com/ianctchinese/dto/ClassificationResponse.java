package com.ianctchinese.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassificationResponse {

  private Long textId;
  private String suggestedCategory;
  private Double confidence;
  private List<String> reasons;
}
