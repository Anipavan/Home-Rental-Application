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

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      authUserId: null,
      userName: null,
      role: null,
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
          isAuthenticated: false,
          accessTokenExpiresAt: null,
          lastActivityAt: null,
        }),
    }),
    { name: "hearth-auth" },
  ),
);
