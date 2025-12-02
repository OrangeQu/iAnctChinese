package com.ianctchinese.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class MapInfo {
    @Id
    private Long id;             // 手动指定ID (1, 2, 3...)
    private String dynasty;      // 朝代名称，如 "唐朝"
    private String filename;     // 文件名，如 "唐时期全图 (二).jpg"
    private Integer startYear;   // 开始年份
    private Integer endYear;     // 结束年份
    private Double width;        // 图片像素宽
    private Double height;       // 图片像素高
}