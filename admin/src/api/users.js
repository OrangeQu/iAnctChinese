import api from "./client";

export const fetchUsers = () => api.get("/users");
export const createUserApi = (payload) => api.post("/users", payload);
export const updateUserStatus = (id, enabled) =>
  api.patch(`/users/${id}/status`, { enabled });
