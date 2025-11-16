package com.ianctchinese.dto;

import com.ianctchinese.model.TextSection;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelAnalysisResponse {

  private ClassificationResponse classification;
  private AutoAnnotationResponse annotation;
  private TextInsightsResponse insights;
  private List<TextSection> sections;
}
