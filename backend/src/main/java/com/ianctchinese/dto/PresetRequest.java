package com.ianctchinese.dto;

import lombok.Data;

@Data
public class PresetRequest {
  private String label;
  private String textCategory;
  private String configJson;
  private Boolean isDefault;
}
