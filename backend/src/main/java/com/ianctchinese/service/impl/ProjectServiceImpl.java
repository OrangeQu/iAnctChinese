package com.ianctchinese.service.impl;

import com.ianctchinese.dto.ProjectCreateRequest;
import com.ianctchinese.dto.ProjectMemberRequest;
import com.ianctchinese.dto.ProjectResponse;
import com.ianctchinese.dto.ProjectStatsResponse;
import com.ianctchinese.model.Project;
import com.ianctchinese.model.ProjectMember;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.model.User;
import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.GeoMarkerRepository;
import com.ianctchinese.repository.HiddenGeoMarkerRepository;
import com.ianctchinese.repository.ModelJobRepository;
import com.ianctchinese.repository.ProjectMemberRepository;
import com.ianctchinese.repository.ProjectRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import com.ianctchinese.repository.TextSectionRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.UserRepository;
import com.ianctchinese.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Objects;
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
  private final TextSectionRepository textSectionRepository;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;
  private final GeoMarkerRepository geoMarkerRepository;
  private final HiddenGeoMarkerRepository hiddenGeoMarkerRepository;
  private final ModelJobRepository modelJobRepository;

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

    return toResponse(
        project,
        owner,
        owner != null ? List.of(buildMemberInfo(owner, "OWNER")) : List.of()
    );
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProjectResponse> listMyProjects(String username, String query, Boolean deleted) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    List<ProjectMember> memberships = projectMemberRepository.findByUserId(user.getId());
    List<Long> projectIds = memberships.stream().map(ProjectMember::getProjectId).toList();
    if (projectIds.isEmpty()) {
      return List.of();
    }
    Boolean effectiveDeleted = deleted;
    if (effectiveDeleted == null) {
      effectiveDeleted = false;
    }
    List<Project> projects = projectRepository.findByIdsWithFilters(
        projectIds,
        query != null && !query.isBlank() ? query.trim() : null,
        effectiveDeleted
    );

    // 收集需要的所有用户：项目所有者 + 当前用户所在项目的成员
    List<Long> ownerIds = projects.stream().map(Project::getOwnerId).toList();
    List<Long> memberUserIds = projectMemberRepository.findByProjectIdIn(projectIds).stream()
        .map(ProjectMember::getUserId)
        .toList();
    List<Long> userIds = new java.util.ArrayList<>();
    userIds.addAll(ownerIds);
    userIds.addAll(memberUserIds);
    Map<Long, User> users = userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, u -> u));

    return projects.stream()
        .map(p -> toResponse(p, users.get(p.getOwnerId()), projectMembers(p, users)))
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
  public ProjectResponse restoreProject(Long projectId, String username) {
    User owner = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    if (!project.getOwnerId().equals(owner.getId())) {
      throw new IllegalArgumentException("只有组长可以恢复项目");
    }
    project.setDeleted(false);
    projectRepository.save(project);
    textDocumentRepository.findByProjectId(projectId).forEach(doc -> {
      doc.setIsDeleted(false);
      textDocumentRepository.save(doc);
    });
    Map<Long, User> users = usersForProject(projectId, project.getOwnerId());
    return toResponse(project, users.get(project.getOwnerId()), projectMembers(project, users));
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
  public ProjectResponse updateMemberRole(Long projectId, String ownerUsername, String targetUsername, String role) {
    User owner = userRepository.findByUsername(ownerUsername)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    if (!project.getOwnerId().equals(owner.getId())) {
      throw new IllegalArgumentException("只有组长可以修改角色");
    }
    User target = userRepository.findByUsername(targetUsername)
        .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));
    ProjectMember membership = projectMemberRepository
        .findByProjectIdAndUserId(projectId, target.getId())
        .orElseThrow(() -> new IllegalArgumentException("用户不在项目中"));
    ProjectMember.Role nextRole;
    try {
      nextRole = ProjectMember.Role.valueOf(role.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("角色无效");
    }
    if (project.getOwnerId().equals(target.getId()) && nextRole == ProjectMember.Role.MEMBER) {
      throw new IllegalArgumentException("不能降级当前组长");
    }
    membership.setRole(nextRole);
    projectMemberRepository.save(membership);
    if (nextRole == ProjectMember.Role.OWNER) {
      project.setOwnerId(target.getId());
      projectRepository.save(project);
    }
    Map<Long, User> users = usersForProject(projectId, project.getOwnerId());
    return toResponse(project, users.get(project.getOwnerId()), projectMembers(project, users));
  }

  @Override
  @Transactional(readOnly = true)
  public ProjectStatsResponse getProjectStats(Long projectId, String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    requireMember(projectId, user.getId());
    List<Long> textIds = textDocumentRepository.findByProjectId(projectId).stream()
        .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
        .map(TextDocument::getId)
        .toList();
    if (textIds.isEmpty()) {
      return ProjectStatsResponse.builder()
          .textCount(0)
          .sectionCount(0)
          .entityCount(0)
          .relationCount(0)
          .markerCount(0)
          .hiddenCount(0)
          .jobCount(0)
          .build();
    }
    return ProjectStatsResponse.builder()
        .textCount(textIds.size())
        .sectionCount(textSectionRepository.countByTextDocumentIdIn(textIds))
        .entityCount(entityAnnotationRepository.countByTextDocumentIdIn(textIds))
        .relationCount(relationAnnotationRepository.countByTextDocumentIdIn(textIds))
        .markerCount(geoMarkerRepository.countByTextIdIn(textIds))
        .hiddenCount(hiddenGeoMarkerRepository.countByTextIdIn(textIds))
        .jobCount(modelJobRepository.countByTextIdIn(textIds))
        .build();
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

  @Override
  @Transactional
  public ProjectResponse leaveProject(Long projectId, String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    Project project = projectRepository.findByIdAndDeletedFalse(projectId)
        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));

    // 检查用户是否是项目成员
    ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId())
        .orElseThrow(() -> new IllegalArgumentException("您不是该项目的成员"));

    // 组长不能退出自己的项目
    if (project.getOwnerId().equals(user.getId())) {
      throw new IllegalArgumentException("组长不能退出项目，请先转让或删除项目");
    }

    // 删除成员关系
    projectMemberRepository.delete(membership);

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
          // 如果用户不存在（被删除或缓存缺失），跳过以避免 NPE
          if (u == null) {
            return null;
          }
          return buildMemberInfo(u, pm.getRole().name());
        })
        .filter(Objects::nonNull)
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
        .deleted(p.getDeleted())
        .members(members)
        .build();
  }

  private ProjectResponse.ProjectMemberInfo buildMemberInfo(User user, String role) {
    if (user == null) {
      return null;
    }
    return ProjectResponse.ProjectMemberInfo.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .role(role)
        .build();
  }
}
