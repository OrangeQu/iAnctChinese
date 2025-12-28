import http from "@/services/http";

export interface EntityAnnotation {
  id: number;
  entityName?: string;
  entityType?: string;
  context?: string;
}

export interface RelationAnnotation {
  id: number;
  subject?: string;
  relation?: string;
  object?: string;
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

export const deleteEntity = async (id: number) => {
  await http.delete(`/annotations/entities/${id}`);
};

export const deleteRelation = async (id: number) => {
  await http.delete(`/annotations/relations/${id}`);
};
