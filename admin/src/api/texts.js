import api from "./client";

export const fetchTexts = (category) =>
  api.get("/texts", { params: { category } });

export const fetchText = (id) => api.get(`/texts/${id}`);

export const updateTextCategory = (id, category) =>
  api.patch(`/texts/${id}/category`, { category });

export const deleteText = (id) => api.delete(`/texts/${id}`);

export const exportText = (id) =>
  api.get(`/texts/${id}/export`, { responseType: "blob" });
