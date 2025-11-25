package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextCategoryUpdateRequest {

  @NotBlank
  private String category;
}
