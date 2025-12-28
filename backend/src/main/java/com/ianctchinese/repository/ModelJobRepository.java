package com.ianctchinese.repository;

import com.ianctchinese.model.ModelJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelJobRepository extends JpaRepository<ModelJob, Long> {

  List<ModelJob> findByTextId(Long textId);

  void deleteByTextId(Long textId);

  long countByTextIdIn(List<Long> textIds);
}
