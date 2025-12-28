import http from "@/services/http";

export interface VisualizationPreset {
  id: number;
  label: string;
  textCategory: string;
  configJson?: string | null;
  isDefault?: boolean;
}

export interface PresetRequest {
  label: string;
  textCategory: string;
  configJson?: string | null;
  isDefault?: boolean;
}

export const listPresets = async (category?: string) => {
  const { data } = await http.get<VisualizationPreset[]>("/presets", {
    params: { category },
  });
  return data;
};

export const createPreset = async (payload: PresetRequest) => {
  const { data } = await http.post<VisualizationPreset>("/presets", payload);
  return data;
};

export const updatePreset = async (id: number, payload: PresetRequest) => {
  const { data } = await http.put<VisualizationPreset>(`/presets/${id}`, payload);
  return data;
};

export const deletePreset = async (id: number) => {
  await http.delete(`/presets/${id}`);
};
