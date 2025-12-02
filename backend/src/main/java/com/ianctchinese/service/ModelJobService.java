package com.ianctchinese.service;

import com.ianctchinese.dto.ModelJobRequest;
import com.ianctchinese.model.ModelJob;
import java.util.List;

public interface ModelJobService {

  ModelJob enqueueJob(ModelJobRequest request);

  List<ModelJob> listJobs(Long textId);

  List<ModelJob> listAllJobs();

  /**
   * 记录已执行完毕的任务，便于审计/列表展示。
   */
  ModelJob recordJob(Long textId, ModelJob.JobType jobType, ModelJob.JobStatus status, String payload,
      String resultData);
}
