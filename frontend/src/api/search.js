import apiClient from "./client";

export const searchTexts = (keyword, projectId) => {
  return apiClient.get("/texts/search", { params: { keyword, projectId } });
};
