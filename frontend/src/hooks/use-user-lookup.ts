import { useQuery } from "@tanstack/react-query";
import { usersApi } from "@/lib/api/users";
import { authApi } from "@/lib/api/auth";
import type { UserResponseDto } from "@/types/api";

/**
 * Bulk-resolve a set of authUserIds to their User Service profiles, cached
 * for 60 s. Used on payment lists / activity feeds where we need to render
 * tenant names instead of opaque ids.
 *
 * <p>Two-tier lookup, mirroring {@link useUserByAuth}: try User Service
 * first (rich profile), fall back to Auth Service (userName / email) on
 * 404 so legacy tenants without a User row still render with a real name.
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
            .catch(async (e) => {
              const status = (e as { response?: { status?: number } })?.response
                ?.status;
              if (status !== 404) return [id, null] as const;
              // Auth-service fallback — at minimum gives us userName + email.
              try {
                const auth = await authApi.lookupById(id);
                const parts = (auth.userName ?? "").split(/[\s._-]+/);
                const firstName = parts[0] ?? "";
                const lastName = parts.slice(1).join(" ") || "";
                const synthesized = {
                  id: 0,
                  authUserId: auth.id,
                  firstName,
                  lastName,
                  email: auth.email ?? "",
                  role: (auth.userRole as UserResponseDto["role"]) ?? undefined,
                } as UserResponseDto;
                return [id, synthesized] as const;
              } catch {
                return [id, null] as const;
              }
            }),
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
