import apiClient from "./client";

export const classifyText = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/classify`, null, {
    params: model ? { model } : {}
  });
};

export const autoAnnotate = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/auto-annotate`, null, {
    params: model ? { model } : {}
  });
};

export const fetchInsights = (textId, params = {}) => {
  return apiClient.get(`/analysis/${textId}/insights`, { params });
};

export const fetchWordCloud = (textId, model) => {
  return apiClient.get(`/analysis/${textId}/word-cloud`, {
    params: model ? { model } : {}
  });
};

export const runFullAnalysis = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/full`, null, {
    params: model ? { model } : {}
  });
};

export const extractRelations = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/relations`, null, {
    params: model ? { model } : {}
  });
};

export const segmentText = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/segments`, null, {
    params: model ? { model } : {}
  });
};
