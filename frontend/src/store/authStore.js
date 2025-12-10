import { defineStore } from 'pinia';
import { authApi } from '../api/auth';
import { userApi } from '../api/user';

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
        const response = await userApi.getProfile();
        this.user = response.data;
        this.isAuthenticated = true;
      } catch (error) {
        this.logout();
      }
    },

    async updateEmail(email) {
      try {
        const response = await userApi.updateEmail({ email });
        this.user = response.data;
        return { success: true, message: '邮箱已更新' };
      } catch (error) {
        const message = error.response?.data?.message || '邮箱更新失败';
        return { success: false, message };
      }
    },

    async changePassword(payload) {
      try {
        const response = await userApi.changePassword(payload);
        return { success: true, message: response.data?.message || '密码已更新' };
      } catch (error) {
        const message = error.response?.data?.message || '密码修改失败';
        return { success: false, message };
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
