import apiClient from './client';

export const authApi = {
  register(data) {
    return apiClient.post('/auth/register', data);
  },

  login(data) {
    return apiClient.post('/auth/login', data);
  },

  getCurrentUser() {
    return apiClient.get('/auth/current');
  }
};
