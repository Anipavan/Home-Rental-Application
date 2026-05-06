import { api } from "./client";
import type {
  NotificationResponse,
  SendNotificationRequest,
} from "@/types/api";

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
};
