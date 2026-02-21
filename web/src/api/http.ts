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
