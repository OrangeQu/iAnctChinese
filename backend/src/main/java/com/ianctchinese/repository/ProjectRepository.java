package com.ianctchinese.repository;

import com.ianctchinese.model.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

  List<Project> findByOwnerIdAndDeletedFalse(Long ownerId);

  List<Project> findByDeletedFalse();

  Optional<Project> findByIdAndDeletedFalse(Long id);
}
