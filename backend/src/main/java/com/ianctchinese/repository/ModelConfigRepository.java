package com.ianctchinese.repository;

import com.ianctchinese.model.ModelConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

  List<ModelConfig> findByEnabledTrueOrderBySortOrder();

  Optional<ModelConfig> findByModelKey(String modelKey);
}
