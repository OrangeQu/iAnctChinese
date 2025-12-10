package com.ianctchinese.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
  private Long id;
  private String name;
  private String description;
  private Long ownerId;
  private String ownerName;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<ProjectMemberInfo> members;

  @Data
  @Builder
  public static class ProjectMemberInfo {
    private Long userId;
    private String username;
    private String email;
    private String role;
  }
}
