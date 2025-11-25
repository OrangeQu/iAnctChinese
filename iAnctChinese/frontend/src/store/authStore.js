import { defineStore } from 'pinia';
import { authApi } from '../api/auth';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    token: localStorage.getItem('token') || null,
    isAuthenticated: false
  }),

  actions: {
    async register(registerData) {
      try {
        const response = await authApi.register(registerData);
        if (response.data.token) {
          this.token = response.data.token;
          this.user = {
            username: response.data.username,
            email: response.data.email
          };
          this.isAuthenticated = true;
          localStorage.setItem('token', this.token);
          return { success: true, message: response.data.message };
        } else {
          return { success: false, message: response.data.message };
        }
      } catch (error) {
        const message = error.response?.data?.message || '注册失败';
        return { success: false, message };
      }
    },

    async login(loginData) {
      try {
        const response = await authApi.login(loginData);
        if (response.data.token) {
          this.token = response.data.token;
          this.user = {
            username: response.data.username,
            email: response.data.email
          };
          this.isAuthenticated = true;
          localStorage.setItem('token', this.token);
          return { success: true, message: response.data.message };
        } else {
          return { success: false, message: response.data.message };
        }
      } catch (error) {
        const message = error.response?.data?.message || '登录失败';
        return { success: false, message };
      }
    },

    async loadUser() {
      if (!this.token) {
        return;
      }
      try {
        const response = await authApi.getCurrentUser();
        this.user = response.data;
        this.isAuthenticated = true;
      } catch (error) {
        this.logout();
      }
    },

    logout() {
      this.token = null;
      this.user = null;
      this.isAuthenticated = false;
      localStorage.removeItem('token');
    }
  }
});
