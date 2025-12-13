package com.ianctchinese.llm.dto;

import lombok.Data;

@Data
public class SaveMarkerRequest {
    private Long textId;
    private Long entityId;
    private String entityLabel;
    private String category;
    private Double latitude;
    private Double longitude;
    private String source;
    private Integer orderIndex;
}

