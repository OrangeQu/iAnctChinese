import apiClient from "./client";

export const fetchNavigationTree = () => {
  return apiClient.get("/navigation/tree");
};
