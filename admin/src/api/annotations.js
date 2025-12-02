import api from "./client";

export const fetchEntities = (textId) =>
  api.get(`/annotations/entities`, { params: { textId } });

export const fetchRelations = (textId) =>
  api.get(`/annotations/relations`, { params: { textId } });

export const deleteEntity = (id) => api.delete(`/annotations/entities/${id}`);

export const deleteRelation = (id) => api.delete(`/annotations/relations/${id}`);
