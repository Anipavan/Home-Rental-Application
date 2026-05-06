import { api } from "./client";
import type {
  AgreementResponseDTO,
  RejectAgreementRequest,
  SignAgreementRequest,
} from "@/types/api";

export const agreementsApi = {
  get: (id: string) =>
    api
      .get<AgreementResponseDTO>(`/properties/agreements/${id}`)
      .then((r) => r.data),

  byTenant: (tenantId: string) =>
    api
      .get<AgreementResponseDTO[]>(`/properties/agreements/tenant/${tenantId}`)
      .then((r) => r.data),

  byOwner: (ownerId: string) =>
    api
      .get<AgreementResponseDTO[]>(`/properties/agreements/owner/${ownerId}`)
      .then((r) => r.data),

  byFlat: (flatId: string) =>
    api
      .get<AgreementResponseDTO[]>(`/properties/agreements/flat/${flatId}`)
      .then((r) => r.data),

  sign: (id: string, body: SignAgreementRequest) =>
    api
      .post<AgreementResponseDTO>(`/properties/agreements/${id}/sign`, body)
      .then((r) => r.data),

  reject: (id: string, body: RejectAgreementRequest) =>
    api
      .post<AgreementResponseDTO>(`/properties/agreements/${id}/reject`, body)
      .then((r) => r.data),
};
