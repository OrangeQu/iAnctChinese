package com.ianctchinese.service;

import com.ianctchinese.dto.AutoAnnotationResponse;
import com.ianctchinese.dto.ClassificationResponse;
import com.ianctchinese.dto.ModelAnalysisResponse;
import com.ianctchinese.dto.TextInsightsResponse;
import com.ianctchinese.dto.TextInsightsResponse.WordCloudItem;
import java.util.List;
import java.util.Set;

public interface AnalysisService {

  ClassificationResponse classifyText(Long textId, String model);

  default TextInsightsResponse buildInsights(Long textId) {
    return buildInsights(textId, false);
  }

  TextInsightsResponse buildInsights(Long textId, boolean light);

  /**
   * 按需构建洞察，避免初始进入页面就触发耗时的外部调用（LLM/地图等）。
   * parts 约定（逗号分隔在 Controller 解析为 Set）：
   * - timeline
   * - mapPoints
   * - battleTimeline
   * - officialTree
   * - processCycle
   */
  default TextInsightsResponse buildInsights(Long textId, boolean light, Set<String> parts) {
    return buildInsights(textId, light);
  }

  AutoAnnotationResponse autoAnnotate(Long textId, String model);

  AutoAnnotationResponse extractRelations(Long textId, String model);

  AutoAnnotationResponse analyzeSentences(Long textId, String model);

  ModelAnalysisResponse runFullAnalysis(Long textId, String model);

  List<WordCloudItem> analyzeWordCloud(Long textId, String model);
}
