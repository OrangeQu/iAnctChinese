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
public class UserSummaryResponse {

  private Long id;
  private String username;
  private String email;
  private Boolean enabled;
  private LocalDateTime createTime;
  private LocalDateTime lastLoginTime;
}
