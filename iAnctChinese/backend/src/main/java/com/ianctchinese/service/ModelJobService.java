package com.ianctchinese.service;

import com.ianctchinese.dto.ModelJobRequest;
import com.ianctchinese.model.ModelJob;
import java.util.List;

public interface ModelJobService {

  ModelJob enqueueJob(ModelJobRequest request);

  List<ModelJob> listJobs(Long textId);
}
