import { api } from "./client";
import type {
  DocumentResponse,
  DocumentType,
  ExtractedData,
  PreSignedUrl,
} from "@/types/api";

/**
 * Document Service (port 8091). Mounted at `/documents/**` server-side,
 * exposed via the gateway at `/rentals/v1/documents/**`.
 */
export const documentsApi = {
  upload: (userId: string, documentType: DocumentType, file: File) => {
    const fd = new FormData();
    fd.append("userId", userId);
    fd.append("documentType", documentType);
    fd.append("file", file);
    return api
      .post<DocumentResponse>("/documents/upload", fd, {
        // IMPORTANT: leave Content-Type unset (we even force-undefined
        // to override the axios-instance default of application/json).
        // axios + the browser then auto-set
        //   `multipart/form-data; boundary=----WebKitFormBoundary...`
        // for FormData bodies — the boundary parameter is what Spring's
        // multipart parser keys off. Setting Content-Type to a literal
        // "multipart/form-data" (no boundary) is the textbook bug that
        // makes Spring reject the body as malformed.
        headers: { "Content-Type": undefined },
      })
      .then((r) => r.data);
  },

  getById: (id: string) =>
    api.get<DocumentResponse>(`/documents/${id}`).then((r) => r.data),

  byUser: (userId: string) =>
    api
      .get<DocumentResponse[]>(`/documents/user/${userId}`)
      .then((r) => r.data),

  getDownloadUrl: (id: string) =>
    api
      .get<PreSignedUrl>(`/documents/${id}/download`)
      .then((r) => r.data),

  extract: (id: string) =>
    api
      .post<DocumentResponse>(`/documents/${id}/extract`)
      .then((r) => r.data),

  getExtracted: (id: string) =>
    api
      .get<ExtractedData>(`/documents/${id}/extracted-data`)
      .then((r) => r.data),

  verify: (id: string, verifiedBy: string, fraudFlag = false) =>
    api
      .post<DocumentResponse>(`/documents/${id}/verify`, null, {
        params: { verifiedBy, fraudFlag },
      })
      .then((r) => r.data),

  /**
   * Issue #9 — owner approves a tenant-uploaded document. Sets
   * verificationStatus=APPROVED, fires document.approved event so
   * the tenant gets a notification fanned out across their channels.
   */
  approve: (id: string, ownerId: string) =>
    api
      .post<DocumentResponse>(`/documents/${id}/approve`, null, {
        params: { ownerId },
      })
      .then((r) => r.data),

  /**
   * Issue #9 — owner rejects a tenant-uploaded document with a
   * free-text reason. The reason is required (max 500 chars) and
   * is surfaced verbatim in the tenant's notification + on their
   * documents tab.
   */
  reject: (id: string, ownerId: string, reason: string) =>
    api
      .post<DocumentResponse>(
        `/documents/${id}/reject`,
        { reason },
        { params: { ownerId } },
      )
      .then((r) => r.data),

  remove: (id: string) =>
    api.delete<void>(`/documents/${id}`).then((r) => r.data),
};
