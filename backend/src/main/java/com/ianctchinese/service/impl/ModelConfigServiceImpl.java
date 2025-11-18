package com.ianctchinese.service.impl;

import com.ianctchinese.model.ModelConfig;
import com.ianctchinese.repository.ModelConfigRepository;
import com.ianctchinese.service.ModelConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

  private final ModelConfigRepository modelConfigRepository;

  @Override
  public List<ModelConfig> getAllEnabledModels() {
    return modelConfigRepository.findByEnabledTrueOrderBySortOrder();
  }

  @Override
  public List<ModelConfig> getAllModels() {
    return modelConfigRepository.findAll();
  }

  @Override
  @Transactional
  public ModelConfig createModel(ModelConfig modelConfig) {
    return modelConfigRepository.save(modelConfig);
  }

  @Override
  @Transactional
  public ModelConfig updateModel(Long id, ModelConfig modelConfig) {
    ModelConfig existing = modelConfigRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Model not found: " + id));
    existing.setModelKey(modelConfig.getModelKey());
    existing.setDisplayName(modelConfig.getDisplayName());
    existing.setProvider(modelConfig.getProvider());
    existing.setEnabled(modelConfig.getEnabled());
    existing.setSortOrder(modelConfig.getSortOrder());
    existing.setDescription(modelConfig.getDescription());
    return modelConfigRepository.save(existing);
  }

  @Override
  @Transactional
  public void deleteModel(Long id) {
    modelConfigRepository.deleteById(id);
  }

  @Override
  public ModelConfig getModelByKey(String modelKey) {
    return modelConfigRepository.findByModelKey(modelKey)
        .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelKey));
  }
}
