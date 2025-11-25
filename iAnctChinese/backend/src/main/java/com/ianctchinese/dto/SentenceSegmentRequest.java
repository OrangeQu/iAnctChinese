package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SentenceSegmentRequest {

  @NotNull
  private Long textId;

  @NotBlank
  private String originalText;

  private String punctuatedText;

  private String summary;

  private Integer sequenceIndex;
}
