package com.ianctchinese.repository;

import com.ianctchinese.model.VisualizationPreset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisualizationPresetRepository extends JpaRepository<VisualizationPreset, Long> {

  List<VisualizationPreset> findByTextCategory(String textCategory);
}
