package com.ianctchinese.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "model_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String modelKey;

  @Column(nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String provider;

  @Column(nullable = false)
  private Boolean enabled;

  @Column(name = "sort_order")
  private Integer sortOrder;

  private String description;
}
