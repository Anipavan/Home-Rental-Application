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
  // withCredentials: true is REQUIRED — the hra_refresh cookie is
  // HttpOnly + SameSite=Lax and only travels when credentials are
  // included. Missing this here would break the silent-refresh handshake
  // on every hard page reload.
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

  // Silent-refresh on hard reload: if we think we're signed in
  // (isAuthenticated persisted to localStorage) but have no in-memory
  // access token, try /auth/refresh — the browser will attach the
  // hra_refresh cookie automatically. Skip for /auth/* endpoints to
  // avoid infinite recursion.
  const path = (config.url ?? "").toLowerCase();
  const looksLikeAuthEndpoint = path.includes("/auth/login")
      || path.includes("/auth/register")
      || path.includes("/auth/refresh")
      || path.includes("/auth/forgot-password")
      || path.includes("/auth/reset-password");
  if (!token && state.isAuthenticated && !looksLikeAuthEndpoint) {
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

/**
 * Mint a fresh access token using the hra_refresh HttpOnly cookie
 * that the browser attaches automatically. No body needed — the
 * cookie is the credential. Backend also Set-Cookies a rotated
 * refresh token in the response.
 */
async function refreshAccessToken(): Promise<string> {
  const { setTokens, clear } = useAuthStore.getState();
  try {
    const { data } = await axios.post(
      `${BASE_URL}/auth/refresh`,
      {},
      {
        withCredentials: true,
        headers: { "ngrok-skip-browser-warning": "true" },
      },
    );
    setTokens(data.accessToken, data.accessTokenExpiresInSeconds);
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

    // ── Account-disabled kill switch ───────────────────────────
    // If ANY request comes back with errorCode=ACCOUNT_DISABLED,
    // the admin has just switched this user off (or their JWT was
    // issued before a disable happened and the gateway just caught
    // up). Clear local auth state and hard-redirect to /login with
    // ?disabled=1 so the login page can surface a "contact support"
    // banner. Runs BEFORE the refresh retry — a disabled account
    // won't get a fresh token back from /auth/refresh either, so
    // trying is wasted work.
    const errorCode = (error.response?.data as { errorCode?: string } | undefined)
      ?.errorCode;
    if (errorCode === "ACCOUNT_DISABLED") {
      try {
        useAuthStore.getState().clear();
      } catch {
        /* store may already be cleared — that's fine */
      }
      if (typeof window !== "undefined"
          && window.location.pathname !== "/login") {
        window.location.href = "/login?disabled=1";
      }
      return Promise.reject(error);
    }

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
        // Audit M27: redirect to /login ONLY when we're not already
        // there. Otherwise a 401 on the login page itself (or on a
        // queued request during the redirect) re-triggers the same
        // assignment and we loop forever. window.location.href
        // assignment in a tight loop will eventually crash the tab.
        if (typeof window !== "undefined"
            && window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  },
);

/**
 * Audit M28: extract a user-facing error message from an Axios error,
 * REDACTING anything that looks like internal infrastructure leakage
 * (SQL table names, stack-trace fragments, JDBC class names). The
 * raw backend message goes through {@link sanitizeForUser} before
 * being returned.
 */
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
    return sanitizeForUser(message) || err.message || fallback;
  }
  if (err instanceof Error) return err.message;
  return fallback;
}

/**
 * Drop leaked infrastructure detail from a backend error message
 * before showing it in a toast. Catches the common bleeds:
 *   - "Hibernate: ..."
 *   - "ORA-NNNNN: ..." Oracle codes
 *   - "java.lang.NullPointerException" + class FQNs
 *   - SQL constraint names like "UQ_FAV_USER_FLAT"
 *   - stack-trace lines starting with "\tat com.spa..."
 * If we'd redact more than 30% of the message, fall back to a generic
 * copy — leftover punctuation/whitespace looks worse than a clean
 * fallback.
 */
function sanitizeForUser(message: string | undefined): string {
  if (!message) return "";
  const original = message;
  let s = message
    // Strip stack-trace lines (Java + JS)
    .replace(/\s*\tat\s+[\w$.]+\([^)]*\)/g, "")
    // Strip Java exception class FQNs
    .replace(/\b(java|jakarta|org|com)\.[\w.$]+Exception(:\s+)?/g, "")
    .replace(/\b[\w.$]+Exception:\s+/g, "")
    // Strip Oracle codes
    .replace(/\bORA-\d{4,5}:\s*[^.\n]*\.?/g, "")
    // Strip "Hibernate:" preamble + the SQL that follows
    .replace(/\bHibernate:\s*[^\n]*/g, "")
    // Strip explicit SQL keywords (heuristic) when followed by a
    // table-like name
    .replace(/\b(SELECT|INSERT INTO|UPDATE|DELETE FROM)\s+[\w$.]+/gi, "")
    .trim();
  if (s.length === 0 || s.length < original.length * 0.7) {
    // Lost too much — fall back to a generic message instead of a
    // confusing fragment.
    return "Something went wrong on our side. Please retry or contact support.";
  }
  return s;
}
