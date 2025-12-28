import http from "@/services/http";

export interface TextDocument {
  id: number;
  title: string;
  content: string;
  description?: string;
  projectId?: number;
  category?: string;
  author?: string;
  era?: string;
  createdAt?: string;
  updatedAt?: string;
  isDeleted?: boolean;
}

export interface TextCreateRequest {
  title: string;
  content?: string;
  description?: string;
  projectId?: number;
  category?: string;
  author?: string;
  era?: string;
}

export interface TextUpdateRequest {
  title: string;
  content: string;
  category?: string;
  author?: string;
  era?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface TextListParams {
  projectId?: number;
  category?: string;
  era?: string;
  author?: string;
  deleted?: boolean;
  page?: number;
  size?: number;
}

export const listTextsPage = async (params?: TextListParams) => {
  const { data } = await http.get<Page<TextDocument>>("/texts", { params });
  return data;
};

export const listTexts = async (params?: TextListParams) => {
  const data = await listTextsPage(params);
  return data.content || [];
};

export const getText = async (id: number) => {
  const { data } = await http.get<TextDocument>(`/texts/${id}`);
  return data;
};

export const createText = async (payload: TextCreateRequest) => {
  const { data } = await http.post<TextDocument>("/texts", payload);
  return data;
};

export const updateText = async (id: number, payload: TextUpdateRequest) => {
  const { data } = await http.patch<TextDocument>(`/texts/${id}`, payload);
  return data;
};

export const deleteText = async (id: number) => {
  await http.delete(`/texts/${id}`);
};

export const exportText = async (id: number) => {
  const response = await http.get(`/texts/${id}/export`, {
    responseType: "blob",
  });
  return response.data as Blob;
};
