package com.ianctchinese.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "model_jobs")
public class ModelJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long textId;

  @Enumerated(EnumType.STRING)
  private JobType jobType;

  @Enumerated(EnumType.STRING)
  private JobStatus status;

  @Lob
  private String payload;

  @Lob
  private String resultData;

  private LocalDateTime createdAt;

  private LocalDateTime completedAt;

  public enum JobType {
    TYPE_CLASSIFICATION,
    ENTITY_EXTRACTION,
    RELATION_EXTRACTION,
    PUNCTUATION,
    SUMMARY
  }

  public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
  }
}
