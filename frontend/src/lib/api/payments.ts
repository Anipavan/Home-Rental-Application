import { api } from "./client";
import type {
  CreatePaymentRequest,
  Page,
  PayCashRequest,
  PaymentResponse,
} from "@/types/api";

export const paymentsApi = {
  list: (page = 0, size = 20) =>
    api
      .get<Page<PaymentResponse>>("/payments", { params: { page, size } })
      .then((r) => r.data),
  get: (id: number | string) =>
    api.get<PaymentResponse>(`/payments/${id}`).then((r) => r.data),
  byTenant: (tenantId: string) =>
    api
      .get<PaymentResponse[]>(`/payments/tenant/${tenantId}`)
      .then((r) => r.data),
  byOwner: (ownerId: string) =>
    api
      .get<PaymentResponse[]>(`/payments/owner/${ownerId}`)
      .then((r) => r.data),
  overdue: () =>
    api.get<PaymentResponse[]>("/payments/overdue").then((r) => r.data),
  create: (body: CreatePaymentRequest) =>
    api.post<PaymentResponse>("/payments", body).then((r) => r.data),
  payCash: (id: number | string, body: PayCashRequest) =>
    api
      .post<PaymentResponse>(`/payments/${id}/pay-cash`, body)
      .then((r) => r.data),
  invoice: (id: number | string) =>
    api.get<{ pdfUrl?: string; invoiceNumber?: string }>(
      `/payments/${id}/invoice`,
    ).then((r) => r.data),
  receipt: (id: number | string) =>
    api.get<{ pdfUrl?: string; receiptNumber?: string }>(
      `/payments/${id}/receipt`,
    ).then((r) => r.data),
};
