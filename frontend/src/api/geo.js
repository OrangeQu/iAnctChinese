import apiClient from "./client";

export const locateEntities = (payload) => apiClient.post("/geo/locate", payload);

export const saveMarkerPosition = (payload) => apiClient.post("/geo/marker", payload);

export const getMarkerPositions = (textId) => apiClient.get(`/geo/markers/${textId}`);

export const deleteMarkerById = (markerId) => apiClient.delete(`/geo/markers/${markerId}`);

export const deleteMarkerForEntity = (textId, entityId) =>
  apiClient.delete(`/geo/marker/${textId}/${entityId}`);

export const hideMarker = (payload) => apiClient.post("/geo/marker/hide", payload);

export const getHiddenMarkers = (textId) => apiClient.get(`/geo/hidden/${textId}`);
