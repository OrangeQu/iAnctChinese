package com.ianctchinese.service;

import com.ianctchinese.dto.AutoAnnotationResponse;
import com.ianctchinese.dto.ClassificationResponse;
import com.ianctchinese.dto.ModelAnalysisResponse;
import com.ianctchinese.dto.TextInsightsResponse;

public interface AnalysisService {

  ClassificationResponse classifyText(Long textId, String model);

  TextInsightsResponse buildInsights(Long textId);

  AutoAnnotationResponse autoAnnotate(Long textId);

  ModelAnalysisResponse runFullAnalysis(Long textId, String model);
}
