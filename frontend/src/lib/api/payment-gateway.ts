import { api } from "./client";
import type {
  InitiatePaymentRequest,
  InitiatePaymentResponse,
  PaymentResponse,
  VerifyPaymentRequest,
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
};
