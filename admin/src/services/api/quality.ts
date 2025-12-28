import http from "@/services/http";

export interface EntityQualityIssue {
  entityId: number;
  textId: number;
  sectionId?: number;
  sectionIndex?: number;
  startOffset?: number;
  endOffset?: number;
  label?: string;
  category?: string;
  issueType: string;
  message: string;
}

export interface RelationQualityIssue {
  relationId: number;
  textId: number;
  sourceId?: number;
  targetId?: number;
  sourceSectionId?: number;
  targetSectionId?: number;
  relationType?: string;
  evidence?: string;
  issueType: string;
  message: string;
}

export const listEntityIssues = async (textId: number) => {
  const { data } = await http.get<EntityQualityIssue[]>("/quality/entities", {
    params: { textId },
  });
  return data;
};

export const listRelationIssues = async (textId: number) => {
  const { data } = await http.get<RelationQualityIssue[]>("/quality/relations", {
    params: { textId },
  });
  return data;
};
