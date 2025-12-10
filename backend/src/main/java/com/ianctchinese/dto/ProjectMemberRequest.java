package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectMemberRequest {

  @NotBlank(message = "用户名不能为空")
  private String username;
}
