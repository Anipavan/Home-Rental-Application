import { api } from "./client";
import type {
  AssignFlatRequest,
  BuildingRequestDTO,
  BuildingResponseDTO,
  FlatRequestDTO,
  FlatResponseDTO,
  Page,
  PropertyImageResponseDTO,
} from "@/types/api";

export const propertiesApi = {
  buildings: {
    list: (page = 0, size = 20) =>
      api
        .get<Page<BuildingResponseDTO>>("/properties/buildings", {
          params: { page, size },
        })
        .then((r) => r.data),
    get: (id: number | string) =>
      api
        .get<BuildingResponseDTO>(`/properties/buildings/${id}`)
        .then((r) => r.data),
    byOwner: (ownerId: string) =>
      api
        .get<BuildingResponseDTO[]>(`/properties/buildings/owner/${ownerId}`)
        .then((r) => r.data),
    /**
     * Case-insensitive search on buildingName / buildingAddress / city /
     * state. Optionally scoped to a single owner. Used by the global
     * search bar in the app shell.
     */
    search: (q: string, ownerId?: string, limit = 8) =>
      api
        .get<BuildingResponseDTO[]>("/properties/buildings/search", {
          params: { q, ownerId, limit },
        })
        .then((r) => r.data),
    create: (body: BuildingRequestDTO) =>
      api
        .post<BuildingResponseDTO>(
          "/properties/buildings/create/building",
          body,
        )
        .then((r) => r.data),
    update: (id: number | string, body: BuildingRequestDTO) =>
      api
        .put<BuildingResponseDTO>(
          `/properties/buildings/${id}/building`,
          body,
        )
        .then((r) => r.data),
    remove: (id: number | string) =>
      api
        .delete<BuildingResponseDTO>(`/properties/buildings/${id}`)
        .then((r) => r.data),
    images: (id: number | string) =>
      api
        .get<PropertyImageResponseDTO[]>(`/properties/buildings/${id}/images`)
        .then((r) => r.data),
    /**
     * Fetch the raw bytes of a stored property image as a Blob. The DB row's
     * {@code imageUrl} is a server-side filesystem path the browser can't
     * load directly, so we go through this streaming endpoint instead.
     */
    imageRaw: (imageId: string) =>
      api
        .get<Blob>(`/properties/images/${imageId}/raw`, { responseType: "blob" })
        .then((r) => r.data),
    /**
     * Loadable URL for an <img src=…>. The /images/*\/raw endpoint is
     * in the gateway's GET public-paths list, so anonymous browsers
     * can render the image without a JWT (and without the Blob
     * round-trip imageRaw() does — that one is for download links).
     */
    imageRawUrl: (imageId: string) => {
      const base =
        (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
        "http://localhost:8080/rentals/v1";
      return `${base.replace(/\/$/, "")}/properties/images/${imageId}/raw`;
    },
    /** Hard-delete a property image (removes DB row + on-disk file). */
    deleteImage: (imageId: string) =>
      api.delete(`/properties/images/${imageId}`).then((r) => r.data),
    uploadImage: (id: number | string, file: File) => {
      const fd = new FormData();
      fd.append("file", file);
      return api
        .post<PropertyImageResponseDTO>(
          `/properties/buildings/${id}/images`,
          fd,
          // axios + the browser handle the boundary; see documentsApi.upload.
          { headers: { "Content-Type": undefined } },
        )
        .then((r) => r.data);
    },
    /**
     * Bulk upload — owners drag many photos onto the gallery editor
     * at once. Each file is validated server-side (content type, size)
     * the same way the single-file endpoint validates. The response
     * preserves upload order so the FE can update the gallery view
     * without a refetch.
     */
    uploadImagesBulk: (id: number | string, files: File[]) => {
      const fd = new FormData();
      for (const f of files) fd.append("files", f);
      return api
        .post<PropertyImageResponseDTO[]>(
          `/properties/buildings/${id}/images/bulk`,
          fd,
          { headers: { "Content-Type": undefined } },
        )
        .then((r) => r.data);
    },
    /**
     * Promote {@code imageId} to be the cover for its property. The
     * server atomically unsets the previous cover so we never need
     * a follow-up "unset". Idempotent.
     */
    setCover: (imageId: string) =>
      api
        .put<PropertyImageResponseDTO>(`/properties/images/${imageId}/cover`)
        .then((r) => r.data),
    /** Drag-reorder. Body is the new ordered list of image ids. */
    reorderImages: (buildingId: string, orderedIds: string[]) =>
      api
        .put<PropertyImageResponseDTO[]>(
          `/properties/buildings/${buildingId}/images/reorder`,
          orderedIds,
        )
        .then((r) => r.data),
  },
  flats: {
    list: (page = 0, size = 20) =>
      api
        .get<Page<FlatResponseDTO>>("/properties/flats", {
          params: { page, size },
        })
        .then((r) => r.data),
    get: (id: number | string) =>
      api.get<FlatResponseDTO>(`/properties/flats/${id}`).then((r) => r.data),
    byBuilding: (buildingId: number | string) =>
      api
        .get<FlatResponseDTO[]>(`/properties/flats/building/${buildingId}`)
        .then((r) => r.data),
    byTenant: (tenantId: string) =>
      api
        .get<FlatResponseDTO[]>(`/properties/flats/tenant/${tenantId}`)
        .then((r) => r.data),
    vacant: () =>
      api.get<FlatResponseDTO[]>("/properties/flats/vacant").then((r) => r.data),
    /**
     * Public preview of a flat by (buildingId, flatNumber). Returns
     * only `{exists, occupied}` — no flatId, no tenantId, no PII —
     * so it's safe to call without a JWT. Used by the maintainer-
     * signup form to verify, BEFORE /auth/register fires, that the
     * applicant actually lives in the building they're applying to
     * manage. Backend re-validates the same check in
     * MembershipClaimServiceImpl.createClaim as defence-in-depth.
     */
    preview: (buildingId: string, flatNumber: string) =>
      api
        .get<{ exists: boolean; occupied: boolean }>(
          "/properties/flats/preview",
          { params: { buildingId, flatNumber } },
        )
        .then((r) => r.data),
    /**
     * Haversine geosearch — vacant flats whose parent building's
     * geo-pin is within {@code radiusKm} of (lat, lng). Pin-less
     * buildings are excluded by the backend. Public GET, callable
     * without sign-in via the gateway's open property routes.
     */
    near: (lat: number, lng: number, radiusKm = 5) =>
      api
        .get<FlatResponseDTO[]>("/properties/flats/near", {
          params: { lat, lng, radiusKm },
        })
        .then((r) => r.data),
    create: (body: FlatRequestDTO) =>
      api
        .post<FlatResponseDTO>("/properties/flats/create/flat", body)
        .then((r) => r.data),
    update: (id: number | string, body: FlatRequestDTO) =>
      api
        .put<FlatResponseDTO>(`/properties/flats/${id}`, body)
        .then((r) => r.data),
    assign: (flatId: number | string, body: AssignFlatRequest) =>
      api
        .post<FlatResponseDTO>(`/properties/flats/${flatId}/assign`, body)
        .then((r) => r.data),
    vacate: (flatId: number | string) =>
      api
        .post<FlatResponseDTO>(`/properties/flats/${flatId}/vacate`)
        .then((r) => r.data),
    /**
     * Soft-delete a flat. Backend rejects when the flat is currently
     * occupied (tenant must be vacated first) — surfaces as a 409
     * Conflict the caller should render with a friendly message.
     */
    remove: (flatId: number | string) =>
      api
        .delete<FlatResponseDTO>(`/properties/flats/${flatId}`)
        .then((r) => r.data),
    /**
     * Tenant-initiated scheduled vacate (Issue #5 + Issue #4).
     *
     * Tenant picks a move-out date (Issue #4 spec) — backend
     * validates that it's at least 60 days from today AND that no
     * outstanding rent invoice exists for the flat. Returns the
     * updated flat with scheduledVacateDate set.
     *
     * @param effectiveDate ISO date string (YYYY-MM-DD) — the
     *   move-out date the tenant picked. Must be at least 60 days
     *   from today; backend rejects with 400 otherwise.
     */
    scheduleVacate: (
      flatId: number | string,
      effectiveDate: string,
      comments?: string,
    ) =>
      api
        .post<FlatResponseDTO>(
          `/properties/flats/${flatId}/schedule-vacate`,
          null,
          {
            params: {
              effectiveDate,
              // Only include comments param if non-empty to keep the
              // querystring tidy; backend treats absent === null.
              ...(comments && comments.trim()
                ? { comments: comments.trim() }
                : {}),
            },
          },
        )
        .then((r) => r.data),
    /** Cancel a previously-scheduled tenant vacate. */
    cancelScheduledVacate: (flatId: number | string) =>
      api
        .post<FlatResponseDTO>(
          `/properties/flats/${flatId}/schedule-vacate/cancel`,
        )
        .then((r) => r.data),
    // Note: flats.remove is defined above (immediately after vacate())
    // — don't redeclare it here. TS errors out with "duplicate property
    // in object literal" when both versions exist.
  },
};

/**
 * Wishlist API — the "save for later" feature on browse / property
 * detail pages. Backend keys favourites on the gateway-supplied
 * X-Auth-User-Id header so we never have to pass userId from the FE.
 *
 *  - {@link add} / {@link remove} are idempotent — calling add twice
 *    or remove on a never-saved flat both succeed silently. Lets the
 *    heart-toggle stay optimistic without a "have I saved this?"
 *    preflight.
 *  - {@link ids} is the cheap projection that powers the heart-icon
 *    filled-vs-outlined state on every flat card. The full
 *    {@link list} hydrates the saved flats for the /app/saved page.
 */
export const favoritesApi = {
  add: (flatId: string) =>
    api.post(`/properties/favorites/${flatId}`).then((r) => r.data),
  remove: (flatId: string) =>
    api.delete(`/properties/favorites/${flatId}`).then((r) => r.data),
  list: () =>
    api
      .get<FlatResponseDTO[]>("/properties/favorites")
      .then((r) => r.data),
  ids: () =>
    api.get<string[]>("/properties/favorites/ids").then((r) => r.data),
};

/**
 * Saved-search alerts API — tenants store a filter combination
 * ("2BHK under ₹30k in Indiranagar") and get an email the moment a
 * matching flat is listed. Like the wishlist, the user identity is
 * taken from the gateway's X-Auth-User-Id header, so the client never
 * sends userId.
 */
export interface SavedSearchRequest {
  name?: string | null;
  city?: string | null;
  bedrooms?: number | null;
  minRent?: number | null;
  maxRent?: number | null;
  minAreaSqft?: number | null;
  furnishingStatus?: string | null;
  petFriendly?: boolean | null;
  isActive?: boolean | null;
}

export interface SavedSearchResponse {
  id: string;
  userId: string;
  name: string | null;
  city: string | null;
  bedrooms: number | null;
  minRent: number | null;
  maxRent: number | null;
  minAreaSqft: number | null;
  furnishingStatus: string | null;
  petFriendly: boolean | null;
  isActive: boolean;
  lastMatchedAt: string | null;
  createdAt: string;
}

export const savedSearchesApi = {
  create: (body: SavedSearchRequest) =>
    api
      .post<SavedSearchResponse>("/properties/saved-searches", body)
      .then((r) => r.data),
  list: () =>
    api
      .get<SavedSearchResponse[]>("/properties/saved-searches")
      .then((r) => r.data),
  update: (id: string, body: SavedSearchRequest) =>
    api
      .put<SavedSearchResponse>(`/properties/saved-searches/${id}`, body)
      .then((r) => r.data),
  remove: (id: string) =>
    api
      .delete<void>(`/properties/saved-searches/${id}`)
      .then((r) => r.data),
};
