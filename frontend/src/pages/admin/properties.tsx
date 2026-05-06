import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Building2 } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { formatINR } from "@/lib/utils";

export function AdminPropertiesPage() {
  const [q, setQ] = useState("");

  const buildingsQ = useQuery({
    queryKey: ["admin", "buildings"],
    queryFn: () => propertiesApi.buildings.list(0, 200),
  });
  const flatsQ = useQuery({
    queryKey: ["admin", "flats"],
    queryFn: () => propertiesApi.flats.list(0, 200),
  });

  const buildings = buildingsQ.data?.content ?? [];
  const flats = flatsQ.data?.content ?? [];

  const filteredBuildings = useMemo(
    () =>
      !q
        ? buildings
        : buildings.filter(
            (b) =>
              b.buildingName.toLowerCase().includes(q.toLowerCase()) ||
              b.buildingCity.toLowerCase().includes(q.toLowerCase()) ||
              b.ownerId.toLowerCase().includes(q.toLowerCase()),
          ),
    [buildings, q],
  );

  const filteredFlats = useMemo(
    () =>
      !q
        ? flats
        : flats.filter(
            (f) =>
              f.flatNumber.toLowerCase().includes(q.toLowerCase()) ||
              String(f.buildingId).includes(q),
          ),
    [flats, q],
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Properties"
        description="Every building and flat on the platform."
      />

      <Card className="p-3 mb-5">
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            placeholder="Search building, city, owner, flat number…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-10"
          />
        </div>
      </Card>

      <Tabs defaultValue="buildings">
        <TabsList>
          <TabsTrigger value="buildings">
            Buildings ({buildings.length})
          </TabsTrigger>
          <TabsTrigger value="flats">Flats ({flats.length})</TabsTrigger>
        </TabsList>

        <TabsContent value="buildings">
          {buildingsQ.isLoading && (
            <Card className="p-3 space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            </Card>
          )}
          {!buildingsQ.isLoading && filteredBuildings.length === 0 && (
            <Card className="p-12 text-center text-muted-foreground">
              No buildings match.
            </Card>
          )}
          {!buildingsQ.isLoading && filteredBuildings.length > 0 && (
            <Card>
              <div className="hidden sm:grid grid-cols-[1.5fr_1fr_140px_100px_120px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
                <span>Building</span>
                <span>Location</span>
                <span>Owner</span>
                <span>Flats</span>
                <span>Floors</span>
              </div>
              <div className="divide-y">
                {filteredBuildings.map((b) => (
                  <div
                    key={b.buildingId}
                    className="grid grid-cols-2 sm:grid-cols-[1.5fr_1fr_140px_100px_120px] gap-3 px-5 py-3.5 text-sm items-center"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="size-9 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
                        <Building2 className="size-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="font-medium truncate">{b.buildingName}</p>
                        <p className="text-xs text-muted-foreground truncate">
                          {b.buildingAddress}
                        </p>
                      </div>
                    </div>
                    <span className="text-muted-foreground truncate hidden sm:block">
                      {b.buildingCity}, {b.buildingState}
                    </span>
                    <span className="font-mono text-xs truncate text-muted-foreground hidden sm:block">
                      {b.ownerId}
                    </span>
                    <Badge variant="secondary">{b.activeFlatsCount ?? b.buildingTotalFlats ?? 0}</Badge>
                    <span className="text-muted-foreground hidden sm:block">
                      {b.buildingTotalFloors ?? "—"}
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="flats">
          {flatsQ.isLoading && (
            <Card className="p-3 space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            </Card>
          )}
          {!flatsQ.isLoading && filteredFlats.length > 0 && (
            <Card>
              <div className="hidden sm:grid grid-cols-[80px_1fr_100px_140px_120px_100px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
                <span>Flat</span>
                <span>Building</span>
                <span>Layout</span>
                <span>Tenant</span>
                <span>Rent</span>
                <span>Status</span>
              </div>
              <div className="divide-y">
                {filteredFlats.map((f) => (
                  <div
                    key={f.id}
                    className="grid grid-cols-2 sm:grid-cols-[80px_1fr_100px_140px_120px_100px] gap-3 px-5 py-3.5 text-sm items-center"
                  >
                    <span className="font-mono font-semibold">
                      {f.flatNumber}
                    </span>
                    <span className="text-muted-foreground">
                      #{f.buildingId}
                    </span>
                    <span className="text-muted-foreground hidden sm:block">
                      {f.bedrooms ?? 2}BHK
                    </span>
                    <span className="text-muted-foreground truncate hidden sm:block">
                      {f.tenantId ?? "—"}
                    </span>
                    <span className="font-medium">
                      {formatINR(f.rentAmount)}
                    </span>
                    <Badge variant={f.isOccupied ? "secondary" : "success"}>
                      {f.isOccupied ? "Occupied" : "Vacant"}
                    </Badge>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
