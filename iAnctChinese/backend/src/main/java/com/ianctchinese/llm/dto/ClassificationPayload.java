package com.ianctchinese.llm.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassificationPayload {

  private String category;
  private double confidence;
  @Builder.Default
  private List<String> reasons = new ArrayList<>();
}
