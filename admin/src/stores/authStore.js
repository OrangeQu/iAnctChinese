import { defineStore } from "pinia";
import { loginApi, profileApi } from "@/api/auth";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem("admin_token") || "",
    user: null,
    loading: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    hasToken: (state) => Boolean(state.token)
  },
  actions: {
    async login(payload) {
      this.loading = true;
      try {
        const { data } = await loginApi(payload);
        this.token = data.token;
        localStorage.setItem("admin_token", this.token);
        await this.fetchProfile();
      } finally {
        this.loading = false;
      }
    },
    async fetchProfile() {
      if (!this.token) return;
      try {
        const { data } = await profileApi();
        this.user = data;
      } catch (err) {
        this.logout();
        throw err;
      }
    },
    logout() {
      this.token = "";
      this.user = null;
      localStorage.removeItem("admin_token");
    }
  }
});
