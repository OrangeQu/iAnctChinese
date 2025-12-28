import http from "@/services/http";

export interface TextSection {
  id: number;
  textDocument?: { id: number };
  sequenceIndex: number;
  originalText: string;
  punctuatedText?: string;
  summary?: string;
}

export interface UpdateSectionRequest {
  originalText: string;
  punctuatedText?: string;
  summary?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const listSections = async (textId: number, params?: { page?: number; size?: number }) => {
  const { data } = await http.get<Page<TextSection>>(`/texts/${textId}/sections`, {
    params,
  });
  return data;
};

export const autoSegment = async (textId: number) => {
  const { data } = await http.post<TextSection[]>(`/texts/${textId}/sections/auto`);
  return data;
};

export const updateSection = async (sectionId: number, payload: UpdateSectionRequest) => {
  const { data } = await http.put<TextSection>(`/sections/${sectionId}`, payload);
  return data;
};

export interface SectionEntity {
  id: number;
  startOffset: number;
  endOffset: number;
  label: string;
  category?: string;
  confidence?: number;
  color?: string;
}

export interface SectionRelation {
  id: number;
  relationType?: string;
  confidence?: number;
  evidence?: string;
  source?: SectionEntity;
  target?: SectionEntity;
}

export interface SectionMarker {
  id: number;
  textId?: number;
  entityId?: number;
  entityLabel?: string;
  category?: string;
  latitude?: number;
  longitude?: number;
  orderIndex?: number;
}

export const listSectionEntities = async (sectionId: number) => {
  const { data } = await http.get<SectionEntity[]>(`/sections/${sectionId}/entities`);
  return data;
};

export const listSectionRelations = async (sectionId: number) => {
  const { data } = await http.get<SectionRelation[]>(`/sections/${sectionId}/relations`);
  return data;
};

export const listSectionMarkers = async (sectionId: number) => {
  const { data } = await http.get<SectionMarker[]>(`/sections/${sectionId}/markers`);
  return data;
};
