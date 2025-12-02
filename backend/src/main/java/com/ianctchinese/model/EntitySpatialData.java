package com.ianctchinese.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "entity_spatial_data")
public class EntitySpatialData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long entityId;       // 对应实体的ID
    private String entityName;   // 冗余存一个名字，方便前端直接显示
    private Long mapId;          // 对应 MapInfo 的 ID
    
    // 坐标 (图片像素坐标)
    private Double x; 
    private Double y; 
    
    private Integer year;        // 时间轴年份
    
    @Column(length = 1000)
    private String description;
}