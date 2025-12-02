import api from "./client";

export const loginApi = (payload) => api.post("/auth/login", payload);
export const profileApi = () => api.get("/auth/current");
