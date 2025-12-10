package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TextUploadRequest {

  @NotBlank(message = "文档名称不能为空")
  private String title;

  private String content;

  private String description;

  private Long projectId;

  private String category;

  private String author;

  private String era;
}
