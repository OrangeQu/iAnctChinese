import apiClient from "./client";

export const classifyText = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/classify`, null, {
    params: model ? { model } : {}
  });
};

export const autoAnnotate = (textId) => {
  return apiClient.post(`/analysis/${textId}/auto-annotate`);
};

export const fetchInsights = (textId) => {
  return apiClient.get(`/analysis/${textId}/insights`);
};

export const runFullAnalysis = (textId, model) => {
  return apiClient.post(`/analysis/${textId}/full`, null, {
    params: model ? { model } : {}
  });
};
