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
  punctuatedText?: string;
  summary?: string;
}

export const listSections = async (textId: number) => {
  const { data } = await http.get<TextSection[]>(`/texts/${textId}/sections`);
  return data;
};

export const autoSegment = async (textId: number) => {
  const { data } = await http.post<TextSection[]>(`/texts/${textId}/sections/auto`);
  return data;
};

export const updateSection = async (sectionId: number, payload: UpdateSectionRequest) => {
  const { data } = await http.patch<TextSection>(`/sections/${sectionId}`, payload);
  return data;
};

export const getSectionBundle = async (textId: number, sectionId: number) => {
  const { data } = await http.get(
    `/texts/${textId}/section/${sectionId}/bundle`
  );
  return data;
};
