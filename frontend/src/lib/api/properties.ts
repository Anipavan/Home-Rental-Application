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
    remove: (id: number | string) =>
      api
        .delete<FlatResponseDTO>(`/properties/flats/${id}`)
        .then((r) => r.data),
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
