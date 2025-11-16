import apiClient from "./client";

export const fetchTexts = (category) => {
  return apiClient.get("/texts", {
    params: { category }
  });
};

export const fetchTextById = (id) => {
  return apiClient.get(`/texts/${id}`);
};

export const uploadText = (payload) => {
  return apiClient.post("/texts", payload);
};

export const updateTextCategory = (id, category) => {
  return apiClient.patch(`/texts/${id}/category`, { category });
};

export const exportText = (id) => {
  return apiClient.get(`/texts/${id}/export`, { responseType: "blob" });
};

export const deleteText = (id) => {
  return apiClient.delete(`/texts/${id}`);
};
