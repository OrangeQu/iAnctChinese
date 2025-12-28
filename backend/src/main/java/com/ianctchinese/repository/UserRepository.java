package com.ianctchinese.repository;

import com.ianctchinese.model.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  @Query("""
      select u from User u
      where (:enabled is null or u.enabled = :enabled)
        and (:query is null or lower(u.username) like lower(concat('%', :query, '%'))
          or lower(u.email) like lower(concat('%', :query, '%')))
      """)
  Page<User> searchUsers(
      @Param("query") String query,
      @Param("enabled") Boolean enabled,
      Pageable pageable);
}
