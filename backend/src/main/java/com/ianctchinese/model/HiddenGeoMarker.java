package com.ianctchinese.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
    name = "geo_marker_hides",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"text_id", "entity_id"})}
)
public class HiddenGeoMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text_id", nullable = false)
    private Long textId;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_label")
    private String entityLabel;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
