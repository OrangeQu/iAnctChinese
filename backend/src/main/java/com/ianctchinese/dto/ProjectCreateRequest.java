package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectCreateRequest {

  @NotBlank(message = "项目名称不能为空")
  @Size(max = 200, message = "项目名称长度不能超过200")
  private String name;

  @Size(max = 2000, message = "描述长度不能超过2000")
  private String description;
}
