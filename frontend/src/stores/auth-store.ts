import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthResponse, Role } from "@/types/api";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  authUserId: string | null;
  userName: string | null;
  role: Role | null;
  isAuthenticated: boolean;
  setSession: (auth: AuthResponse) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
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
      setSession: (auth) =>
        set({
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
          authUserId: auth.authUserId,
          userName: auth.userName,
          role: auth.role,
          isAuthenticated: true,
        }),
      setTokens: (accessToken, refreshToken) =>
        set({ accessToken, refreshToken }),
      clear: () =>
        set({
          accessToken: null,
          refreshToken: null,
          authUserId: null,
          userName: null,
          role: null,
          isAuthenticated: false,
        }),
    }),
    { name: "hearth-auth" },
  ),
);
