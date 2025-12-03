package com.ianctchinese.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "relation_annotations")
@JsonIgnoreProperties({"textDocument"})
public class RelationAnnotation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "text_id")
  private TextDocument textDocument;

  @ManyToOne
  @JoinColumn(name = "source_entity_id")
  private EntityAnnotation source;

  @ManyToOne
  @JoinColumn(name = "target_entity_id")
  private EntityAnnotation target;

  // 显式声明枚举取值，便于 Hibernate 在 MySQL 上将 ENUM 同步为完整的类型集合，避免“Data truncated for column 'relation_type'”
  @Column(columnDefinition = "enum('ALLY','SUPPORT','RIVAL','CONFLICT','FAMILY','MENTOR','INFLUENCE','LOCATION_OF','PART_OF','CAUSE','TEMPORAL','TRAVEL','CUSTOM')")
  @Enumerated(EnumType.STRING)
  private RelationType relationType;

  private Double confidence;

  @Column(length = 1024)
  private String evidence;

  public enum RelationType {
    // 人物/阵营
    ALLY,
    SUPPORT,
    RIVAL,
    CONFLICT,
    FAMILY,
    MENTOR,
    INFLUENCE,
    // 空间/结构
    LOCATION_OF,
    PART_OF,
    // 因果/时间/行旅
    CAUSE,
    TEMPORAL,
    TRAVEL,
    // 兜底
    CUSTOM
  }
}
