import http from "@/services/http";

export interface EntityAnnotation {
  id: number;
  label?: string;
  category?: string;
  startOffset?: number;
  endOffset?: number;
  section?: {
    id: number;
    originalText?: string;
  };
}

export interface RelationAnnotation {
  id: number;
  relationType?: string;
  evidence?: string;
  source?: EntityAnnotation;
  target?: EntityAnnotation;
}

export const listEntities = async (textId: number) => {
  const { data } = await http.get<EntityAnnotation[]>(
    `/annotations/entities?textId=${textId}`
  );
  return data;
};

export const listRelations = async (textId: number) => {
  const { data } = await http.get<RelationAnnotation[]>(
    `/annotations/relations?textId=${textId}`
  );
  return data;
};

export const searchEntities = async (query: string) => {
  const { data } = await http.get<EntityAnnotation[]>("/annotations/search", {
    params: { query },
  });
  return data;
};

export const deleteEntity = async (id: number) => {
  await http.delete(`/annotations/entities/${id}`);
};

export const deleteRelation = async (id: number) => {
  await http.delete(`/annotations/relations/${id}`);
};
