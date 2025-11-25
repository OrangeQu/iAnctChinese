package com.ianctchinese.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextInsightsResponse {

  private Long textId;
  private String category;
  private Stats stats;
  private List<WordCloudItem> wordCloud;
  private List<TimelineEvent> timeline;
  private List<MapPathPoint> mapPoints;
  private List<BattleEvent> battleTimeline;
  private List<FamilyNode> familyTree;
  private List<String> recommendedViews;
  private String analysisSummary;

  @Data
  @Builder
  public static class Stats {

    private Integer entityCount;
    private Integer relationCount;
    private Double punctuationProgress;
  }

  @Data
  @Builder
  public static class WordCloudItem {

    private String label;
    private Double weight;
  }

  @Data
  @Builder
  public static class TimelineEvent {

    private String title;
    private String description;
    private String dateLabel;
    private Integer significance;
    private String eventType;             // 事件类型：birth, official, battle, travel, death等

    // 新增字段
    private String location;              // 地点
    private List<String> participants;    // 相关人物
    private String impact;                // 历史影响
    private List<String> relatedEvents;   // 关联事件

    // 连接到古文内容的位置信息
    private Long entityId;                // 关联的实体ID
    private Integer startOffset;          // 在原文中的起始位置
    private Integer endOffset;            // 在原文中的结束位置

    // Setter方法用于后处理
    public void setDateLabel(String dateLabel) {
      this.dateLabel = dateLabel;
    }

    public void setImpact(String impact) {
      this.impact = impact;
    }

    public void setLocation(String location) {
      this.location = location;
    }

    public void setParticipants(List<String> participants) {
      this.participants = participants;
    }
  }

  @Data
  @Builder
  public static class MapPathPoint {

    private String label;
    private Double latitude;
    private Double longitude;
    private Integer sequence;
  }

  @Data
  @Builder
  public static class BattleEvent {

    private String phase;
    private String description;
    private Integer intensity;
    private String opponent;
  }

  @Data
  @Builder
  public static class FamilyNode {

    private String name;
    private String relation;
    private List<FamilyNode> children;
  }
}
