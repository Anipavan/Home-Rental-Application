import { api } from "./client";
import type {
  MaintainerPaymentStatusResponse,
  RegisterPendingResponse,
} from "@/types/api";

/**
 * Logged-in maintainer-payment endpoints. The dashboard gate polls
 * {@link getStatus} on mount and every 5 minutes; the prompt modal
 * fires {@link skip} or {@link initiate} on user action.
 */
export const maintainerPaymentApi = {
  getStatus: () =>
    api
      .get<MaintainerPaymentStatusResponse>("/auth/me/payment-status")
      .then((r) => r.data),

  skip: () =>
    api
      .post<MaintainerPaymentStatusResponse>("/auth/me/payment-skip")
      .then((r) => r.data),

  initiate: () =>
    api
      .post<RegisterPendingResponse>("/auth/me/payment/initiate")
      .then((r) => r.data),
};
