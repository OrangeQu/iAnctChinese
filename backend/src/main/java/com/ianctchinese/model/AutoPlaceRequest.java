package com.ianctchinese.model;

import lombok.Data;
import java.util.List;

@Data
public class AutoPlaceRequest {
    private Long mapId;
    private List<SimpleEntity> entities;

    @Data
    public static class SimpleEntity {
        private Long id;
        private String name;
        private String label; // 兼容前端字段
        private String category;
    }
}
