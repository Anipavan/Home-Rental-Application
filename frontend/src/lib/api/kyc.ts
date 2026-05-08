import { api } from "./client";
import type {
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
};
