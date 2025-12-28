import http from "@/services/http";

export interface ProjectMemberInfo {
  userId: number;
  username: string;
  email?: string;
  role?: string;
}

export interface ProjectResponse {
  id: number;
  name: string;
  description?: string;
  ownerId?: number;
  ownerName?: string;
  createdAt?: string;
  updatedAt?: string;
  deleted?: boolean;
  members?: ProjectMemberInfo[];
}

export interface ProjectCreateRequest {
  name: string;
  description?: string;
}

export interface ProjectStatsResponse {
  textCount: number;
  sectionCount: number;
  entityCount: number;
  relationCount: number;
  markerCount: number;
  hiddenCount: number;
  jobCount: number;
}

export const listProjects = async (params?: { query?: string; deleted?: boolean }) => {
  const { data } = await http.get<ProjectResponse[]>("/projects/mine", {
    params,
  });
  return data;
};

export const createProject = async (payload: ProjectCreateRequest) => {
  const { data } = await http.post<ProjectResponse>("/projects", payload);
  return data;
};

export const getProject = async (id: number) => {
  const { data } = await http.get<ProjectResponse>(`/projects/${id}`);
  return data;
};

export const deleteProject = async (id: number) => {
  await http.delete(`/projects/${id}`);
};

export const restoreProject = async (id: number) => {
  const { data } = await http.post<ProjectResponse>(`/projects/${id}/restore`);
  return data;
};

export const addMember = async (projectId: number, username: string) => {
  const { data } = await http.post<ProjectResponse>(
    `/projects/${projectId}/members`,
    { username }
  );
  return data;
};

export const removeMember = async (projectId: number, username: string) => {
  const { data } = await http.delete<ProjectResponse>(
    `/projects/${projectId}/members`,
    { data: { username } }
  );
  return data;
};

export const updateMemberRole = async (
  projectId: number,
  username: string,
  role: string
) => {
  const { data } = await http.put<ProjectResponse>(`/projects/${projectId}/members/role`, {
    username,
    role,
  });
  return data;
};

export const getProjectStats = async (projectId: number) => {
  const { data } = await http.get<ProjectStatsResponse>(`/projects/${projectId}/stats`);
  return data;
};
