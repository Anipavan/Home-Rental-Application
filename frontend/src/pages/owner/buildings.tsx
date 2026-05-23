import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Building2, Loader2, MapPin, PencilLine, Plus, Trash2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/page-header";
import { PropertyImage } from "@/components/property/property-image";
import { EditBuildingDialog } from "@/components/owner/edit-building-dialog";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { BuildingResponseDTO } from "@/types/api";

export function BuildingsPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  /**
   * Delete target — when non-null, the confirmation dialog opens
   * showing the building's name + a "this can't be undone" warning.
   * The actual delete fires through `propertiesApi.buildings.remove`
   * which the backend soft-deletes (the row stays for audit; only
   * the active-list view hides it).
   */
  const [deleteTarget, setDeleteTarget] = useState<BuildingResponseDTO | null>(
    null,
  );
  /**
   * Edit target — when non-null, the EditBuildingDialog opens with the
   * building's current values pre-filled. Save calls
   * propertiesApi.buildings.update and invalidates the my-buildings
   * query so the card re-renders with the new data.
   */
  const [editTarget, setEditTarget] = useState<BuildingResponseDTO | null>(
    null,
  );

  const deleteM = useMutation({
    mutationFn: (buildingId: string) =>
      propertiesApi.buildings.remove(buildingId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      qc.invalidateQueries({ queryKey: ["owner-all-flats"] });
      toast({
        title: "Building deleted",
        description:
          "Removed from your portfolio. Flats inside the building have been deactivated; past records stay for audit.",
      });
      setDeleteTarget(null);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't delete the building",
        description: extractErrorMessage(e),
      }),
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
          <BuildingCard
            key={b.buildingId}
            building={b}
            onEdit={() => setEditTarget(b)}
            onDelete={() => setDeleteTarget(b)}
          />
        ))}
      </div>

      {/* Edit dialog — shared across every card. Mounted only when an
          edit target is selected so the form's useState defaults are
          reset on every open. */}
      {editTarget && (
        <EditBuildingDialog
          open={!!editTarget}
          onOpenChange={(o) => {
            if (!o) setEditTarget(null);
          }}
          building={editTarget}
        />
      )}

      {/* Single delete-confirmation dialog mounted at the page level —
          shared across every card so we don't need one per row. */}
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
              Delete {deleteTarget?.buildingName}?
            </DialogTitle>
            <DialogDescription>
              This removes the building and all its flats from your active
              portfolio. Past payments, agreements, and tenant records are
              preserved for audit. You can't undo this from the app.
              <br />
              <br />
              <strong className="text-foreground">
                Heads-up:
              </strong>{" "}
              if any flat in this building is currently occupied, the
              delete will be rejected — vacate active tenants first.
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
              onClick={() =>
                deleteTarget && deleteM.mutate(deleteTarget.buildingId)
              }
              disabled={deleteM.isPending}
            >
              {deleteM.isPending && <Loader2 className="size-4 animate-spin" />}
              <Trash2 className="size-4" /> Delete building
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/**
 * One building tile on the list page. Pulls the building's image list and
 * uses the first photo as the cover thumbnail. Shows a generic icon when
 * the owner hasn't uploaded any photos yet.
 *
 * <p>The card is wrapped in a Link to the detail page, so the new Delete
 * icon button has to stopPropagation + preventDefault on click so it
 * doesn't trigger the navigation.
 */
function BuildingCard({
  building: b,
  onEdit,
  onDelete,
}: {
  building: BuildingResponseDTO;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const imagesQ = useQuery({
    queryKey: ["building-images", b.buildingId],
    queryFn: () => propertiesApi.buildings.images(b.buildingId),
    enabled: !!b.buildingId,
    staleTime: 60_000,
  });
  const cover = imagesQ.data?.[0];

  return (
    <div className="relative group">
      {/* Edit + Delete icon buttons float on the cover image, top-right.
          Visible only on hover/focus so they don't clutter the default
          view. Both stop the Link navigation when clicked. */}
      <div className="absolute top-2 right-2 z-10 flex gap-1 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
        <Button
          size="icon"
          variant="ghost"
          aria-label={`Edit building ${b.buildingName}`}
          className="size-8 bg-background/80 backdrop-blur text-muted-foreground hover:text-primary hover:bg-background"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onEdit();
          }}
        >
          <PencilLine className="size-4" />
        </Button>
        <Button
          size="icon"
          variant="ghost"
          aria-label={`Delete building ${b.buildingName}`}
          className="size-8 bg-background/80 backdrop-blur text-muted-foreground hover:text-destructive hover:bg-background"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onDelete();
          }}
        >
          <Trash2 className="size-4" />
        </Button>
      </div>

      <Link to={`/owner/buildings/${b.buildingId}`}>
        <Card className="hover:shadow-lift transition-shadow group overflow-hidden">
          <div className="aspect-[16/9] bg-muted overflow-hidden">
            {cover ? (
              <PropertyImage
                imageId={cover.id}
                alt={b.buildingName}
                className="w-full h-full"
              />
            ) : (
              <div className="w-full h-full grid place-items-center text-muted-foreground bg-gradient-to-br from-secondary/40 to-secondary/10">
                <Building2 className="size-8" />
              </div>
            )}
          </div>
          <CardContent className="p-5">
            <h3 className="font-display font-semibold text-lg truncate">
              {b.buildingName}
            </h3>
            <p className="text-xs text-muted-foreground flex items-center gap-1 mt-1">
              <MapPin className="size-3" />
              {b.buildingCity}, {b.buildingState}
            </p>
            <div className="mt-4 flex items-center gap-2">
              <Badge variant="secondary">
                {b.activeFlatsCount ?? b.buildingTotalFlats ?? 0} flats
              </Badge>
              <Badge variant="secondary">
                {b.buildingTotalFloors ?? 0} floors
              </Badge>
            </div>
          </CardContent>
        </Card>
      </Link>
    </div>
  );
}
