import apiClient from "./client";

export const locateEntities = (payload) => apiClient.post("/geo/locate", payload);

export const saveMarkerPosition = (payload) => apiClient.post("/geo/marker", payload);

export const getMarkerPositions = (textId) => apiClient.get(`/geo/markers/${textId}`);
