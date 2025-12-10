package com.ianctchinese.controller;

import com.ianctchinese.dto.ProjectCreateRequest;
import com.ianctchinese.dto.ProjectMemberRequest;
import com.ianctchinese.dto.ProjectResponse;
import com.ianctchinese.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

  private final ProjectService projectService;

  @GetMapping("/mine")
  public ResponseEntity<List<ProjectResponse>> listMine(Authentication auth) {
    String username = auth.getName();
    return ResponseEntity.ok(projectService.listMyProjects(username));
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
}
