import apiClient from "./client";

export const locateEntities = (payload) => apiClient.post("/geo/locate", payload);
