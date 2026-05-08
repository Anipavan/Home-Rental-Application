import { api } from "./client";
import type { RefCityDto, RefStateDto } from "@/types/api";

/**
 * Reference data lookups for the Add Building cascade. Mounted server-side
 * at `/properties/reference/**` (gateway: `/rentals/v1/properties/reference/**`).
 *
 * Data is seeded once in the property-service from a CSV at boot —
 * see `ReferenceDataSeeder`.
 */
export const referenceApi = {
  states: () =>
    api
      .get<RefStateDto[]>("/properties/reference/states")
      .then((r) => r.data),

  cities: (stateId: number) =>
    api
      .get<RefCityDto[]>("/properties/reference/cities", {
        params: { stateId },
      })
      .then((r) => r.data),

  searchCities: (q: string) =>
    api
      .get<RefCityDto[]>("/properties/reference/cities/search", {
        params: { q },
      })
      .then((r) => r.data),
};
