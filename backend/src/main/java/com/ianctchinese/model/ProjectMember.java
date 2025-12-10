package com.ianctchinese.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "project_members",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"project_id", "user_id"})}
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMember {

  public enum Role {
    OWNER,
    MEMBER
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
