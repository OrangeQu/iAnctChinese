package com.ianctchinese.llm.dto;

import lombok.Data;

@Data
public class HideMarkerRequest {
  private Long textId;
  private Long entityId;
  private String entityLabel;
}
