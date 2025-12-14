import apiClient from "./client";

export const fetchEntities = (textId) => {
  return apiClient.get("/annotations/entities", { params: { textId } });
};

export const fetchRelations = (textId) => {
  return apiClient.get("/annotations/relations", { params: { textId } });
};

export const createEntity = (payload) => {
  return apiClient.post("/annotations/entities", payload);
};

export const createRelation = (payload) => {
  return apiClient.post("/annotations/relations", payload);
};

export const deleteEntity = (id) => {
  return apiClient.delete(`/annotations/entities/${id}`);
};

export const deleteRelation = (id) => {
  return apiClient.delete(`/annotations/relations/${id}`);
};
