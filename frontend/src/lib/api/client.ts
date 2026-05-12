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
  headers: {
    "Content-Type": "application/json",
    // ngrok's free tier blocks XHR from browsers (any request with a
    // browser User-Agent) with 403 unless this header is present. It's
    // a no-op when not going through ngrok, so we send it on every call.
    "ngrok-skip-browser-warning": "true",
  },
});

api.interceptors.request.use(async (config) => {
  const state = useAuthStore.getState();
  let token = state.accessToken;

  // Audit H17: the access token no longer persists. On a hard refresh
  // we lose it but the refresh token is still on disk. If we're about
  // to make a request that needs auth, transparently mint a new
  // access token first so the user experience is identical to the
  // pre-H17 behaviour. Skip for the /auth/* endpoints themselves to
  // avoid recursion.
  const path = (config.url ?? "").toLowerCase();
  const looksLikeAuthEndpoint = path.includes("/auth/login")
      || path.includes("/auth/register")
      || path.includes("/auth/refresh")
      || path.includes("/auth/forgot-password")
      || path.includes("/auth/reset-password");
  if (!token && state.refreshToken && !looksLikeAuthEndpoint) {
    try {
      refreshing = refreshing ?? refreshAccessToken();
      token = await refreshing;
      refreshing = null;
    } catch {
      refreshing = null;
      // fall through — request will get 401 and the response
      // interceptor below will redirect to /login.
    }
  }

  if (token) {
    if (!config.headers) config.headers = new AxiosHeaders();
    config.headers.set("Authorization", `Bearer ${token}`);
  }

  // FormData → drop the JSON default Content-Type so the browser
  // gets to set `multipart/form-data; boundary=...` itself. axios
  // v1's merge of instance defaults + per-request headers is
  // unreliable when the per-request value is `undefined` — the
  // instance-level `application/json` can bleed through and Spring's
  // multipart parser then rejects the body as malformed. The visible
  // symptom is a generic 500 from the document/user-service catch-all
  // ("An unexpected error occurred"). Explicitly deleting the header
  // here removes that whole class of bug from every upload site.
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    if (config.headers instanceof AxiosHeaders) {
      config.headers.delete("Content-Type");
    } else if (config.headers) {
      // Plain-object headers path — same intent.
      delete (config.headers as Record<string, unknown>)["Content-Type"];
    }
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
      {
        withCredentials: true,
        headers: { "ngrok-skip-browser-warning": "true" },
      },
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
    const data = err.response?.data as
      | { message?: string; errorCode?: string; error?: string }
      | undefined;
    const message = data?.message;
    const code = data?.errorCode;
    // The backend catch-all returns the same string ("An unexpected
    // error occurred. Please contact support.") for every unhandled
    // exception — useless in a toast because the user can't tell what
    // actually broke. When we see it, fall back to something more
    // diagnostic: the HTTP status code + endpoint, plus the error code
    // if it's anything other than INTERNAL_ERROR.
    if (
      message &&
      (message.toLowerCase().includes("unexpected error occurred") ||
        code === "INTERNAL_ERROR")
    ) {
      const status = err.response?.status;
      const path = err.response?.config?.url ?? err.config?.url ?? "";
      const briefPath = path.split("?")[0].split("/").slice(-3).join("/");
      const parts: string[] = ["Request failed"];
      if (status) parts.push(`(HTTP ${status})`);
      if (briefPath) parts.push(`on ${briefPath}`);
      parts.push("— please retry, or contact support if it persists.");
      return parts.join(" ");
    }
    return message || err.message || fallback;
  }
  if (err instanceof Error) return err.message;
  return fallback;
}
