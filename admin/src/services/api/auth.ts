import http from "@/services/http";

export interface LoginPayload {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username?: string;
  email?: string;
  message?: string;
}

export const login = async (payload: LoginPayload) => {
  const { data } = await http.post<AuthResponse>("/auth/login", payload);
  return data;
};
