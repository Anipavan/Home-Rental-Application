import axios from "axios";
import type {
  InitiatePaymentResponse,
  InitiateRegistrationPaymentRequest,
  RegistrationPaymentResultResponse,
  VerifyRegistrationPaymentRequest,
} from "@/types/api";

/**
 * Paid maintainer-registration payment client. Hits the
 * /payments/registration/{initiate,verify} endpoints directly with a
 * REG_PAY-token Authorization header — we deliberately avoid the
 * shared `api` axios instance (lib/api/client.ts) because that
 * instance auto-attaches the user's access token from the auth store,
 * and the user is NOT logged in yet at this stage. A separate axios
 * instance keeps the REG_PAY token contained to these two calls.
 */

const BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  "http://localhost:8080/rentals/v1";

function tokenedClient(paymentToken: string) {
  return axios.create({
    baseURL: BASE_URL,
    withCredentials: false,
    headers: {
      "Content-Type": "application/json",
      "ngrok-skip-browser-warning": "true",
      Authorization: `Bearer ${paymentToken}`,
    },
  });
}

export const registrationPaymentApi = {
  /**
   * Open a Razorpay order for the maintainer-registration fee. The
   * shape returned matches the standard /payments/initiate response
   * — gatewayOrderId + redirectUrl + (optionally) UPI intent URL.
   */
  initiate: (paymentToken: string, body: InitiateRegistrationPaymentRequest) =>
    tokenedClient(paymentToken)
      .post<InitiatePaymentResponse>("/payments/registration/initiate", body)
      .then((r) => r.data),

  /**
   * Confirm the Razorpay-side payment. On success the user's auth row
   * flips to enabled and they can sign in normally.
   */
  verify: (paymentToken: string, body: VerifyRegistrationPaymentRequest) =>
    tokenedClient(paymentToken)
      .post<RegistrationPaymentResultResponse>(
        "/payments/registration/verify",
        body,
      )
      .then((r) => r.data),
};
