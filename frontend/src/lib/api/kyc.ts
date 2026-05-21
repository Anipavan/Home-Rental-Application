import { api } from "./client";
import type {
  DigiLockerAuthorizeRequest,
  DigiLockerAuthorizeResponse,
  DigiLockerCallbackRequest,
  InitiateKycRequest,
  KycReport,
  KycResponse,
  VerifyPanRequest,
} from "@/types/api";

/**
 * KYC Service (port 8092). Routes mounted under `/kyc/**` server-side and
 * exposed via the gateway at `/rentals/v1/kyc/**`.
 */
export const kycApi = {
  initiate: (userId: string, body: InitiateKycRequest) =>
    api.post<KycResponse>(`/kyc/initiate/${userId}`, body).then((r) => r.data),

  status: (userId: string) =>
    api.get<KycResponse>(`/kyc/status/${userId}`).then((r) => r.data),

  verifyPan: (body: VerifyPanRequest) =>
    api.post<KycResponse>("/kyc/verify-pan", body).then((r) => r.data),

  linkDigilocker: (body: { userId: string; authCode: string; redirectUri?: string }) =>
    api.post<KycResponse>("/kyc/digilocker/link", body).then((r) => r.data),

  report: (userId: string) =>
    api.get<KycReport>(`/kyc/report/${userId}`).then((r) => r.data),

  /**
   * Begin a DigiLocker OAuth flow. The response carries the authorize URL —
   * the page navigates the browser there. The `state` should be stashed
   * in sessionStorage so the callback page can defence-in-depth verify it
   * before posting back to /digilocker/callback.
   */
  digilockerAuthorize: (userId: string, body: DigiLockerAuthorizeRequest) =>
    api
      .post<DigiLockerAuthorizeResponse>(`/kyc/digilocker/authorize/${userId}`, body)
      .then((r) => r.data),

  /**
   * Complete a DigiLocker flow. Called by the /app/kyc/callback page with
   * the {@code code} + {@code state} extracted from the redirect URL.
   * Server-side this triggers the OAuth token exchange + eAadhaar fetch
   * and flips the record to VERIFIED.
   */
  digilockerCallback: (body: DigiLockerCallbackRequest) =>
    api.post<KycResponse>("/kyc/digilocker/callback", body).then((r) => r.data),
};
