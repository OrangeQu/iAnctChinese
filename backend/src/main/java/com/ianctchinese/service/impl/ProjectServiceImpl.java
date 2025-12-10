package com.ianctchinese.service.impl;

import com.ianctchinese.dto.ProjectCreateRequest;
import com.ianctchinese.dto.ProjectMemberRequest;
import com.ianctchinese.dto.ProjectResponse;
import com.ianctchinese.model.Project;
import com.ianctchinese.model.ProjectMember;
import com.ianctchinese.model.User;
import com.ianctchinese.repository.ProjectMemberRepository;
import com.ianctchinese.repository.ProjectRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.UserRepository;
import com.ianctchinese.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final UserRepository userRepository;
  private final TextDocumentRepository textDocumentRepository;

  @Override
  @Transactional
  public ProjectResponse createProject(String ownerUsername, ProjectCreateRequest request) {
    User owner = userRepository.findByUsername(ownerUsername)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    LocalDateTime now = LocalDateTime.now();
    Project project = Project.builder()
        .name(request.getName())
        .description(request.getDescription())
        .ownerId(owner.getId())
        .createdAt(now)
        .updatedAt(now)
        .deleted(false)
        .build();
    projectRepository.save(project);

    projectMemberRepository.save(ProjectMember.builder()
        .projectId(project.getId())
        .userId(owner.getId())
        .role(ProjectMember.Role.OWNER)
        .createdAt(now)
        .build());

    return toResponse(project, owner, List.of(buildMemberInfo(owner, "OWNER")));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProjectResponse> listMyProjects(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    List<ProjectMember> memberships = projectMemberRepository.findByUserId(user.getId());
    List<Long> projectIds = memberships.stream().map(ProjectMember::getProjectId).toList();
    Map<Long, ProjectMember.Role> roleMap = memberships.stream()
        .collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getRole));

    List<Project> projects = projectRepository.findAll().stream()
        .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
        .filter(p -> projectIds.contains(p.getId()))
        .toList();

    Map<Long, User> owners = userRepository.findAllById(
        projects.stream().map(Project::getOwnerId).toList()
    ).stream().collect(Collectors.toMap(User::getId, u -> u));

    return projects.stream()
        .map(p -> toResponse(p, owners.get(p.getOwnerId()), projectMembers(p, owners)))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ProjectResponse getProject(Long projectId, String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findByIdAndDeletedFalse(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    requireMember(projectId, user.getId());
    Map<Long, User> users = usersForProject(projectId, project.getOwnerId());
    return toResponse(project, users.get(project.getOwnerId()), projectMembers(project, users));
  }

  @Override
  @Transactional
  public void deleteProject(Long projectId, String username) {
    User owner = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findByIdAndDeletedFalse(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    if (!project.getOwnerId().equals(owner.getId())) {
      throw new IllegalArgumentException("只有组长可以删除项目");
    }
    project.setDeleted(true);
    projectRepository.save(project);
    // 文档软删（复用 is_deleted 字段）
    textDocumentRepository.findByProjectId(projectId).forEach(doc -> {
      doc.setIsDeleted(true);
      textDocumentRepository.save(doc);
    });
  }

  @Override
  @Transactional
  public ProjectResponse addMember(Long projectId, String ownerUsername, ProjectMemberRequest request) {
    User owner = userRepository.findByUsername(ownerUsername)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findByIdAndDeletedFalse(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    if (!project.getOwnerId().equals(owner.getId())) {
      throw new IllegalArgumentException("只有组长可以添加成员");
    }
    User target = userRepository.findByUsername(request.getUsername())
        .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));
    if (projectMemberRepository.existsByProjectIdAndUserId(projectId, target.getId())) {
      throw new IllegalArgumentException("用户已在项目中");
    }
    projectMemberRepository.save(ProjectMember.builder()
        .projectId(projectId)
        .userId(target.getId())
        .role(ProjectMember.Role.MEMBER)
        .createdAt(LocalDateTime.now())
        .build());
    Map<Long, User> users = usersForProject(projectId, project.getOwnerId());
    return toResponse(project, users.get(project.getOwnerId()), projectMembers(project, users));
  }

  @Override
  @Transactional
  public ProjectResponse removeMember(Long projectId, String ownerUsername, String targetUsername) {
    User owner = userRepository.findByUsername(ownerUsername)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findByIdAndDeletedFalse(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    if (!project.getOwnerId().equals(owner.getId())) {
      throw new IllegalArgumentException("只有组长可以移除成员");
    }
    User target = userRepository.findByUsername(targetUsername)
        .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));
    Optional<ProjectMember> membership = projectMemberRepository.findByProjectIdAndUserId(projectId, target.getId());
    membership.ifPresent(projectMemberRepository::delete);
    Map<Long, User> users = usersForProject(projectId, project.getOwnerId());
    return toResponse(project, users.get(project.getOwnerId()), projectMembers(project, users));
  }

  private void requireMember(Long projectId, Long userId) {
    if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
      throw new IllegalArgumentException("无权访问此项目");
    }
  }

  private Map<Long, User> usersForProject(Long projectId, Long ownerId) {
    List<Long> userIds = projectMemberRepository.findByProjectId(projectId).stream()
        .map(ProjectMember::getUserId)
        .distinct()
        .collect(Collectors.toList());
    if (!userIds.contains(ownerId)) {
      userIds.add(ownerId);
    }
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, u -> u));
  }

  private List<ProjectResponse.ProjectMemberInfo> projectMembers(Project project, Map<Long, User> users) {
    return projectMemberRepository.findByProjectId(project.getId()).stream()
        .map(pm -> {
          User u = users.get(pm.getUserId());
          return buildMemberInfo(u, pm.getRole().name());
        })
        .toList();
  }

  private ProjectResponse toResponse(Project p, User owner, List<ProjectResponse.ProjectMemberInfo> members) {
    return ProjectResponse.builder()
        .id(p.getId())
        .name(p.getName())
        .description(p.getDescription())
        .ownerId(p.getOwnerId())
        .ownerName(owner != null ? owner.getUsername() : null)
        .createdAt(p.getCreatedAt())
        .updatedAt(p.getUpdatedAt())
        .members(members)
        .build();
  }

  private ProjectResponse.ProjectMemberInfo buildMemberInfo(User user, String role) {
    return ProjectResponse.ProjectMemberInfo.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .role(role)
        .build();
  }
}
