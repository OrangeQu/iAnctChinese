package com.ianctchinese.service.impl;

import com.ianctchinese.dto.ModelJobRequest;
import com.ianctchinese.llm.LargeLanguageModelClient;
import com.ianctchinese.llm.ModelJobResult;
import com.ianctchinese.model.ModelJob;
import com.ianctchinese.model.ModelJob.JobStatus;
import com.ianctchinese.model.ModelJob.JobType;
import com.ianctchinese.repository.ModelJobRepository;
import com.ianctchinese.service.ModelJobService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelJobServiceImpl implements ModelJobService {

  private final ModelJobRepository modelJobRepository;
  private final LargeLanguageModelClient largeLanguageModelClient;

  @Override
  @Transactional
  public ModelJob enqueueJob(ModelJobRequest request) {
    ModelJob job = ModelJob.builder()
        .textId(request.getTextId())
        .jobType(request.getJobType())
        .status(JobStatus.PENDING)
        .payload(request.getPayload())
        .createdAt(LocalDateTime.now())
        .build();
    ModelJob saved = modelJobRepository.save(job);
    try {
      ModelJobResult result = largeLanguageModelClient.run(saved);
      if ("SUCCESS".equalsIgnoreCase(result.getStatus())) {
        saved.setStatus(JobStatus.SUCCEEDED);
        saved.setResultData(result.getResultPayload());
      } else {
        saved.setStatus(JobStatus.FAILED);
        saved.setResultData(result.getMessage());
      }
    } catch (Exception ex) {
      log.error("LLM client failed to execute job {}", saved.getId(), ex);
      saved.setStatus(JobStatus.FAILED);
      saved.setResultData("LLM 调用失败：" + ex.getMessage());
    }
    saved.setCompletedAt(LocalDateTime.now());
    return modelJobRepository.save(saved);
  }

  @Override
  public List<ModelJob> listJobs(Long textId) {
    return modelJobRepository.findByTextId(textId);
  }

  @Override
  public List<ModelJob> listAllJobs() {
    return modelJobRepository.findAllByOrderByCreatedAtDesc();
  }

  @Override
  @Transactional
  public ModelJob recordJob(Long textId, JobType jobType, JobStatus status, String payload, String resultData) {
    ModelJob job = ModelJob.builder()
        .textId(textId)
        .jobType(jobType)
        .status(status)
        .payload(payload)
        .resultData(resultData)
        .createdAt(LocalDateTime.now())
        .completedAt(LocalDateTime.now())
        .build();
    return modelJobRepository.save(job);
  }
}
