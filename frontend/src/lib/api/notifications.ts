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
