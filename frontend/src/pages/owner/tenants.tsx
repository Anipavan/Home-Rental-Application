import { useQuery } from "@tanstack/react-query";
import { Users, Mail, Phone } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/layout/page-header";
import { formatDate, formatINR, initials } from "@/lib/utils";

export function TenantsPage() {
  const { authUserId } = useAuthStore();

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const flatsQ = useQuery({
    queryKey: ["owner-all-flats", buildingsQ.data?.map((b) => b.buildingId).join(",")],
    queryFn: async () => {
      const buildings = buildingsQ.data ?? [];
      const all = await Promise.all(
        buildings.map((b) =>
          propertiesApi.flats
            .byBuilding(b.buildingId)
            .then((flats) => flats.map((f) => ({ ...f, _buildingName: b.buildingName }))),
        ),
      );
      return all.flat();
    },
    enabled: !!buildingsQ.data,
  });

  const tenantedFlats = (flatsQ.data ?? []).filter((f) => f.tenantId);

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Tenants"
        description="People living in your homes."
      />

      {flatsQ.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-44 rounded-2xl" />
          ))}
        </div>
      )}

      {!flatsQ.isLoading && tenantedFlats.length === 0 && (
        <Card className="p-12 text-center">
          <Users className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            No tenants yet.
          </p>
          <p className="text-muted-foreground text-sm mt-1">
            Once you assign tenants to flats, they'll show up here.
          </p>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tenantedFlats.map((f) => (
          <Card key={f.id}>
            <CardContent className="p-5">
              <div className="flex items-center gap-3">
                <Avatar className="size-12">
                  <AvatarFallback>{initials(f.tenantId ?? "")}</AvatarFallback>
                </Avatar>
                <div className="min-w-0">
                  <p className="font-semibold truncate">
                    Tenant {f.tenantId}
                  </p>
                  <p className="text-xs text-muted-foreground truncate">
                    {(f as { _buildingName?: string })._buildingName} · {f.flatNumber}
                  </p>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
                <div>
                  <p className="text-muted-foreground">Rent</p>
                  <p className="font-semibold mt-0.5">{formatINR(f.rentAmount)}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">Lease ends</p>
                  <p className="font-semibold mt-0.5">{formatDate(f.leaseEndDate)}</p>
                </div>
              </div>
              <div className="mt-4 flex items-center gap-2">
                <button className="size-8 rounded-md bg-secondary hover:bg-primary/10 hover:text-primary grid place-items-center text-muted-foreground transition-colors">
                  <Mail className="size-4" />
                </button>
                <button className="size-8 rounded-md bg-secondary hover:bg-primary/10 hover:text-primary grid place-items-center text-muted-foreground transition-colors">
                  <Phone className="size-4" />
                </button>
                <Badge variant="success" className="ml-auto">
                  Active
                </Badge>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
