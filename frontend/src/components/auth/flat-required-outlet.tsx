import { Link, Outlet } from "react-router-dom";
import { Lock } from "lucide-react";
import { useTenantHasFlat } from "@/hooks/use-tenant-has-flat";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

/**
 * Route-level gate for tenant features that need a flat assignment.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Tenant has a flat → renders the page normally.</li>
 *   <li>Tenant has no flat → still navigates to the route (so the URL
 *       reflects what the user clicked) but renders the page content
 *       greyed-out and non-interactive, with a centered message panel
 *       overlaying the page area: <b>Flat not assigned · To avail this
 *       feature a flat must be assigned.</b></li>
 *   <li>Lookup in flight → renders a skeleton while we resolve.</li>
 * </ul>
 *
 * <p>Why render the page at all behind the overlay rather than
 * substituting a stub: it preserves the navigation — the URL bar
 * shows the route the user intended to visit, the active nav item
 * highlights, and once a flat is assigned the page becomes
 * immediately usable on a refresh. The {@code pointer-events-none}
 * + {@code opacity} treatment prevents the user from interacting
 * with the underlying components while still hinting at what's
 * behind the locked door.
 *
 * <p>Children pages may fire their own queries when mounted under
 * this guard. That's fine — they're built to handle empty results
 * (a no-flat tenant has no agreements / payments / maintenance
 * tickets), and {@code pointer-events-none} stops the user from
 * triggering side-effecting actions while blocked.
 */
export function FlatRequiredOutlet() {
  const { hasFlat, isLoading } = useTenantHasFlat();

  if (isLoading) {
    return <Skeleton className="h-64 rounded-2xl" />;
  }

  if (hasFlat === false) {
    return (
      <div className="relative min-h-[60vh]">
        {/* The page itself, ghosted out and inert. aria-hidden so screen
            readers skip the dead content and jump straight to the
            message panel below. */}
        <div
          className="opacity-40 pointer-events-none select-none blur-[1px]"
          aria-hidden="true"
        >
          <Outlet />
        </div>

        {/* Overlay panel — semi-transparent backdrop + a centered card
            with the message and a CTA back to My Home. */}
        <div className="absolute inset-0 flex items-center justify-center bg-background/60 backdrop-blur-sm">
          <Card className="max-w-md w-[min(28rem,90%)] border-dashed">
            <CardContent className="p-8 text-center">
              <div className="size-14 rounded-full bg-warning/15 grid place-items-center mx-auto">
                <Lock className="size-6 text-warning" />
              </div>
              <h2 className="font-display text-xl font-semibold mt-4">
                Flat not assigned
              </h2>
              <p className="text-muted-foreground mt-2">
                To avail this feature a flat must be assigned.
              </p>
              <Button asChild variant="gradient" className="mt-5">
                <Link to="/app/my-flat">Go to My Home</Link>
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  return <Outlet />;
}
