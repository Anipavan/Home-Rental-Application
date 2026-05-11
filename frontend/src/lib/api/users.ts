import { api } from "./client";
import type {
  Page,
  UserByRole,
  UserRequestDto,
  UserResponseDto,
} from "@/types/api";

export const usersApi = {
  list: (page = 0, size = 20) =>
    api
      .get<Page<UserResponseDto>>("/users", { params: { page, size } })
      .then((r) => r.data),
  getById: (id: number | string) =>
    api.get<UserResponseDto>(`/users/user/${id}`).then((r) => r.data),
  byAuthId: (authUserId: string) =>
    api.get<UserResponseDto>(`/users/auth/${authUserId}`).then((r) => r.data),
  /**
   * Returns the projection joined with auth-service ({@link UserByRole})
   * so the caller has both {@code id} and {@code authUserId}. The
   * previous typing as {@code UserResponseDto[]} was a lie — those
   * fields aren't on the wire from this endpoint.
   */
  byRole: (role: string) =>
    api
      .get<UserByRole[]>(`/users/role/${role}`)
      .then((r) => r.data),
  search: (q: string) =>
    api.get<UserResponseDto[]>(`/users/search/${q}`).then((r) => r.data),
  create: (body: UserRequestDto) =>
    api.post<UserResponseDto>("/users/user", body).then((r) => r.data),
  update: (id: number | string, body: UserRequestDto) =>
    api.put<UserResponseDto>(`/users/user/${id}`, body).then((r) => r.data),
  remove: (id: number | string) =>
    api.delete<UserResponseDto>(`/users/${id}`).then((r) => r.data),
  uploadDocument: (
    userId: number | string,
    file: File,
    type: "PROFILE" | "ID_PROOF",
  ) => {
    const fd = new FormData();
    fd.append("file", file);
    return api
      .put<UserResponseDto>(`/users/${userId}/documents`, fd, {
        params: { type },
        // See documentsApi.upload for the full rationale — never set
        // a literal "multipart/form-data" Content-Type; axios + the
        // browser will add the boundary parameter automatically.
        headers: { "Content-Type": undefined },
      })
      .then((r) => r.data);
  },
};
