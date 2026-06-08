import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Building2, ChevronRight, Plus } from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { propertiesApi } from "@/lib/api/properties";
import { useAuthStore } from "@/stores/auth-store";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";

/**
 * Owner / maintainer overview — every society they manage in one
 * place. Lets them jump to the per-building ledger, and points
 * buildings that don't have a society set up yet at the setup
 * wizard.
 */
export function OwnerSocietiesOverviewPage() {
  const { authUserId } = useAuthStore();

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const societiesQ = useQuery({
    queryKey: ["my-societies"],
    queryFn: () => societyApi.mine(),
  });

  const societyByBuilding = new Map(
    (societiesQ.data ?? []).map((s) => [s.buildingId, s]),
  );

  const isLoading = buildingsQ.isLoading || societiesQ.isLoading;

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Society"
        description="Manage common-area expenses + share read-only ledgers with residents."
      />

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Skeleton className="h-32 rounded-2xl" />
          <Skeleton className="h-32 rounded-2xl" />
        </div>
      ) : !buildingsQ.data?.length ? (
        <EmptyState
          variant="info"
          icon={Building2}
          title="No buildings yet"
          description="Add a building first — then you can set up its society."
          action={
            <Button asChild variant="gradient">
              <Link to="/owner/buildings">Go to buildings</Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {buildingsQ.data.map((b) => {
            const society = societyByBuilding.get(b.buildingId);
            return (
              <Card key={b.buildingId}>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-display font-semibold text-base truncate">
                        {b.buildingName}
                      </p>
                      <p className="text-xs text-muted-foreground mt-0.5 truncate">
                        {b.buildingCity ?? "—"}
                      </p>
                    </div>
                    {society ? (
                      <span className="rounded-full bg-success/15 text-success text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 shrink-0">
                        Active
                      </span>
                    ) : (
                      <span className="rounded-full bg-warning/15 text-warning text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 shrink-0">
                        Not set up
                      </span>
                    )}
                  </div>

                  {society && (
                    <p className="text-xs text-muted-foreground mt-3">
                      Default ₹{society.defaultPerFlatAmount}/flat · due day{" "}
                      {society.monthlyDueDay}
                    </p>
                  )}

                  <div className="mt-4">
                    <Button asChild variant="outline" size="sm">
                      <Link to={`/owner/buildings/${b.buildingId}/society`}>
                        {society ? (
                          <>
                            View Records <ChevronRight className="size-4" />
                          </>
                        ) : (
                          <>
                            <Plus className="size-4" /> Set up society
                          </>
                        )}
                      </Link>
                    </Button>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
