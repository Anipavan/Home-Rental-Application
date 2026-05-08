import { api } from "./client";
import type {
  CreateLeaseRequest,
  LeaseHistoryEntry,
  LeaseResponse,
  RenewLeaseRequest,
  SignLeaseRequest,
  TerminateLeaseRequest,
} from "@/types/api";

/**
 * Lease Service (port 8090). Mounted at `/lease/**` server-side; gateway
 * route is `/rentals/v1/lease/**`.
 */
export const leaseApi = {
  create: (body: CreateLeaseRequest) =>
    api.post<LeaseResponse>("/lease/leases", body).then((r) => r.data),

  getById: (id: string) =>
    api.get<LeaseResponse>(`/lease/leases/${id}`).then((r) => r.data),

  byTenant: (tenantId: string) =>
    api
      .get<LeaseResponse[]>(`/lease/leases/tenant/${tenantId}`)
      .then((r) => r.data),

  byFlat: (flatId: string) =>
    api
      .get<LeaseResponse[]>(`/lease/leases/flat/${flatId}`)
      .then((r) => r.data),

  renew: (id: string, body: RenewLeaseRequest) =>
    api
      .put<LeaseResponse>(`/lease/leases/${id}/renew`, body)
      .then((r) => r.data),

  terminate: (id: string, body: TerminateLeaseRequest) =>
    api
      .put<LeaseResponse>(`/lease/leases/${id}/terminate`, body)
      .then((r) => r.data),

  sign: (id: string, body: SignLeaseRequest) =>
    api
      .post<LeaseResponse>(`/lease/leases/${id}/sign`, body)
      .then((r) => r.data),

  /** Returns a Blob — surface to the user via a download link. */
  downloadDocument: (id: string) =>
    api
      .get<Blob>(`/lease/leases/${id}/document`, { responseType: "blob" })
      .then((r) => r.data),

  expiring: (days = 60) =>
    api
      .get<LeaseResponse[]>(`/lease/expiring`, { params: { days } })
      .then((r) => r.data),

  generateRera: (id: string, state: string) =>
    api
      .post<LeaseResponse>(`/lease/leases/generate-rera/${id}`, null, {
        params: { state },
      })
      .then((r) => r.data),

  history: (id: string) =>
    api
      .get<LeaseHistoryEntry[]>(`/lease/leases/${id}/history`)
      .then((r) => r.data),
};
