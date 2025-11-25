package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextUploadRequest {

  @NotBlank
  private String title;

  @NotBlank
  private String content;

  private String category;

  private String author;

  private String era;
}
