package com.ianctchinese.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

  private Long id;
  private String username;
  private String email;
  private LocalDateTime createTime;
  private LocalDateTime lastLoginTime;
}
