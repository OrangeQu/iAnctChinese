import api from "./client";

export const listJobs = () => api.get("/model-jobs");
export const retryJob = (id) => api.post(`/model-jobs/${id}/retry`);
