import { useQuery } from "@tanstack/react-query";
import { usersApi } from "@/lib/api/users";
import { authApi } from "@/lib/api/auth";
import type { UserResponseDto } from "@/types/api";

/**
 * Look up a User Service profile by auth user id, with a 60 s cache so the
 * same id rendered multiple times on a page only triggers one fetch.
 *
 * <p><b>Two-tier lookup.</b> User Service holds the rich profile (name,
 * phone, address, KYC fields). For tenants registered before the
 * Auth → User Feign wiring landed, that row may simply not exist; in that
 * case we fall back to Auth Service via {@link authApi.lookupById} which
 * always has at minimum (userName, email, role). The fallback is enough to
 * render names + contact buttons in owner-side views without showing the
 * dreaded "Couldn't load tenant profile" message.
 *
 * <p>The returned shape is identical regardless of which tier resolved.
 */
export function useUserByAuth(authUserId?: string | null) {
  const q = useQuery({
    queryKey: ["user-by-auth", authUserId],
    queryFn: async () => {
      // Try the rich profile first.
      try {
        return await usersApi.byAuthId(authUserId!);
      } catch (e) {
        const status = (e as { response?: { status?: number } })?.response
          ?.status;
        if (status !== 404) throw e;
      }
      // 404 — fall back to auth-service for the bare-bones identity.
      try {
        const auth = await authApi.lookupById(authUserId!);
        // Synthesize a UserResponseDto-shaped object so callers don't need
        // to know which tier the data came from. firstName / lastName are
        // derived best-effort from userName.
        const parts = (auth.userName ?? "").split(/[\s._-]+/);
        const firstName = parts[0] ?? "";
        const lastName = parts.slice(1).join(" ") || "";
        return {
          id: 0,
          authUserId: auth.id,
          firstName,
          lastName,
          email: auth.email ?? "",
          role: (auth.userRole as UserResponseDto["role"]) ?? undefined,
        } as UserResponseDto;
      } catch (e) {
        // Both tiers failed — let the caller show whatever fallback it
        // wants (we surface this through isError).
        throw e;
      }
    },
    enabled: !!authUserId,
    staleTime: 60_000,
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 404 && failureCount < 2;
    },
  });

  const user = q.data;
  const fullName = user
    ? `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim()
    : null;

  return {
    user,
    fullName: fullName || null,
    isLoading: q.isLoading,
    isError: q.isError,
  };
}
