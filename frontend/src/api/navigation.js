import apiClient from "./client";

export const fetchNavigationTree = (projectId) => {
  return apiClient.get("/navigation/tree", { params: { projectId } });
};
