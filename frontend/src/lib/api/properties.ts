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
    uploadImage: (id: number | string, file: File) => {
      const fd = new FormData();
      fd.append("file", file);
      return api
        .post<PropertyImageResponseDTO>(
          `/properties/buildings/${id}/images`,
          fd,
          { headers: { "Content-Type": "multipart/form-data" } },
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
