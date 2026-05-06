import { api } from "./client";
import type {
  CreateRequestDto,
  MaintenanceRequestResponse,
  MaintenanceStatus,
  Page,
} from "@/types/api";

export const maintenanceApi = {
  list: (page = 0, size = 20) =>
    api
      .get<Page<MaintenanceRequestResponse>>("/maintenance/requests", {
        params: { page, size },
      })
      .then((r) => r.data),
  get: (id: string) =>
    api
      .get<MaintenanceRequestResponse>(`/maintenance/requests/${id}`)
      .then((r) => r.data),
  byTenant: (tenantId: string) =>
    api
      .get<MaintenanceRequestResponse[]>(
        `/maintenance/requests/tenant/${tenantId}`,
      )
      .then((r) => r.data),
  byOwner: (ownerId: string) =>
    api
      .get<MaintenanceRequestResponse[]>(
        `/maintenance/requests/owner/${ownerId}`,
      )
      .then((r) => r.data),
  byStatus: (status: MaintenanceStatus) =>
    api
      .get<MaintenanceRequestResponse[]>(
        `/maintenance/requests/status/${status}`,
      )
      .then((r) => r.data),
  create: (body: CreateRequestDto) =>
    api
      .post<MaintenanceRequestResponse>("/maintenance/requests", body)
      .then((r) => r.data),
  comment: (id: string, userId: string, comment: string) =>
    api
      .post<MaintenanceRequestResponse>(
        `/maintenance/requests/${id}/comment`,
        { userId, comment },
      )
      .then((r) => r.data),
  setStatus: (id: string, newStatus: MaintenanceStatus, notes?: string) =>
    api
      .post<MaintenanceRequestResponse>(
        `/maintenance/requests/${id}/status`,
        { newStatus, notes },
      )
      .then((r) => r.data),
  pendingCount: () =>
    api
      .get<Record<string, number>>("/maintenance/stats/pending")
      .then((r) => r.data),
  uploadImage: (id: string, file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return api
      .post<MaintenanceRequestResponse>(
        `/maintenance/requests/${id}/images`,
        fd,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      .then((r) => r.data);
  },
};
