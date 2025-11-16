package com.ianctchinese.llm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SentenceSuggestion {

  private String original;
  private String punctuated;
  private String summary;
}
