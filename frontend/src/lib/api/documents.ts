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
        headers: { "Content-Type": "multipart/form-data" },
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

  remove: (id: string) =>
    api.delete<void>(`/documents/${id}`).then((r) => r.data),
};
