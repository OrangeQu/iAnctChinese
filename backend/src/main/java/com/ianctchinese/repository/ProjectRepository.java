package com.ianctchinese.repository;

import com.ianctchinese.model.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

  List<Project> findByOwnerIdAndDeletedFalse(Long ownerId);

  List<Project> findByDeletedFalse();

  Optional<Project> findByIdAndDeletedFalse(Long id);

  @Query("""
      select p from Project p
      where p.id in :projectIds
        and (:deleted is null or p.deleted = :deleted)
        and (:query is null or lower(p.name) like lower(concat('%', :query, '%')))
      """)
  List<Project> findByIdsWithFilters(
      @Param("projectIds") List<Long> projectIds,
      @Param("query") String query,
      @Param("deleted") Boolean deleted);
}
