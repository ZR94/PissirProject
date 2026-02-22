import axios from "axios";
import { refreshToken, getKeycloak } from "@/auth/keycloak";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
});

http.interceptors.request.use(async (config) => {
  const kc = getKeycloak();

  if (kc.authenticated) {
    // Keep token fresh
    await refreshToken(30);

    const token = kc.token;
    if (token) {
      config.headers = config.headers ?? {};
      config.headers.Authorization = `Bearer ${token}`;
    }
  }

  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const kc = getKeycloak();
    const status = error?.response?.status;
    const originalRequest = error?.config as
      | ({ headers?: Record<string, string>; _retry?: boolean } & Record<string, unknown>)
      | undefined;

    if (status === 401 && kc.authenticated && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;
      await refreshToken(0);
      const token = kc.token;
      if (token) {
        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return http(originalRequest);
      }
    }

    if (status === 401 && !kc.authenticated) {
      kc.login();
    }

    return Promise.reject(error);
  },
);
