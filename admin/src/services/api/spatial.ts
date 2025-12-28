import http from "@/services/http";

export interface SpatialData {
  id: number;
  entityId?: number;
  entityName?: string;
  mapId?: number;
  x?: number;
  y?: number;
  year?: number;
  description?: string;
}

export interface SpatialDataRequest {
  entityId?: number;
  entityName?: string;
  mapId?: number;
  x?: number;
  y?: number;
  year?: number;
  description?: string;
}

export const listSpatial = async (params: { mapId?: number; year?: number }) => {
  const { data } = await http.get<SpatialData[]>("/spatial", { params });
  return data;
};

export const createSpatial = async (payload: SpatialDataRequest) => {
  const { data } = await http.post<SpatialData>("/spatial", payload);
  return data;
};

export const updateSpatial = async (id: number, payload: SpatialDataRequest) => {
  const { data } = await http.put<SpatialData>(`/spatial/${id}`, payload);
  return data;
};

export const deleteSpatial = async (id: number) => {
  await http.delete(`/spatial/${id}`);
};
