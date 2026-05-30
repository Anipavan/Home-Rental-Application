import { api } from "./client";
import type {
  CreatePaymentRequest,
  Page,
  PayCashRequest,
  PaymentResponse,
} from "@/types/api";

/**
 * Owner's payout details for a single invoice — what the tenant
 * needs to pay rent DIRECTLY to the owner via UPI or bank transfer.
 * The platform never touches the money; this is just the look-up
 * the tenant payment page renders into a QR + bank-details panel.
 * Once the tenant has paid out-of-band, the OWNER comes back and
 * marks the payment as received via {@link paymentsApi.markUpiReceived}
 * or the existing cash flow.
 */
export interface PayoutDetailsResponse {
  paymentId: string;
  amount: number;
  invoiceReference: string;
  payeeName: string | null;
  ownerId: string | null;
  upiVpa: string | null;
  upiQrPayload: string | null;
  bankName: string | null;
  accountNumberMasked: string | null;
  ifscCode: string | null;
  branch: string | null;
  accountType: string | null;
  /** True when the owner hasn't saved any payment details yet. */
  ownerPayoutMissing: boolean;
}

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

  /**
   * Public landing-page stat: lifetime rupee sum across every settled
   * (PAID) payment. No auth required (whitelisted at the gateway).
   * Returns 0 when no payments have settled — the caller (landing
   * page) hides the tile rather than display ₹0.
   */
  publicLifetimeStats: () =>
    api
      .get<{ totalCollectedRupees: number }>("/payments/stats/public/lifetime")
      .then((r) => r.data),
  create: (body: CreatePaymentRequest) =>
    api.post<PaymentResponse>("/payments", body).then((r) => r.data),
  payCash: (id: number | string, body: PayCashRequest) =>
    api
      .post<PaymentResponse>(`/payments/${id}/pay-cash`, body)
      .then((r) => r.data),
  /**
   * Owner confirms an out-of-band UPI / NEFT / IMPS payment. Same
   * shape as payCash — actor is the owner, money never touched the
   * platform. Reference is typically the UPI reference number the
   * owner copies from their bank SMS / app.
   */
  markUpiReceived: (id: number | string, body: PayCashRequest) =>
    api
      .post<PaymentResponse>(`/payments/${id}/mark-upi-received`, body)
      .then((r) => r.data),
  /**
   * Owner's UPI VPA + QR deep link + bank fallback for an invoice.
   * Read by the tenant payment page to render the "scan & pay"
   * UI. Returns ownerPayoutMissing=true when the owner hasn't
   * saved any payment details yet.
   */
  payoutDetails: (id: number | string) =>
    api
      .get<PayoutDetailsResponse>(`/payments/${id}/payout-details`)
      .then((r) => r.data),
  invoice: (id: number | string) =>
    api.get<{ pdfUrl?: string; invoiceNumber?: string }>(
      `/payments/${id}/invoice`,
    ).then((r) => r.data),
  receipt: (id: number | string) =>
    api.get<{ pdfUrl?: string; receiptNumber?: string }>(
      `/payments/${id}/receipt`,
    ).then((r) => r.data),
  /**
   * Fetch a freshly-rendered PDF (invoice or receipt) for a payment.
   * Returns a Blob the caller can hand to a saveAs / link download.
   */
  invoicePdf: (id: number | string) =>
    api
      .get<Blob>(`/payments/${id}/invoice.pdf`, { responseType: "blob" })
      .then((r) => r.data),
  receiptPdf: (id: number | string) =>
    api
      .get<Blob>(`/payments/${id}/receipt.pdf`, { responseType: "blob" })
      .then((r) => r.data),
};
