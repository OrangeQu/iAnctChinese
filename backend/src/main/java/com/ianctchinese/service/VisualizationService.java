package com.ianctchinese.service;

import com.ianctchinese.dto.VisualizationRequest;
import com.ianctchinese.dto.PresetRequest;
import com.ianctchinese.model.VisualizationPreset;
import java.util.List;

public interface VisualizationService {

  VisualizationPreset savePreset(VisualizationRequest request);

  List<VisualizationPreset> listPresets(String category);

  VisualizationPreset createPreset(PresetRequest request);

  VisualizationPreset updatePreset(Long id, PresetRequest request);

  void deletePreset(Long id);
}
