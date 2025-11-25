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
@Table(name = "entity_annotations")
@JsonIgnoreProperties({"textDocument"})
public class EntityAnnotation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "text_id")
  private TextDocument textDocument;

  @ManyToOne
  @JoinColumn(name = "section_id")
  private TextSection section;

  @Column(nullable = false)
  private Integer startOffset;

  @Column(nullable = false)
  private Integer endOffset;

  @Column(nullable = false)
  private String label;

  @Enumerated(EnumType.STRING)
  private EntityCategory category;

  private Double confidence;

  private String color;

  public enum EntityCategory {
    PERSON,
    LOCATION,
    EVENT,
    ORGANIZATION,
    OBJECT,
    CUSTOM
  }
}
