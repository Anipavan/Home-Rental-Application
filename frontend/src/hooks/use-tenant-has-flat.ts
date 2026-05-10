import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";

/**
 * Returns whether the currently logged-in tenant has at least one flat
 * assigned. Powers the "no flat → restricted feature" gate that the
 * AppShell + FlatRequiredOutlet enforce.
 *
 * <p>Result shape:
 * <ul>
 *   <li>{@code hasFlat = true}  — at least one assigned flat, or the
 *       caller isn't a tenant (gate is a no-op for OWNER/ADMIN).</li>
 *   <li>{@code hasFlat = false} — tenant with zero flats: gate ON.</li>
 *   <li>{@code hasFlat = undefined} — still loading.</li>
 * </ul>
 *
 * <p>The query key matches {@code MyFlatPage}'s
 * {@code ["my-flats", authUserId]} so the result is shared with that
 * page — assigning a flat from the owner side and returning to the
 * tenant view will lift the gate without a manual refresh.
 */
export function useTenantHasFlat() {
  const { authUserId, role } = useAuthStore();
  const enabled = !!authUserId && role === "TENANT";

  const q = useQuery({
    queryKey: ["my-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId!),
    enabled,
    staleTime: 60_000,
  });

  // Non-tenant roles never see the gate.
  if (!enabled) {
    return { hasFlat: true as const, isLoading: false };
  }
  return {
    hasFlat: q.data ? q.data.length > 0 : undefined,
    isLoading: q.isLoading,
  };
}

/**
 * Tenant nav paths that require a flat. The AppShell click-handler and
 * the FlatRequiredOutlet route wrapper both consult this set, so changing
 * the gate's coverage is a one-line edit here.
 *
 * <p>Allowed without a flat: Overview ({@code /app}), My Home
 * ({@code /app/my-flat}), Profile ({@code /app/profile}). Everything
 * else is gated.
 */
export const TENANT_FLAT_REQUIRED_PATHS: ReadonlySet<string> = new Set([
  "/app/lease",
  "/app/payments",
  "/app/maintenance",
  "/app/kyc",
  "/app/documents",
  "/app/reviews",
]);

/** True when {@code path} (or one of its subpaths) needs a flat. */
export function isFlatRequiredPath(path: string): boolean {
  for (const p of TENANT_FLAT_REQUIRED_PATHS) {
    if (path === p || path.startsWith(p + "/")) return true;
  }
  return false;
}
