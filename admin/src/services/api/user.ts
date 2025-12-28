import http from "@/services/http";

export interface UserInfoResponse {
  id: number;
  username: string;
  email: string;
  createTime?: string;
  lastLoginTime?: string;
  role?: string;
}

export interface UpdateEmailRequest {
  email: string;
}

export interface UpdatePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export const getCurrentUser = async () => {
  const { data } = await http.get<UserInfoResponse>("/user/me");
  return data;
};

export const updateEmail = async (payload: UpdateEmailRequest) => {
  const { data } = await http.put<UserInfoResponse>("/user/email", payload);
  return data;
};

export const updatePassword = async (payload: UpdatePasswordRequest) => {
  const { data } = await http.put("/user/password", payload);
  return data;
};
