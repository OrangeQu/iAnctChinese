package com.ianctchinese.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
@JsonIgnoreProperties({"sections"})
@Table(name = "texts")
public class TextDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 8192)
  private String content;

  @Column(nullable = false)
  private String category;

  @Column(nullable = false)
  private String author;

  private String era;

  @Column(name = "uploaded_by")
  private String uploadedBy;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @Builder.Default
  @Column(name = "is_deleted", nullable = false)
  private Boolean isDeleted = false;

  @Builder.Default
  @OneToMany(mappedBy = "textDocument", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TextSection> sections = new ArrayList<>();
}
