package com.ianctchinese.service;

import com.ianctchinese.dto.VisualizationRequest;
import com.ianctchinese.model.VisualizationPreset;
import java.util.List;

public interface VisualizationService {

  VisualizationPreset savePreset(VisualizationRequest request);

  List<VisualizationPreset> listPresets(String category);
}
