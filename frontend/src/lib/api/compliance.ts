import { api } from "./client";
import type {
  GenerateGstInvoiceRequest,
  GstInvoice,
  ReraRegisterRequest,
  ReraRegistration,
} from "@/types/api";

/**
 * Compliance Service (port 8093). Mounted at `/compliance/**` server-side;
 * gateway route is `/rentals/v1/compliance/**`.
 */
export const complianceApi = {
  // ---- RERA ----
  registerRera: (body: ReraRegisterRequest) =>
    api
      .post<ReraRegistration>("/compliance/rera/register", body)
      .then((r) => r.data),

  reraStatus: (propertyId: string) =>
    api
      .get<ReraRegistration[]>(`/compliance/rera/status/${propertyId}`)
      .then((r) => r.data),

  generateReraLeaseMetadata: (
    leaseId: string,
    body: { leaseId: string; propertyId: string; state: string },
  ) =>
    api
      .post<{ leaseId: string; reraMetadata: string }>(
        `/compliance/lease/generate-rera/${leaseId}`,
        body,
      )
      .then((r) => r.data),

  // ---- GST ----
  generateGstInvoice: (paymentId: string, body: GenerateGstInvoiceRequest) =>
    api
      .post<GstInvoice>(`/compliance/gst/generate/${paymentId}`, body)
      .then((r) => r.data),

  getGstInvoice: (id: string) =>
    api.get<GstInvoice>(`/compliance/gst/invoice/${id}`).then((r) => r.data),

  /** PDF blob — wire to a download anchor. */
  downloadGstInvoicePdf: (id: string) =>
    api
      .get<Blob>(`/compliance/gst/invoice/${id}/pdf`, {
        responseType: "blob",
      })
      .then((r) => r.data),
};
