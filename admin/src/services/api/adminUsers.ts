import http from "@/services/http";

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  enabled: boolean | number;
  createTime?: string;
  lastLoginTime?: string;
  create_time?: string;
  last_login_time?: string;
}

export interface AdminUserPage {
  content: AdminUser[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
}

export interface ListUsersParams {
  query?: string;
  enabled?: boolean;
  page?: number;
  size?: number;
}

export const listUsers = async (params: ListUsersParams) => {
  const { data } = await http.get<AdminUser[] | AdminUserPage>("/admin/users", {
    params,
  });
  return data;
};

export const updateUserEnabled = async (id: number, enabled: boolean) => {
  const { data } = await http.put(`/admin/users/${id}/enabled`, { enabled });
  return data;
};

export const resetUserPassword = async (id: number, newPassword: string) => {
  const { data } = await http.post(`/admin/users/${id}/reset-password`, {
    newPassword,
  });
  return data;
};
