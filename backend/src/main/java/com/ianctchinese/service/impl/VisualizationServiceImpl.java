package com.ianctchinese.service.impl;

import com.ianctchinese.dto.PresetRequest;
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

  @Override
  @Transactional
  public VisualizationPreset createPreset(PresetRequest request) {
    VisualizationPreset preset = VisualizationPreset.builder()
        .label(request.getLabel())
        .textCategory(request.getTextCategory())
        .configJson(request.getConfigJson())
        .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
        .build();
    VisualizationPreset saved = visualizationPresetRepository.save(preset);
    applyDefaultRule(saved);
    return saved;
  }

  @Override
  @Transactional
  public VisualizationPreset updatePreset(Long id, PresetRequest request) {
    VisualizationPreset existing = visualizationPresetRepository.findById(id).orElse(null);
    if (existing == null) {
      return null;
    }
    if (request.getLabel() != null) {
      existing.setLabel(request.getLabel());
    }
    if (request.getTextCategory() != null) {
      existing.setTextCategory(request.getTextCategory());
    }
    if (request.getConfigJson() != null) {
      existing.setConfigJson(request.getConfigJson());
    }
    if (request.getIsDefault() != null) {
      existing.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
    }
    VisualizationPreset saved = visualizationPresetRepository.save(existing);
    applyDefaultRule(saved);
    return saved;
  }

  @Override
  @Transactional
  public void deletePreset(Long id) {
    visualizationPresetRepository.deleteById(id);
  }

  private void applyDefaultRule(VisualizationPreset preset) {
    if (Boolean.TRUE.equals(preset.getIsDefault()) && preset.getTextCategory() != null) {
      visualizationPresetRepository.clearDefaultByCategory(preset.getTextCategory(), preset.getId());
    }
  }
}
