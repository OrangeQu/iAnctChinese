package com.ianctchinese.controller;

import com.ianctchinese.dto.ProjectCreateRequest;
import com.ianctchinese.dto.ProjectMemberRequest;
import com.ianctchinese.dto.ProjectMemberRoleRequest;
import com.ianctchinese.dto.ProjectResponse;
import com.ianctchinese.dto.ProjectStatsResponse;
import com.ianctchinese.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

  private final ProjectService projectService;

  @GetMapping("/mine")
  public ResponseEntity<List<ProjectResponse>> listMine(
      Authentication auth,
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "deleted", required = false) Boolean deleted) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.listMyProjects(username, query, deleted));
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> create(Authentication auth,
      @Valid @RequestBody ProjectCreateRequest request) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.createProject(username, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProjectResponse> get(Authentication auth, @PathVariable Long id) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.getProject(id, username));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
    String username = auth.getName();
    projectService.deleteProject(id, username);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/restore")
  public ResponseEntity<?> restore(Authentication auth, @PathVariable Long id) {
    String username = auth.getName();
    try {
      return ResponseEntity.ok(projectService.restoreProject(id, username));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PostMapping("/{id}/members")
  public ResponseEntity<ProjectResponse> addMember(
      Authentication auth,
      @PathVariable Long id,
      @Valid @RequestBody ProjectMemberRequest request) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.addMember(id, username, request));
  }

  @DeleteMapping("/{id}/members")
  public ResponseEntity<ProjectResponse> removeMember(
      Authentication auth,
      @PathVariable Long id,
      @RequestBody ProjectMemberRequest request) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.removeMember(id, username, request.getUsername()));
  }

  @PutMapping("/{id}/members/role")
  public ResponseEntity<?> updateMemberRole(
      Authentication auth,
      @PathVariable Long id,
      @Valid @RequestBody ProjectMemberRoleRequest request) {
    String username = auth.getName();
    try {
      return ResponseEntity.ok(
          projectService.updateMemberRole(id, username, request.getUsername(), request.getRole())
      );
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @GetMapping("/{id}/stats")
  public ResponseEntity<ProjectStatsResponse> stats(Authentication auth, @PathVariable Long id) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.getProjectStats(id, username));
  }

  @PostMapping("/{id}/leave")
  public ResponseEntity<ProjectResponse> leaveProject(
      Authentication auth,
      @PathVariable Long id) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.leaveProject(id, username));
  }
}
