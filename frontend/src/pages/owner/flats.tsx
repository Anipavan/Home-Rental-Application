import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Plus, Search } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { formatINR } from "@/lib/utils";

export function FlatsPage() {
  const { authUserId } = useAuthStore();
  const [q, setQ] = useState("");

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  // Aggregate all flats across owner's buildings
  const flatsQ = useQuery({
    queryKey: ["owner-all-flats", buildingsQ.data?.map((b) => b.buildingId).join(",")],
    queryFn: async () => {
      const buildings = buildingsQ.data ?? [];
      const all = await Promise.all(
        buildings.map((b) =>
          propertiesApi.flats.byBuilding(b.buildingId).then((flats) =>
            flats.map((f) => ({ ...f, _buildingName: b.buildingName })),
          ),
        ),
      );
      return all.flat();
    },
    enabled: !!buildingsQ.data,
  });

  const flats = useMemo(() => {
    let list = flatsQ.data ?? [];
    if (q) {
      const n = q.toLowerCase();
      list = list.filter(
        (f) =>
          f.flatNumber.toLowerCase().includes(n) ||
          (f as { _buildingName?: string })._buildingName?.toLowerCase().includes(n),
      );
    }
    return list;
  }, [flatsQ.data, q]);

  const occupied = flats.filter((f) => f.isOccupied);
  const vacant = flats.filter((f) => !f.isOccupied);

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Flats"
        description="Every unit, across every building you own."
        actions={
          <Button asChild variant="gradient">
            <Link to="/owner/flats/new">
              <Plus /> Add flat
            </Link>
          </Button>
        }
      />

      <Card className="p-3 mb-5">
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            placeholder="Search by flat number or building"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-10"
          />
        </div>
      </Card>

      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All ({flats.length})</TabsTrigger>
          <TabsTrigger value="occupied">Occupied ({occupied.length})</TabsTrigger>
          <TabsTrigger value="vacant">Vacant ({vacant.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="all">
          <FlatTable flats={flats} loading={flatsQ.isLoading} />
        </TabsContent>
        <TabsContent value="occupied">
          <FlatTable flats={occupied} loading={flatsQ.isLoading} />
        </TabsContent>
        <TabsContent value="vacant">
          <FlatTable flats={vacant} loading={flatsQ.isLoading} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function FlatTable({
  flats,
  loading,
}: {
  flats: (import("@/types/api").FlatResponseDTO & { _buildingName?: string })[];
  loading?: boolean;
}) {
  if (loading) {
    return (
      <Card className="p-3 space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </Card>
    );
  }
  if (flats.length === 0) {
    return (
      <Card className="p-12 text-center text-muted-foreground">
        No flats here yet.
      </Card>
    );
  }
  return (
    <Card>
      <div className="hidden sm:grid grid-cols-[80px_1fr_1fr_140px_120px_100px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
        <span>Flat</span>
        <span>Building</span>
        <span>Layout</span>
        <span>Tenant</span>
        <span>Rent</span>
        <span>Status</span>
      </div>
      <div className="divide-y">
        {flats.map((f) => (
          <div
            key={f.id}
            className="grid grid-cols-2 sm:grid-cols-[80px_1fr_1fr_140px_120px_100px] gap-3 px-5 py-3.5 text-sm items-center"
          >
            <span className="font-mono font-semibold">{f.flatNumber}</span>
            <span className="truncate">{f._buildingName ?? "—"}</span>
            <span className="text-muted-foreground">
              {f.bedrooms ?? 2}BHK · {f.areaSqft ?? "—"} sqft
            </span>
            <span className="text-muted-foreground truncate">
              {f.tenantId ?? "—"}
            </span>
            <span className="font-medium">{formatINR(f.rentAmount)}</span>
            <Badge variant={f.isOccupied ? "secondary" : "success"}>
              {f.isOccupied ? "Occupied" : "Vacant"}
            </Badge>
          </div>
        ))}
      </div>
    </Card>
  );
}
