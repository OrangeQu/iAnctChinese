package com.ianctchinese.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePasswordRequest {

  @NotBlank(message = "当前密码不能为空")
  private String currentPassword;

  @NotBlank(message = "新密码不能为空")
  @Size(min = 6, max = 64, message = "新密码长度需在6到64个字符之间")
  private String newPassword;
}
