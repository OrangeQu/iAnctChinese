package com.ianctchinese.controller;

import com.ianctchinese.dto.ModelJobRequest;
import com.ianctchinese.model.ModelJob;
import com.ianctchinese.service.ModelJobService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-jobs")
@RequiredArgsConstructor
public class ModelJobController {

  private final ModelJobService modelJobService;

  @PostMapping
  public ResponseEntity<ModelJob> enqueueJob(@Valid @RequestBody ModelJobRequest request) {
    return ResponseEntity.ok(modelJobService.enqueueJob(request));
  }

  @GetMapping
  public ResponseEntity<List<ModelJob>> listJobs(@RequestParam(value = "textId", required = false) Long textId) {
    if (textId != null) {
      return ResponseEntity.ok(modelJobService.listJobs(textId));
    }
    return ResponseEntity.ok(modelJobService.listAllJobs());
  }
}
