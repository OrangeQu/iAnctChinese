package com.ianctchinese.dto;

import lombok.Data;

@Data
public class AdminUserResetPasswordRequest {
  private String newPassword;
}
