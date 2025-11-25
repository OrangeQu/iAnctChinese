package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SentenceUpdateRequest {

  private String punctuatedText;

  private String summary;

  @NotBlank
  private String originalText;
}
