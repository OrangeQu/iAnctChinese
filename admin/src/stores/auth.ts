import { defineStore } from "pinia";
import { login as loginApi } from "@/services/api/auth";
import { getCurrentUser } from "@/services/api/user";

const TOKEN_KEY = "adminToken";

export interface UserInfo {
  id: number;
  username: string;
  email: string;
  createTime: string | null;
  lastLoginTime: string | null;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: null as string | null,
    user: null as UserInfo | null,
  }),
  actions: {
    restore() {
      this.token = localStorage.getItem(TOKEN_KEY);
    },
    async login(username: string, password: string) {
      const response = await loginApi({ username, password });
      this.token = response.token;
      localStorage.setItem(TOKEN_KEY, response.token);
      await this.fetchCurrentUser();
    },
    async fetchCurrentUser() {
      try {
        this.user = await getCurrentUser();
      } catch (error) {
        this.user = null;
      }
    },
    logout() {
      this.token = null;
      this.user = null;
      localStorage.removeItem(TOKEN_KEY);
    },
  },
});
