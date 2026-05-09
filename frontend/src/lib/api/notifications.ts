import { api } from "./client";
import type {
  NotificationResponse,
  Page,
  SendNotificationRequest,
} from "@/types/api";

/**
 * Notification Service (port 8086) — direct + log endpoints.
 *
 * Mounted at `/notifications/**` server-side, gateway route is
 * `/rentals/v1/notifications/**`. Stabilization sprint additions:
 *  - {@link byUser}: list a user's notification log (powers the bell dropdown)
 *  - {@link supportTickets}: in-app support-ticket inbox
 */
export const notificationsApi = {
  sendEmail: (body: SendNotificationRequest) =>
    api
      .post<NotificationResponse>("/notifications/send/email", body)
      .then((r) => r.data),

  sendSms: (body: SendNotificationRequest) =>
    api
      .post<NotificationResponse>("/notifications/send/sms", body)
      .then((r) => r.data),

  sendPush: (body: SendNotificationRequest) =>
    api
      .post<NotificationResponse>("/notifications/send/push", body)
      .then((r) => r.data),

  byUser: (userId: string) =>
    api
      .get<NotificationResponse[]>(`/notifications/user/${userId}`)
      .then((r) => r.data),
};

/* ────────────────── Support tickets (Day 2–3 stabilization) ────────────────── */

export interface SupportTicket {
  id: string;
  userId: string;
  userName?: string;
  userEmail?: string;
  userRole?: string;
  subject: string;
  message: string;
  contextUrl?: string;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  adminResponse?: string;
  respondedBy?: string;
  respondedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateSupportTicketBody {
  userId: string;
  userName?: string;
  userEmail?: string;
  userRole?: string;
  subject: string;
  message: string;
  contextUrl?: string;
}

export interface RespondToTicketBody {
  respondedBy: string;
  adminResponse: string;
  newStatus: "IN_PROGRESS" | "RESOLVED" | "CLOSED";
}

export const supportTicketsApi = {
  create: (body: CreateSupportTicketBody) =>
    api
      .post<SupportTicket>("/notifications/support-tickets", body)
      .then((r) => r.data),

  getById: (id: string) =>
    api
      .get<SupportTicket>(`/notifications/support-tickets/${id}`)
      .then((r) => r.data),

  list: (status: SupportTicket["status"] = "OPEN", page = 0, size = 20) =>
    api
      .get<Page<SupportTicket>>("/notifications/support-tickets", {
        params: { status, page, size },
      })
      .then((r) => r.data),

  byUser: (userId: string, page = 0, size = 20) =>
    api
      .get<Page<SupportTicket>>(`/notifications/support-tickets/user/${userId}`, {
        params: { page, size },
      })
      .then((r) => r.data),

  respond: (id: string, body: RespondToTicketBody) =>
    api
      .put<SupportTicket>(`/notifications/support-tickets/${id}/respond`, body)
      .then((r) => r.data),

  openCount: () =>
    api
      .get<{ count: number }>("/notifications/support-tickets/count/open")
      .then((r) => r.data.count),
};

/* ────────────────── Visit requests (property-detail "Schedule a visit") ────────────────── */

export type VisitRequestStatus =
  | "PENDING"
  | "CONFIRMED"
  | "COMPLETED"
  | "CANCELLED";

export interface VisitRequest {
  id: string;
  userId: string;
  visitorName: string;
  visitorEmail?: string;
  visitorPhone?: string;
  flatId: string;
  buildingId?: string;
  propertyLabel?: string;
  /** ISO-8601 instant. Null when the visitor didn't pick a time. */
  preferredAt?: string | null;
  message?: string;
  contextUrl?: string;
  status: VisitRequestStatus;
  adminResponse?: string;
  respondedBy?: string;
  respondedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateVisitRequestBody {
  userId: string;
  visitorName: string;
  visitorEmail?: string;
  visitorPhone?: string;
  flatId: string;
  buildingId?: string;
  propertyLabel?: string;
  preferredAt?: string;
  message?: string;
  contextUrl?: string;
}

export interface RespondToVisitRequestBody {
  newStatus: VisitRequestStatus;
  adminResponse?: string;
  respondedBy?: string;
}

export const visitRequestsApi = {
  create: (body: CreateVisitRequestBody) =>
    api
      .post<VisitRequest>("/notifications/visit-requests", body)
      .then((r) => r.data),

  getById: (id: string) =>
    api
      .get<VisitRequest>(`/notifications/visit-requests/${id}`)
      .then((r) => r.data),

  list: (
    params: {
      status?: VisitRequestStatus;
      from?: string;
      to?: string;
      page?: number;
      size?: number;
    } = {},
  ) =>
    api
      .get<Page<VisitRequest>>("/notifications/visit-requests", {
        params: {
          status: params.status ?? "PENDING",
          from: params.from,
          to: params.to,
          page: params.page ?? 0,
          size: params.size ?? 20,
        },
      })
      .then((r) => r.data),

  byFlat: (flatId: string, page = 0, size = 20) =>
    api
      .get<Page<VisitRequest>>(
        `/notifications/visit-requests/flat/${flatId}`,
        { params: { page, size } },
      )
      .then((r) => r.data),

  byUser: (userId: string, page = 0, size = 20) =>
    api
      .get<Page<VisitRequest>>(
        `/notifications/visit-requests/user/${userId}`,
        { params: { page, size } },
      )
      .then((r) => r.data),

  respond: (id: string, body: RespondToVisitRequestBody) =>
    api
      .put<VisitRequest>(
        `/notifications/visit-requests/${id}/respond`,
        body,
      )
      .then((r) => r.data),

  pendingCount: () =>
    api
      .get<{ count: number }>("/notifications/visit-requests/count/pending")
      .then((r) => r.data.count),
};
