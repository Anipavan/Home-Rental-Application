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

  /** Returns the rendered lease deed as a PDF Blob. */
  downloadDocument: (id: string) =>
    api
      .get<Blob>(`/properties/agreements/${id}/document`, { responseType: "blob" })
      .then((r) => r.data),

  /**
   * Upload the wet-signed, notary-stamped PDF back to the platform.
   * The agreement must already be SIGNED. Replaces any prior upload.
   */
  uploadSignedDeed: (id: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api
      .post<AgreementResponseDTO>(
        `/properties/agreements/${id}/signed-deed`,
        form,
        // Let axios + the browser set Content-Type with the boundary —
        // see documentsApi.upload for why a literal "multipart/form-data"
        // (no boundary) makes Spring reject the body as malformed.
        { headers: { "Content-Type": undefined } },
      )
      .then((r) => r.data);
  },

  /** Returns the previously uploaded notarized lease deed as a PDF Blob. */
  downloadSignedDeed: (id: string) =>
    api
      .get<Blob>(`/properties/agreements/${id}/signed-deed`, {
        responseType: "blob",
      })
      .then((r) => r.data),
};
