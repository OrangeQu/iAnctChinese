package com.ianctchinese.dto;

import com.ianctchinese.model.ModelJob;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {

  private long textCount;
  private long entityCount;
  private long relationCount;
  private long modelJobCount;
  private List<ModelJob> recentJobs;
}
