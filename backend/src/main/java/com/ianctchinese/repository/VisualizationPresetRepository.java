package com.ianctchinese.repository;

import com.ianctchinese.model.VisualizationPreset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VisualizationPresetRepository extends JpaRepository<VisualizationPreset, Long> {

  List<VisualizationPreset> findByTextCategory(String textCategory);

  @Modifying
  @Transactional
  @Query("update VisualizationPreset v set v.isDefault = false where v.textCategory = :category and v.id <> :id")
  void clearDefaultByCategory(@Param("category") String category, @Param("id") Long id);
}
