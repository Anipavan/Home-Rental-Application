import { useQuery } from "@tanstack/react-query";
import { usersApi } from "@/lib/api/users";
import type { UserResponseDto } from "@/types/api";

/**
 * Bulk-resolve a set of authUserIds to their User Service profiles, cached
 * for 60 s. Used on payment lists / activity feeds where we need to render
 * tenant names instead of opaque ids.
 *
 * <p>Failed lookups (404) are silently skipped — the {@link nameOf}
 * helper falls back to a short id slice so the row still renders.
 */
export function useUserLookup(authUserIds: string[]) {
  const dedup = Array.from(new Set(authUserIds.filter(Boolean))).sort();

  const q = useQuery({
    queryKey: ["user-lookup", dedup.join(",")],
    queryFn: async () => {
      const results = await Promise.all(
        dedup.map((id) =>
          usersApi
            .byAuthId(id)
            .then((u) => [id, u] as const)
            .catch(() => [id, null] as const),
        ),
      );
      const map = new Map<string, UserResponseDto>();
      for (const [id, u] of results) if (u) map.set(id, u);
      return map;
    },
    enabled: dedup.length > 0,
    staleTime: 60_000,
  });

  return {
    map: q.data ?? new Map<string, UserResponseDto>(),
    nameOf: (id?: string) => {
      if (!id) return "—";
      const u = q.data?.get(id);
      if (!u) return `User ${id.slice(0, 8)}…`;
      const name = `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim();
      return name || `User ${id.slice(0, 8)}…`;
    },
    isLoading: q.isLoading,
  };
}
