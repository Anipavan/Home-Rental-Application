import { api } from "./client";
import type {
  AuthResponse,
  AuthUserResponse,
  LoginRequest,
  MessageResponse,
  RegisterRequest,
  RegisterResponse,
} from "@/types/api";

export const authApi = {
  login: (body: LoginRequest) =>
    api.post<AuthResponse>("/auth/login", body).then((r) => r.data),
  register: (body: RegisterRequest) =>
    api.post<RegisterResponse>("/auth/register", body).then((r) => r.data),
  forgotPassword: (email: string) =>
    api
      .post<MessageResponse>("/auth/forgot-password", { email })
      .then((r) => r.data),
  resetPassword: (token: string, newPassword: string) =>
    api
      .post<MessageResponse>("/auth/reset-password", { token, newPassword })
      .then((r) => r.data),
  logout: (refreshToken: string) =>
    api
      .post<MessageResponse>("/auth/logout", { refreshToken })
      .then((r) => r.data),
  /**
   * Manual refresh — used by the "Stay signed in" idle-timer button.
   * The interceptor in client.ts handles the automatic on-401-retry
   * refresh; this overload is for explicit UI-initiated extension.
   */
  refresh: (refreshToken: string) =>
    api
      .post<AuthResponse>("/auth/refresh", { refreshToken })
      .then((r) => r.data),
  byRole: (role: string) =>
    api.get<AuthUserResponse[]>(`/auth/role/${role}`).then((r) => r.data),
  byId: (id: string) =>
    api.get<AuthUserResponse>(`/auth/users/${id}`).then((r) => r.data),
  /**
   * Owner-accessible fallback lookup. Used by the tenant-detail / tenants
   * list when the User Service has no profile row for a given authUserId
   * (common for legacy registrations) — at minimum we get back userName,
   * email, and role so we can render something useful.
   */
  lookupById: (id: string) =>
    api.get<AuthUserResponse>(`/auth/users/lookup/${id}`).then((r) => r.data),
};
