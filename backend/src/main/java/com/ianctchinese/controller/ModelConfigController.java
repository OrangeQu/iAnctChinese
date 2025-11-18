package com.ianctchinese.controller;

import com.ianctchinese.model.ModelConfig;
import com.ianctchinese.service.ModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/models")
@Tag(name = "模型配置管理")
@RequiredArgsConstructor
public class ModelConfigController {

  private final ModelConfigService modelConfigService;

  @GetMapping
  @Operation(summary = "获取所有启用的模型配置")
  public ResponseEntity<List<ModelConfig>> getAllEnabledModels() {
    return ResponseEntity.ok(modelConfigService.getAllEnabledModels());
  }

  @GetMapping("/all")
  @Operation(summary = "获取所有模型配置（包括禁用的）")
  public ResponseEntity<List<ModelConfig>> getAllModels() {
    return ResponseEntity.ok(modelConfigService.getAllModels());
  }

  @PostMapping
  @Operation(summary = "创建新模型配置")
  public ResponseEntity<ModelConfig> createModel(@RequestBody ModelConfig modelConfig) {
    return ResponseEntity.ok(modelConfigService.createModel(modelConfig));
  }

  @PutMapping("/{id}")
  @Operation(summary = "更新模型配置")
  public ResponseEntity<ModelConfig> updateModel(
      @PathVariable Long id,
      @RequestBody ModelConfig modelConfig) {
    return ResponseEntity.ok(modelConfigService.updateModel(id, modelConfig));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "删除模型配置")
  public ResponseEntity<Void> deleteModel(@PathVariable Long id) {
    modelConfigService.deleteModel(id);
    return ResponseEntity.noContent().build();
  }
}
