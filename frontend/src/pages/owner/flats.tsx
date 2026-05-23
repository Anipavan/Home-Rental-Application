import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Loader2, LogOut, Plus, Search, Trash2, UserPlus } from "lucide-react";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { AssignTenantDialog } from "@/components/owner/assign-tenant-dialog";
import { VacateFlatDialog } from "@/components/owner/vacate-flat-dialog";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
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

type FlatRow = import("@/types/api").FlatResponseDTO & { _buildingName?: string };

function FlatTable({
  flats,
  loading,
}: {
  flats: FlatRow[];
  loading?: boolean;
}) {
  // Mutable targets for the per-row Assign / Vacate / Delete dialogs.
  // Clicking a row button sets the right one; closing the dialog clears
  // it. Delete is destructive so it requires a confirmation step.
  const [assignTarget, setAssignTarget] = useState<FlatRow | null>(null);
  const [vacateTarget, setVacateTarget] = useState<FlatRow | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<FlatRow | null>(null);

  const qc = useQueryClient();
  const deleteM = useMutation({
    mutationFn: (flatId: string) => propertiesApi.flats.remove(flatId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-all-flats"] });
      qc.invalidateQueries({ queryKey: ["flats-by-building"] });
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      toast({
        title: "Flat deleted",
        description:
          "The unit has been removed from your portfolio. Past payments + agreements are preserved for audit.",
      });
      setDeleteTarget(null);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't delete the flat",
        description: extractErrorMessage(e),
      }),
  });

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
    <>
      <Card>
        {/* New "Action" column on the right for the Assign / status button. */}
        <div className="hidden sm:grid grid-cols-[80px_1fr_1fr_140px_110px_90px_120px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
          <span>Flat</span>
          <span>Building</span>
          <span>Layout</span>
          <span>Tenant</span>
          <span>Rent</span>
          <span>Status</span>
          <span className="text-right">Action</span>
        </div>
        <div className="divide-y">
          {flats.map((f) => (
            <div
              key={f.id}
              className="grid grid-cols-2 sm:grid-cols-[80px_1fr_1fr_140px_110px_90px_120px] gap-3 px-5 py-3.5 text-sm items-center"
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
              <div className="sm:text-right col-span-2 sm:col-span-1 flex items-center justify-end gap-1">
                {f.isOccupied ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    onClick={() => setVacateTarget(f)}
                  >
                    <LogOut /> Vacate
                  </Button>
                ) : (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setAssignTarget(f)}
                  >
                    <UserPlus /> Assign
                  </Button>
                )}
                {/* Delete is hidden for occupied flats — the tenant has
                    to be vacated first. Backend enforces this too
                    (returns 409 Conflict), but pre-hiding makes the
                    rule visible without needing to click + see an error. */}
                {!f.isOccupied && (
                  <Button
                    size="icon"
                    variant="ghost"
                    aria-label="Delete flat"
                    className="size-8 text-muted-foreground hover:text-destructive"
                    onClick={() => setDeleteTarget(f)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {assignTarget && (
        <AssignTenantDialog
          open={!!assignTarget}
          onOpenChange={(o) => {
            if (!o) setAssignTarget(null);
          }}
          flatId={assignTarget.id}
          flatNumber={assignTarget.flatNumber}
          buildingName={assignTarget._buildingName}
        />
      )}

      {vacateTarget && (
        <VacateFlatDialog
          open={!!vacateTarget}
          onOpenChange={(o) => {
            if (!o) setVacateTarget(null);
          }}
          flatId={vacateTarget.id}
          flatNumber={vacateTarget.flatNumber}
          buildingName={vacateTarget._buildingName}
          tenantId={vacateTarget.tenantId}
        />
      )}

      {/* Delete-confirmation dialog. Stays mounted while deleteTarget is
          non-null so the loading spinner stays visible during the API
          call. Closes via setDeleteTarget(null) on cancel or successful
          delete (handled in deleteM.onSuccess above). */}
      <Dialog
        open={!!deleteTarget}
        onOpenChange={(o) => {
          if (!o) setDeleteTarget(null);
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <Trash2 className="size-4" />
              Delete Flat {deleteTarget?.flatNumber}?
            </DialogTitle>
            <DialogDescription>
              This removes the unit from your portfolio. Past payments,
              agreements, and maintenance records are preserved for audit —
              only the listing itself is removed. This action can't be undone
              from the app.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => setDeleteTarget(null)}
              disabled={deleteM.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => deleteTarget && deleteM.mutate(deleteTarget.id)}
              disabled={deleteM.isPending}
            >
              {deleteM.isPending && (
                <Loader2 className="size-4 animate-spin" />
              )}
              <Trash2 className="size-4" /> Delete flat
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
