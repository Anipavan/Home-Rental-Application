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
  refreshToken: string | null;
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
    refreshToken: string,
    expiresInSeconds?: number,
  ) => void;
  touchActivity: () => void;
  clear: () => void;
}

/**
 * Audit H17: the access token NEVER persists to localStorage anymore.
 * An XSS attack still has window-scoped access while the page is open,
 * but a stolen localStorage dump is no longer enough to impersonate
 * the user.
 *
 * Strategy:
 *   - accessToken / accessTokenExpiresAt live in memory only — gone
 *     on full page reload.
 *   - refreshToken still persists (it's opaque + server-rotated + now
 *     IP/UA-bound thanks to the H5 backend fix) so users don't have
 *     to re-log-in after every tab close.
 *   - On app boot, if a refreshToken is present in localStorage but no
 *     accessToken in memory, the API client transparently calls
 *     `/auth/refresh` to mint a new access token. UX is unchanged;
 *     the security boundary shrinks dramatically.
 *
 * The {@code partialize} option below is Zustand's way to opt fields
 * INTO persistence — anything not listed stays in memory only.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
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
          refreshToken: auth.refreshToken,
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
      setTokens: (accessToken, refreshToken, expiresInSeconds) =>
        set({
          accessToken,
          refreshToken,
          accessTokenExpiresAt:
            expiresInSeconds != null ? Date.now() + expiresInSeconds * 1000 : null,
        }),
      touchActivity: () => set({ lastActivityAt: Date.now() }),
      clear: () =>
        set({
          accessToken: null,
          refreshToken: null,
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
      // Whitelist the non-sensitive bits — the access token is
      // intentionally excluded so it can't be exfiltrated from a
      // localStorage dump. On hard refresh the API client uses the
      // persisted refresh token to mint a new access token.
      partialize: (state) => ({
        refreshToken: state.refreshToken,
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
