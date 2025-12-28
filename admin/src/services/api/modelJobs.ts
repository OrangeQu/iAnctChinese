import http from "@/services/http";

export type JobType =
  | "TYPE_CLASSIFICATION"
  | "ENTITY_EXTRACTION"
  | "RELATION_EXTRACTION"
  | "PUNCTUATION"
  | "SUMMARY";

export interface ModelJob {
  id: number;
  textId: number;
  jobType: JobType;
  status: string;
  createdAt?: string;
  completedAt?: string;
}

export interface ModelJobRequest {
  textId: number;
  jobType: JobType;
  payload?: string;
}

export const listJobs = async (textId: number) => {
  const { data } = await http.get<ModelJob[]>(`/model-jobs?textId=${textId}`);
  return data;
};

export const enqueueJob = async (payload: ModelJobRequest) => {
  const { data } = await http.post<ModelJob>("/model-jobs", payload);
  return data;
};
