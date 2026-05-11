import { api } from "./client";
import type {
  CreateRequestDto,
  MaintenanceHistoryEntry,
  MaintenanceRequestResponse,
  MaintenanceStatus,
  Page,
  TicketKind,
} from "@/types/api";

/**
 * Maintenance + complaint API client.
 *
 * Backend uses one collection / one state machine for both kinds (so
 * assignment, comments, status-change, image upload, and Kafka
 * fan-out are shared). Callers pick the kind they care about via the
 * {@code ?kind=} query parameter — when omitted, the server returns
 * everything (default behavior preserved for legacy callers).
 *
 * The default {@link maintenanceApi} surface filters to
 * {@code kind=MAINTENANCE} so existing maintenance pages don't
 * accidentally inherit complaints. The complaints UX uses
 * {@link complaintsApi} which mirrors the surface but filters to
 * {@code kind=COMPLAINT}.
 */

const KIND_MAINTENANCE: TicketKind = "MAINTENANCE";
const KIND_COMPLAINT: TicketKind = "COMPLAINT";

function makeApi(kind: TicketKind) {
  return {
    list: (page = 0, size = 20) =>
      api
        .get<Page<MaintenanceRequestResponse>>("/maintenance/requests", {
          params: { page, size, kind },
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
          { params: { kind } },
        )
        .then((r) => r.data),
    byOwner: (ownerId: string) =>
      api
        .get<MaintenanceRequestResponse[]>(
          `/maintenance/requests/owner/${ownerId}`,
          { params: { kind } },
        )
        .then((r) => r.data),
    byStatus: (status: MaintenanceStatus) =>
      api
        .get<MaintenanceRequestResponse[]>(
          `/maintenance/requests/status/${status}`,
        )
        // Status endpoint isn't kind-scoped on the backend; filter here.
        .then((r) => r.data.filter((row) => row.kind === kind)),
    create: (body: Omit<CreateRequestDto, "kind">) =>
      api
        .post<MaintenanceRequestResponse>("/maintenance/requests", {
          ...body,
          kind,
        })
        .then((r) => r.data),
    comment: (id: string, userId: string, comment: string) =>
      api
        .post<MaintenanceRequestResponse>(
          `/maintenance/requests/${id}/comment`,
          { userId, comment },
        )
        .then((r) => r.data),
    setStatus: (
      id: string,
      newStatus: MaintenanceStatus,
      changedBy: string,
    ) =>
      api
        .post<MaintenanceRequestResponse>(
          `/maintenance/requests/${id}/status`,
          { newStatus, changedBy },
        )
        .then((r) => r.data),
    assign: (id: string, assignedTo: string) =>
      api
        .post<MaintenanceRequestResponse>(
          `/maintenance/requests/${id}/assign`,
          { assignedTo },
        )
        .then((r) => r.data),
    history: (id: string) =>
      api
        .get<MaintenanceHistoryEntry[]>(`/maintenance/requests/${id}/history`)
        .then((r) => r.data),
    pendingCount: () =>
      api
        .get<number>(`/maintenance/requests/pending-count`, {
          params: { kind },
        })
        .then((r) => r.data),
    uploadImage: (id: string, file: File) => {
      const fd = new FormData();
      fd.append("file", file);
      return api
        .post<MaintenanceRequestResponse>(
          `/maintenance/requests/${id}/images`,
          fd,
          // axios + the browser handle the multipart boundary; an
          // explicit Content-Type would strip the boundary and break
          // upstream parsing. See documentsApi.upload for the same pattern.
          { headers: { "Content-Type": undefined } },
        )
        .then((r) => r.data);
    },
  };
}

export const maintenanceApi = makeApi(KIND_MAINTENANCE);
export const complaintsApi = makeApi(KIND_COMPLAINT);
