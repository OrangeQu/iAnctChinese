import apiClient from "./client";

export const searchTexts = (keyword) => {
  return apiClient.get("/texts/search", { params: { keyword } });
};
