import apiClient from "./client";

export const projectApi = {
  listMine() {
    return apiClient.get("/projects/mine");
  },
  create(data) {
    return apiClient.post("/projects", data);
  },
  delete(id) {
    return apiClient.delete(`/projects/${id}`);
  },
  getDetails(id) {
    return apiClient.get(`/projects/${id}`);
  },
  addMember(id, data) {
    return apiClient.post(`/projects/${id}/members`, data);
  },
  removeMember(id, data) {
    return apiClient.delete(`/projects/${id}/members`, { data });
  }
};
