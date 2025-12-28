import http from "@/services/http";

export interface DashboardOverview {
  text?: {
    id: number;
    title: string;
    category?: string;
  };
  entityCount?: number;
  relationCount?: number;
  status?: string;
}

export const getOverview = async (textId: number) => {
  const { data } = await http.get<DashboardOverview>(`/dashboard/overview?textId=${textId}`);
  return data;
};
