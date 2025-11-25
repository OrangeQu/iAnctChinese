package com.ianctchinese.dto;

import com.ianctchinese.model.ModelJob.JobType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModelJobRequest {

  @NotNull
  private Long textId;

  @NotNull
  private JobType jobType;

  private String payload;
}
