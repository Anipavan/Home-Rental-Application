import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Heart, Scale, Search } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { favoritesApi } from "@/lib/api/properties";
import { PropertyCard } from "@/components/property/property-card";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";

/**
 * The tenant's wishlist — every flat they've hit the heart on, newest
 * save first. Lives at /app/saved.
 *
 * <p>The list is server-side; we don't shadow-save to localStorage
 * for anonymous users because the comparison + saved-search features
 * planned on top of this need real persistence.
 *
 * <p>Empty-state CTA points to /browse so a brand-new tenant lands
 * here from the sidebar and immediately gets a useful next step.
 */
export function SavedListingsPage() {
  const { isAuthenticated } = useAuthStore();
  const q = useQuery({
    queryKey: ["favorites", "list"],
    queryFn: () => favoritesApi.list(),
    enabled: isAuthenticated,
    staleTime: 30_000,
  });

  const items = q.data ?? [];

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Saved homes"
        description="Every listing you've added to your wishlist, in one place."
        actions={
          items.length >= 2 && (
            <Button asChild variant="outline" size="sm">
              {/* Comparison page accepts up to 3 ids in the URL.
                  We pre-fill with the most-recent 3 saves — the user
                  can drop/add columns once they're on /compare. */}
              <Link
                to={`/app/compare?ids=${items
                  .slice(0, 3)
                  .map((f) => f.id)
                  .join(",")}`}
              >
                <Scale /> Compare top {Math.min(items.length, 3)}
              </Link>
            </Button>
          )
        }
      />

      {q.isLoading && (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Card key={i} className="overflow-hidden">
              <Skeleton className="aspect-[4/3] w-full rounded-none" />
              <div className="p-4 space-y-3">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/2" />
                <Skeleton className="h-5 w-1/3" />
              </div>
            </Card>
          ))}
        </div>
      )}

      {!q.isLoading && items.length === 0 && (
        <Card className="p-12 text-center">
          <div className="size-14 rounded-full bg-primary/10 grid place-items-center mx-auto">
            <Heart className="size-6 text-primary" />
          </div>
          <p className="font-display font-semibold text-lg mt-4">
            No saved homes yet
          </p>
          <p className="text-muted-foreground text-sm mt-1 max-w-sm mx-auto">
            Tap the heart icon on any listing to save it here. Your
            wishlist syncs across every device you sign in on.
          </p>
          <Button asChild variant="gradient" className="mt-5">
            <Link to="/browse">
              <Search /> Browse homes
            </Link>
          </Button>
        </Card>
      )}

      {!q.isLoading && items.length > 0 && (
        <>
          <p className="text-sm text-muted-foreground mb-4">
            <span className="font-semibold text-foreground">
              {items.length}
            </span>{" "}
            saved {items.length === 1 ? "home" : "homes"}
          </p>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((flat) => (
              <PropertyCard
                key={flat.id}
                flat={flat}
                buildingName={flat.buildingName}
                city={flat.buildingCity}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
