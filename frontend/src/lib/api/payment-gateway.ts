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

  /**
   * Check whether an owner is ready to receive Cashfree Easy Split
   * payouts. Backed by GET /payments/vendors/{userId}/payout-ready
   * which returns {@code { ready: boolean }} and never 404s.
   *
   * <p>Called by the tenant Pay page: {@code true} → offer the
   * Cashfree Checkout flow; {@code false} → fall back to the
   * direct-UPI QR (owner hasn't been onboarded to Easy Split yet).
   *
   * <p>Deliberately network-optimistic — treats any error as
   * "not ready" so a payment-service outage keeps tenants on the
   * always-working direct-UPI path rather than blocking payment.
   */
  isOwnerPayoutReady: (ownerId: string) =>
    api
      .get<{ ready: boolean }>(`/payments/vendors/${ownerId}/payout-ready`)
      .then((r) => r.data?.ready === true)
      .catch(() => false),

  /**
   * Sibling of {@link isOwnerPayoutReady} for the SOCIETY-scoped
   * Cashfree vendor. Used by the tenant maintenance flow to decide
   * whether Pay buttons are enabled — checks whether the society
   * (keyed on buildingId) has an ACTIVE Cashfree vendor registered
   * from its own bank + KYC fields (distinct from the maintainer
   * user's personal vendor used for rent).
   *
   * <p>Same network-optimistic contract: any error → treated as
   * "not ready", so a payment-service outage keeps tenants on the
   * always-working direct-UPI QR path rather than blocking payment.
   */
  isSocietyPayoutReady: (buildingId: string) =>
    api
      .get<{ ready: boolean }>(`/payments/vendors/society/${buildingId}/payout-ready`)
      .then((r) => r.data?.ready === true)
      .catch(() => false),
};
