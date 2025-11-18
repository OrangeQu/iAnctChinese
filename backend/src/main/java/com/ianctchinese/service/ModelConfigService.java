package com.ianctchinese.service;

import com.ianctchinese.model.ModelConfig;
import java.util.List;

public interface ModelConfigService {

  List<ModelConfig> getAllEnabledModels();

  List<ModelConfig> getAllModels();

  ModelConfig createModel(ModelConfig modelConfig);

  ModelConfig updateModel(Long id, ModelConfig modelConfig);

  void deleteModel(Long id);

  ModelConfig getModelByKey(String modelKey);
}
