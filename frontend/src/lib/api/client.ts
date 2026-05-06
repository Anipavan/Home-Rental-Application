import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from "axios";
import { useAuthStore } from "@/stores/auth-store";

const BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  "http://localhost:8080/rentals/v1";

export const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    if (!config.headers) config.headers = new AxiosHeaders();
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const { refreshToken, setTokens, clear } = useAuthStore.getState();
  if (!refreshToken) throw new Error("No refresh token");
  try {
    const { data } = await axios.post(
      `${BASE_URL}/auth/refresh`,
      { refreshToken },
      { withCredentials: true },
    );
    setTokens(data.accessToken, data.refreshToken ?? refreshToken);
    return data.accessToken as string;
  } catch (e) {
    clear();
    throw e;
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & {
      _retried?: boolean;
    };
    const expired =
      error.response?.status === 401 &&
      (error.response?.headers?.["x-token-expired"] === "true" ||
        error.response?.headers?.["X-Token-Expired"] === "true");

    if (expired && original && !original._retried) {
      original._retried = true;
      try {
        refreshing = refreshing ?? refreshAccessToken();
        const fresh = await refreshing;
        refreshing = null;
        if (!original.headers) original.headers = new AxiosHeaders();
        (original.headers as AxiosHeaders).set(
          "Authorization",
          `Bearer ${fresh}`,
        );
        return api(original);
      } catch {
        refreshing = null;
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  },
);

export function extractErrorMessage(err: unknown, fallback = "Something went wrong"): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined;
    return data?.message || err.message || fallback;
  }
  if (err instanceof Error) return err.message;
  return fallback;
}
