import apiClient from "./client";

export const userApi = {
  getProfile() {
    return apiClient.get("/user/me");
  },
  updateEmail(data) {
    return apiClient.put("/user/email", data);
  },
  changePassword(data) {
    return apiClient.put("/user/password", data);
  }
};
