package com.ianctchinese.llm.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimelineEnrichment {

  private String summary;              // 原文摘要
  private List<String> participants;   // 相关人物
  private String location;             // 地点
  private String impact;               // 历史影响
}
