import { useQuery } from "@tanstack/react-query";
import { propertiesApi } from "@/lib/api/properties";
import type { FlatResponseDTO } from "@/types/api";

/**
 * Bulk-resolve a set of flat ids to their full Flat entities, cached
 * for 60 s. Used on payment lists / pay page so we can render
 * "Flat #A-302" instead of the raw UUID.
 *
 * <p>Single-flat usage is fine too — pass a one-element array.
 *
 * <p>Returns a {@code Map<string, FlatResponseDTO>} keyed by id, and a
 * {@link nameOf} convenience that hands back the formatted flat number
 * (or the id-prefix as a last-resort fallback).
 */
export function useFlatLookup(flatIds: string[]) {
  const dedup = Array.from(new Set(flatIds.filter(Boolean))).sort();

  const q = useQuery({
    queryKey: ["flat-lookup", dedup.join(",")],
    queryFn: async () => {
      // Per-id fetch is fine for typical pages (≤ 20 flats). When this
      // grows we'll add a batch endpoint.
      const results = await Promise.all(
        dedup.map((id) =>
          propertiesApi.flats
            .get(id)
            .then((f) => [id, f] as const)
            .catch(() => [id, null] as const),
        ),
      );
      const map = new Map<string, FlatResponseDTO>();
      for (const [id, f] of results) if (f) map.set(id, f);
      return map;
    },
    enabled: dedup.length > 0,
    staleTime: 60_000,
  });

  return {
    map: q.data ?? new Map<string, FlatResponseDTO>(),
    nameOf: (id?: string) => {
      if (!id) return "—";
      const f = q.data?.get(id);
      return f?.flatNumber ?? `#${id.slice(0, 8)}…`;
    },
    isLoading: q.isLoading,
  };
}
