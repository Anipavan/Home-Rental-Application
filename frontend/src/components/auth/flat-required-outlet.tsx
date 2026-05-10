import { useEffect, useRef } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useTenantHasFlat } from "@/hooks/use-tenant-has-flat";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "@/hooks/use-toast";

/**
 * Route-level gate for tenant features that require a flat assignment.
 * Wrap a route's {@code element} with this component to redirect
 * unassigned tenants back to the overview, with a toast explaining why.
 *
 * <p>The AppShell already intercepts nav-link clicks so nav-driven
 * navigation never hits this guard; this is the safety net for direct
 * URL typing, programmatic {@code navigate(…)}, deep-linked
 * notifications, etc. Keeping both layers means a new restricted route
 * just needs to be added to {@code TENANT_FLAT_REQUIRED_PATHS} (for the
 * click intercept) and wrapped in {@code <FlatRequiredOutlet>} (for the
 * route guard).
 *
 * <p>Toast is fired from a {@link useEffect} rather than render so
 * StrictMode's double-render in dev doesn't trigger the toast twice. A
 * ref tracks whether we've already shown the toast for this redirect
 * so re-renders during the transition don't repeat it either.
 */
export function FlatRequiredOutlet() {
  const { hasFlat, isLoading } = useTenantHasFlat();
  const location = useLocation();
  const toastedRef = useRef(false);

  const blocked = !isLoading && hasFlat === false;

  useEffect(() => {
    if (blocked && !toastedRef.current) {
      toastedRef.current = true;
      toast({
        variant: "destructive",
        title: "Flat not assigned",
        description: "To avail this feature a flat must be assigned.",
      });
    }
  }, [blocked]);

  if (isLoading) {
    return <Skeleton className="h-64 rounded-2xl" />;
  }
  if (blocked) {
    // `state` lets the redirect target read where the user came from
    // if it ever wants to deep-link back here once a flat is assigned.
    return <Navigate to="/app" replace state={{ blockedFrom: location.pathname }} />;
  }
  return <Outlet />;
}
