import { api } from "./client";
import type {
  ResendVerificationRequest,
  VerifyEmailRequest,
  VerifyEmailResponse,
} from "@/types/api";

/**
 * Magic-link verification flow. Both endpoints are public (no JWT
 * required) — the token in the body IS the credential for verify,
 * resend is rate-limited server-side.
 */
export const emailVerificationApi = {
  verify: (token: string) =>
    api
      .post<VerifyEmailResponse>("/auth/verify-email", {
        token,
      } satisfies VerifyEmailRequest)
      .then((r) => r.data),

  resend: (email: string) =>
    api
      .post<{ message: string }>("/auth/resend-verification", {
        email,
      } satisfies ResendVerificationRequest)
      .then((r) => r.data),
};
