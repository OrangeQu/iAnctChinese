package com.ianctchinese.service;

import com.ianctchinese.dto.ProjectCreateRequest;
import com.ianctchinese.dto.ProjectMemberRequest;
import com.ianctchinese.dto.ProjectResponse;
import java.util.List;

public interface ProjectService {

  ProjectResponse createProject(String ownerUsername, ProjectCreateRequest request);

  List<ProjectResponse> listMyProjects(String username);

  ProjectResponse getProject(Long projectId, String username);

  void deleteProject(Long projectId, String username);

  ProjectResponse addMember(Long projectId, String ownerUsername, ProjectMemberRequest request);

  ProjectResponse removeMember(Long projectId, String ownerUsername, String targetUsername);
}
