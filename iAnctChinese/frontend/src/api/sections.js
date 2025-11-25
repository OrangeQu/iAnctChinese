import apiClient from "./client";

export const fetchSections = (textId) => {
  return apiClient.get(`/texts/${textId}/sections`);
};

export const autoSegment = (textId) => {
  return apiClient.post(`/texts/${textId}/sections/auto`);
};

export const updateSection = (sectionId, payload) => {
  return apiClient.patch(`/sections/${sectionId}`, payload);
};

export const createSection = (textId, payload) => {
  return apiClient.post(`/texts/${textId}/sections`, payload);
};
