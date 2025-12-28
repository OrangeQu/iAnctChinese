import axios from "axios";
import { notify } from "@/services/toast";

const TOKEN_KEY = "adminToken";
const API_BASE_KEY = "adminApiBase";

const getDefaultBase = () => import.meta.env.VITE_API_BASE_URL || "/api";

export const getApiBase = () => {
  return localStorage.getItem(API_BASE_KEY) || getDefaultBase();
};

export const setApiBase = (value: string) => {
  const next = value || getDefaultBase();
  localStorage.setItem(API_BASE_KEY, next);
  http.defaults.baseURL = next;
};

const http = axios.create({
  baseURL: getApiBase(),
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status as number | undefined;
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      notify("登录过期", "error");
      const base = import.meta.env.BASE_URL || "/";
      const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      if (!window.location.pathname.includes("/login")) {
        window.location.href = `${base}login?redirect=${encodeURIComponent(redirect)}`;
      }
      return Promise.reject(error);
    }
    if (status === 403) {
      notify("无权限", "error");
      return Promise.reject(error);
    }
    const message =
      error?.response?.data?.message ||
      error?.message ||
      (status ? `Request failed (${status})` : "Network error");
    notify(String(message), "error");
    return Promise.reject(error);
  }
);

export default http;
