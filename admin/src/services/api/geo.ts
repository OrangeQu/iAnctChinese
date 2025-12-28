import http from "@/services/http";

export interface MapInfo {
  id: number;
  dynasty?: string;
  filename?: string;
  startYear?: number;
  endYear?: number;
  width?: number;
  height?: number;
}

export interface GeoMarker {
  id: number;
  textId?: number;
  entityId?: number;
  entityLabel?: string;
  category?: string;
  latitude?: number | null;
  longitude?: number | null;
  source?: string;
  updatedAt?: string;
}

export interface HiddenGeoMarker {
  id: number;
  textId: number;
  entityId?: number | null;
  entityLabel?: string | null;
  createdAt?: string;
}

export const listMaps = async () => {
  const { data } = await http.get<MapInfo[]>("/maps");
  return data;
};

export const createMap = async (payload: MapInfo) => {
  const { data } = await http.post<MapInfo>("/maps", payload);
  return data;
};

export const updateMap = async (id: number, payload: MapInfo) => {
  const { data } = await http.put<MapInfo>(`/maps/${id}`, payload);
  return data;
};

export const deleteMap = async (id: number) => {
  await http.delete(`/maps/${id}`);
};

export const listGeoMarkers = async (params: {
  textId?: number;
  category?: string;
  source?: string;
  missingCoords?: boolean;
  invalidCoords?: boolean;
}) => {
  const { data } = await http.get<GeoMarker[]>("/geo-markers", { params });
  return data;
};

export const updateGeoMarker = async (id: number, payload: { latitude?: number | null; longitude?: number | null }) => {
  const { data } = await http.put<GeoMarker>(`/geo-markers/${id}`, payload);
  return data;
};

export const deleteGeoMarker = async (id: number) => {
  await http.delete(`/geo-markers/${id}`);
};

export const listGeoHides = async (textId: number) => {
  const { data } = await http.get<HiddenGeoMarker[]>("/geo-hides", { params: { textId } });
  return data;
};

export const clearGeoHides = async (textId: number) => {
  await http.delete("/geo-hides", { params: { textId } });
};

export const restoreGeoHide = async (id: number) => {
  await http.delete(`/geo-hides/${id}`);
};
