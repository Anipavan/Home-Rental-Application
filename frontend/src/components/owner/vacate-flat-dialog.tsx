import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, LogOut, AlertTriangle } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/**
 * Owner-side confirmation dialog for vacating a tenant from a flat.
 *
 * <p>{@code POST /properties/flats/{id}/vacate} clears the assignment,
 * fires the {@code flat.vacated} Kafka event (downstream services
 * close out leases, payments, etc.), and the property-service backend
 * additionally enforces a 2-month minimum occupancy — if the lease
 * started less than 2 months ago, the call returns a 400 with a
 * specific message that we surface verbatim. We invalidate the same
 * caches as the assign flow so the UI flips back to "Vacant" + the
 * tenant's gated routes lock again on their next page load.
 */
interface Props {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  flatId: string;
  flatNumber: string;
  buildingName?: string;
  /** Current tenant authUserId so we can show who's being moved out. */
  tenantId?: string | null;
}

export function VacateFlatDialog({
  open,
  onOpenChange,
  flatId,
  flatNumber,
  buildingName,
  tenantId,
}: Props) {
  const qc = useQueryClient();

  const vacateM = useMutation({
    mutationFn: () => propertiesApi.flats.vacate(flatId),
    onSuccess: () => {
      // Same invalidation set the assign dialog uses — every cache that
      // surfaces flat occupancy / lease state needs to drop.
      qc.invalidateQueries({ queryKey: ["owner-all-flats"] });
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      qc.invalidateQueries({ queryKey: ["building"] });
      qc.invalidateQueries({ queryKey: ["flats-by-building"] });
      qc.invalidateQueries({ queryKey: ["flat", flatId] });
      qc.invalidateQueries({ queryKey: ["my-flats"] });
      qc.invalidateQueries({ queryKey: ["agreements"] });
      qc.invalidateQueries({ queryKey: ["my-agreements"] });
      toast({
        title: "Tenant vacated",
        description:
          "The flat is back on the vacant list. The tenant's gated routes are locked again.",
      });
      onOpenChange(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't vacate",
        description: extractErrorMessage(e),
      }),
  });

  const propertyLabel = buildingName
    ? `${buildingName} · Flat ${flatNumber}`
    : `Flat ${flatNumber}`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <LogOut className="size-4 text-destructive" /> Vacate this flat?
          </DialogTitle>
          <DialogDescription>
            You're about to move the current tenant out of{" "}
            <span className="font-medium">{propertyLabel}</span>. This is
            usually irreversible from the UI — re-assigning a flat creates a
            new agreement / lease.
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-xl border bg-warning/10 border-warning/40 p-4 flex items-start gap-3">
          <AlertTriangle className="size-5 text-warning shrink-0 mt-0.5" />
          <div className="text-sm space-y-1.5">
            <p className="font-medium">What happens next</p>
            <ul className="text-muted-foreground space-y-1">
              <li>
                • Flat is marked vacant; {tenantId ? `tenant ${tenantId}` : "the tenant"} is detached.
              </li>
              <li>• Lease + agreement state moves to terminated downstream.</li>
              <li>
                • Tenant's gated tabs (Lease, Payments, Maintenance,
                Documents…) re-lock on their next page load.
              </li>
              <li>
                • Property-service enforces a 2-month minimum occupancy —
                if the lease started less than 2 months ago you'll see a
                clear error.
              </li>
            </ul>
          </div>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={vacateM.isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={() => vacateM.mutate()}
            disabled={vacateM.isPending}
          >
            {vacateM.isPending && (
              <Loader2 className="size-4 animate-spin" />
            )}
            <LogOut /> Vacate flat
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
