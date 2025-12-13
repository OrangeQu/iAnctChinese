package com.ianctchinese.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "geo_markers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"textId", "entityId"})
})
public class GeoMarker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long textId;
    private Long entityId;
    private String entityLabel;
    private String category;
    
    private Double latitude;
    private Double longitude;
    
    private String source;
    
    private Integer orderIndex;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

