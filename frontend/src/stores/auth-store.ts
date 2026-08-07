import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthResponse, Role } from "@/types/api";

/**
 * Auth + session state. Persisted to localStorage via zustand/persist (key
 * `hearth-auth`). Stabilization sprint additions:
 *  - {@link AuthState.accessTokenExpiresAt}: epoch-ms; lets the frontend
 *    proactively refresh / log out instead of waiting for a 401.
 *  - {@link AuthState.lastActivityAt}: epoch-ms; updated by the IdleTimer
 *    component. Drives the 30-min idle-logout policy.
 */
interface AuthState {
  accessToken: string | null;
  authUserId: string | null;
  userName: string | null;
  role: Role | null;
  /**
   * V17 multi-role: full set of roles the user holds. Always contains
   * {@link #role}; may contain additional roles. Single-role users
   * see roles=[role]; everything that currently keys on `role`
   * continues to work unchanged.
   */
  roles: string[];
  isAuthenticated: boolean;

  /** Epoch millis when the access token expires (issued + TTL from auth-service). */
  accessTokenExpiresAt: number | null;
  /** Epoch millis of the last user interaction. Updated by IdleTimer. */
  lastActivityAt: number | null;

  setSession: (auth: AuthResponse) => void;
  setTokens: (
    accessToken: string,
    expiresInSeconds?: number,
  ) => void;
  touchActivity: () => void;
  clear: () => void;
}

/**
 * Session-storage strategy:
 *
 *  - accessToken / accessTokenExpiresAt live in MEMORY ONLY — gone on
 *    full page reload. XSS can't exfiltrate a persisted copy.
 *
 *  - refreshToken lives in an HttpOnly + Secure + SameSite=Lax cookie
 *    (set by auth-service as `hra_refresh` on login/refresh). It is
 *    NOT accessible from JavaScript at all. XSS on our domain cannot
 *    read or exfiltrate it; the browser sends it automatically on
 *    same-site requests (all axios calls have withCredentials:true).
 *
 *  - On app boot with no in-memory accessToken, the axios interceptor
 *    transparently POSTs /auth/refresh with an empty body — the
 *    browser attaches the hra_refresh cookie, backend rotates and
 *    returns a fresh access token + a fresh Set-Cookie for the new
 *    refresh token. UX is a silent handshake; user stays signed in.
 *
 * The {@code partialize} option is Zustand's way to opt fields INTO
 * persistence — anything not listed stays in memory only. We persist
 * only non-sensitive UI state (username, role, authenticated flag);
 * NOTHING that could impersonate the user ever hits localStorage.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      authUserId: null,
      userName: null,
      role: null,
      roles: [],
      isAuthenticated: false,
      accessTokenExpiresAt: null,
      lastActivityAt: null,
      setSession: (auth) =>
        set({
          accessToken: auth.accessToken,
          authUserId: auth.authUserId,
          userName: auth.userName,
          role: auth.role,
          roles:
            auth.roles && auth.roles.length > 0
              ? auth.roles
              : auth.role
                ? [auth.role]
                : [],
          isAuthenticated: true,
          accessTokenExpiresAt:
            auth.accessTokenExpiresInSeconds != null
              ? Date.now() + auth.accessTokenExpiresInSeconds * 1000
              : null,
          lastActivityAt: Date.now(),
        }),
      setTokens: (accessToken, expiresInSeconds) =>
        set({
          accessToken,
          accessTokenExpiresAt:
            expiresInSeconds != null ? Date.now() + expiresInSeconds * 1000 : null,
        }),
      touchActivity: () => set({ lastActivityAt: Date.now() }),
      clear: () =>
        set({
          accessToken: null,
          authUserId: null,
          userName: null,
          role: null,
          roles: [],
          isAuthenticated: false,
          accessTokenExpiresAt: null,
          lastActivityAt: null,
        }),
    }),
    {
      name: "hearth-auth",
      // Only non-sensitive UI state. The access token stays in memory
      // (XSS can't exfiltrate a persisted copy); the refresh token
      // lives in an HttpOnly cookie (JS can't read it at all). Neither
      // token ever touches localStorage.
      partialize: (state) => ({
        authUserId: state.authUserId,
        userName: state.userName,
        role: state.role,
        roles: state.roles,
        isAuthenticated: state.isAuthenticated,
        lastActivityAt: state.lastActivityAt,
      }),
    },
  ),
);
