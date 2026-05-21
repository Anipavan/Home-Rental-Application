import { api } from "./client";
import type {
  InitiatePaymentRequest,
  InitiatePaymentResponse,
  PaymentResponse,
  VerifyPaymentRequest,
  VpaValidationResponse,
} from "@/types/api";

/**
 * Payment-gateway client. Mirrors the backend's PaymentGatewayController exactly.
 *
 *   POST /payments/initiate -> InitiatePaymentResponse
 *   POST /payments/verify   -> PaymentResponse  (returns the updated payment row)
 *
 * Flow shapes by method:
 *  - UPI:           response has upiIntentUrl. Client opens it; the server marks
 *                   the payment PAID via webhook. Frontend polls GET /payments/{id}
 *                   until status === "PAID".
 *  - CARD / NET_BANKING / WALLET: response has redirectUrl. Client redirects to
 *                   it; gateway redirects back to returnUrl with
 *                   gatewayOrderId/transactionId/signature query params, and the
 *                   client posts those to /verify.
 *  - BANK_TRANSFER: response has bank account details. Client shows them; backend
 *                   marks PAID once the transfer reconciles.
 */
export const paymentGateway = {
  initiate: (body: InitiatePaymentRequest) =>
    api
      .post<InitiatePaymentResponse>("/payments/initiate", body)
      .then((r) => r.data),

  verify: (body: VerifyPaymentRequest) =>
    api.post<PaymentResponse>("/payments/verify", body).then((r) => r.data),

  /**
   * Validate a UPI VPA against the active gateway. The response carries
   * the masked holder name (e.g. "ANIRUDH P****") when the VPA exists on
   * the UPI directory. Used by both the owner's bank-details form
   * (saved VPA) and the tenant's "Other UPI" flow.
   *
   * <p>Backed by Razorpay's /v1/payments/validate/vpa on real gateways;
   * the in-process MockPaymentGateway returns a deterministic stub name
   * derived from the local part of the VPA so dev flows can be tested
   * without external calls.
   */
  validateVpa: (vpa: string) =>
    api
      .get<VpaValidationResponse>("/payments/vpa/validate", {
        params: { vpa },
      })
      .then((r) => r.data),
};
