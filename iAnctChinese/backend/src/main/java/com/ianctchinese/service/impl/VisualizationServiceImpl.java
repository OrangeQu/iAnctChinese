package com.ianctchinese.service.impl;

import com.ianctchinese.dto.VisualizationRequest;
import com.ianctchinese.model.VisualizationPreset;
import com.ianctchinese.repository.VisualizationPresetRepository;
import com.ianctchinese.service.VisualizationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VisualizationServiceImpl implements VisualizationService {

  private final VisualizationPresetRepository visualizationPresetRepository;

  @Override
  @Transactional
  public VisualizationPreset savePreset(VisualizationRequest request) {
    VisualizationPreset preset = VisualizationPreset.builder()
        .label(request.getViewType())
        .textCategory(request.getTextCategory())
        .configJson(request.getConfigJson())
        .isDefault(false)
        .build();
    return visualizationPresetRepository.save(preset);
  }

  @Override
  public List<VisualizationPreset> listPresets(String category) {
    if (category == null || category.isBlank()) {
      return visualizationPresetRepository.findAll();
    }
    return visualizationPresetRepository.findByTextCategory(category);
  }
}
