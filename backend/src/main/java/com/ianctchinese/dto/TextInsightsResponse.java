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
  private List<OfficialNode> officialTree;
  private List<ProcessStep> processCycle;
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
    private String eventType;
    private String location;
    private List<String> participants;
    private String impact;
    private Long entityId;
    private Integer startOffset;
    private Integer endOffset;
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

  @Data
  @Builder
  public static class OfficialNode {

    private String name;
    private String position;
    private String level;
    private String department;
    private List<OfficialNode> subordinates;
    private String description;
  }

  @Data
  @Builder
  public static class ProcessStep {

    private String name;
    private String description;
    private Integer sequence;
    private String category;
    private List<String> tools;
    private List<String> materials;
    private String output;
    private Integer duration;
  }
}
