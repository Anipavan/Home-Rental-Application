import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Building2, Plus, MapPin } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";

export function BuildingsPage() {
  const { authUserId } = useAuthStore();
  const q = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Buildings"
        description="Every property you manage."
        actions={
          <Button asChild variant="gradient">
            <Link to="/owner/buildings/new">
              <Plus /> Add building
            </Link>
          </Button>
        }
      />

      {q.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-44 rounded-2xl" />
          ))}
        </div>
      )}

      {!q.isLoading && (q.data?.length ?? 0) === 0 && (
        <Card className="p-12 text-center">
          <Building2 className="size-12 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            Add your first building
          </p>
          <p className="text-muted-foreground text-sm mt-1 max-w-sm mx-auto">
            Start by listing the building, then add the flats inside.
          </p>
          <Button asChild variant="gradient" className="mt-5">
            <Link to="/owner/buildings/new">
              <Plus /> Add building
            </Link>
          </Button>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {(q.data ?? []).map((b) => (
          <Link key={b.buildingId} to={`/owner/buildings/${b.buildingId}`}>
            <Card className="hover:shadow-lift transition-shadow group">
              <CardContent className="p-5">
                <div className="size-11 rounded-lg bg-primary/10 text-primary grid place-items-center mb-3">
                  <Building2 className="size-5" />
                </div>
                <h3 className="font-display font-semibold text-lg truncate">
                  {b.buildingName}
                </h3>
                <p className="text-xs text-muted-foreground flex items-center gap-1 mt-1">
                  <MapPin className="size-3" />
                  {b.buildingCity}, {b.buildingState}
                </p>
                <div className="mt-4 flex items-center gap-2">
                  <Badge variant="secondary">{b.activeFlatsCount ?? b.buildingTotalFlats ?? 0} flats</Badge>
                  <Badge variant="secondary">{b.buildingTotalFloors ?? 0} floors</Badge>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
