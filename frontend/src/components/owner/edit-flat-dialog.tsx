import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, PencilLine } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { FlatResponseDTO } from "@/types/api";

/**
 * Edit-in-place dialog for a flat's core attributes. The building it
 * belongs to is immutable post-create — moving a flat between buildings
 * doesn't model anything real and would orphan past payments /
 * agreements. To move a unit, delete and recreate.
 *
 * <p>Tenant-preference toggles (acceptsBachelor / acceptsFamily) are
 * shown as checkboxes — same defaults as the create form (both ON).
 */
export function EditFlatDialog({
  open,
  onOpenChange,
  flat,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  flat: FlatResponseDTO;
}) {
  const qc = useQueryClient();

  // Mirror the editable fields. Initial values come from the prop;
  // reset on each open so a closed-mid-edit doesn't keep stale state.
  const [flatNumber, setFlatNumber] = useState(flat.flatNumber ?? "");
  const [floor, setFloor] = useState<string>(String(flat.floor ?? 0));
  const [bedrooms, setBedrooms] = useState<string>(String(flat.bedrooms ?? 1));
  const [bathrooms, setBathrooms] = useState<string>(
    String(flat.bathrooms ?? 1),
  );
  const [areaSqft, setAreaSqft] = useState<string>(String(flat.areaSqft ?? ""));
  const [rentAmount, setRentAmount] = useState<string>(
    String(flat.rentAmount ?? ""),
  );
  // acceptsBachelor / acceptsFamily default to true on legacy rows
  // (the listing filter excludes a flat only when the owner has
  // explicitly turned it off). Mirror that here.
  const [acceptsBachelor, setAcceptsBachelor] = useState<boolean>(
    flat.acceptsBachelor !== false,
  );
  const [acceptsFamily, setAcceptsFamily] = useState<boolean>(
    flat.acceptsFamily !== false,
  );
  // V10: "listed for rent" toggle. Default FALSE on legacy rows
  // (server-side default for backfilled flats) — the owner has to
  // explicitly opt in for the flat to appear on the public browse.
  const [availableForRent, setAvailableForRent] = useState<boolean>(
    flat.availableForRent === true,
  );

  useEffect(() => {
    if (open) {
      setFlatNumber(flat.flatNumber ?? "");
      setFloor(String(flat.floor ?? 0));
      setBedrooms(String(flat.bedrooms ?? 1));
      setBathrooms(String(flat.bathrooms ?? 1));
      setAreaSqft(String(flat.areaSqft ?? ""));
      setRentAmount(String(flat.rentAmount ?? ""));
      setAcceptsBachelor(flat.acceptsBachelor !== false);
      setAcceptsFamily(flat.acceptsFamily !== false);
      setAvailableForRent(flat.availableForRent === true);
    }
  }, [open, flat]);

  const updateM = useMutation({
    mutationFn: () =>
      propertiesApi.flats.update(flat.id, {
        buildingId: flat.buildingId,
        flatNumber: flatNumber.trim(),
        floor: Number(floor) || 0,
        bedrooms: Number(bedrooms) || 1,
        bathrooms: Number(bathrooms) || 1,
        areaSqft: Number(areaSqft) || 0,
        rentAmount: Number(rentAmount) || 0,
        acceptsBachelor,
        acceptsFamily,
        availableForRent,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-all-flats"] });
      qc.invalidateQueries({ queryKey: ["flats-by-building"] });
      qc.invalidateQueries({ queryKey: ["flat", flat.id] });
      toast({ title: "Flat updated" });
      onOpenChange(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't save changes",
        description: extractErrorMessage(e),
      }),
  });

  // Block submission when both tenant-type toggles are off — the
  // listing would be visible to nobody (every filter would exclude it).
  // Same pathological-combo guard the create form uses.
  const tenantTypeValid = acceptsBachelor || acceptsFamily;

  const dirty =
    flatNumber.trim() !== (flat.flatNumber ?? "") ||
    Number(floor) !== (flat.floor ?? 0) ||
    Number(bedrooms) !== (flat.bedrooms ?? 1) ||
    Number(bathrooms) !== (flat.bathrooms ?? 1) ||
    Number(areaSqft) !== (flat.areaSqft ?? 0) ||
    Number(rentAmount) !== (flat.rentAmount ?? 0) ||
    acceptsBachelor !== (flat.acceptsBachelor !== false) ||
    acceptsFamily !== (flat.acceptsFamily !== false) ||
    availableForRent !== (flat.availableForRent === true);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <PencilLine className="size-4 text-primary" />
            Edit flat {flat.flatNumber}
          </DialogTitle>
          <DialogDescription>
            Update the unit's listing attributes. The building it
            belongs to is locked — to move the unit, delete and recreate
            it in the target building.
          </DialogDescription>
        </DialogHeader>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!tenantTypeValid) {
              toast({
                variant: "destructive",
                title: "At least one tenant type required",
                description:
                  "Allow bachelors, families, or both — otherwise the listing is invisible to every tenant filter.",
              });
              return;
            }
            updateM.mutate();
          }}
          className="space-y-4"
        >
          <div className="grid sm:grid-cols-2 gap-3">
            <div>
              <Label htmlFor="ef-number">Flat number</Label>
              <Input
                id="ef-number"
                value={flatNumber}
                onChange={(e) => setFlatNumber(e.target.value)}
                className="mt-1.5 font-mono"
                required
                maxLength={20}
              />
            </div>
            <div>
              <Label htmlFor="ef-floor">Floor</Label>
              <Input
                id="ef-floor"
                type="number"
                min={0}
                value={floor}
                onChange={(e) => setFloor(e.target.value)}
                className="mt-1.5"
                required
              />
            </div>
          </div>

          <div className="grid sm:grid-cols-3 gap-3">
            <div>
              <Label htmlFor="ef-beds">Bedrooms</Label>
              <Input
                id="ef-beds"
                type="number"
                min={1}
                max={10}
                value={bedrooms}
                onChange={(e) => setBedrooms(e.target.value)}
                className="mt-1.5"
                required
              />
            </div>
            <div>
              <Label htmlFor="ef-baths">Bathrooms</Label>
              <Input
                id="ef-baths"
                type="number"
                min={1}
                max={10}
                value={bathrooms}
                onChange={(e) => setBathrooms(e.target.value)}
                className="mt-1.5"
                required
              />
            </div>
            <div>
              <Label htmlFor="ef-area">Area (sqft)</Label>
              <Input
                id="ef-area"
                type="number"
                min={100}
                value={areaSqft}
                onChange={(e) => setAreaSqft(e.target.value)}
                className="mt-1.5"
                required
              />
            </div>
          </div>

          <div>
            <Label htmlFor="ef-rent">Monthly rent (₹)</Label>
            <Input
              id="ef-rent"
              type="number"
              min={1000}
              step={500}
              value={rentAmount}
              onChange={(e) => setRentAmount(e.target.value)}
              className="mt-1.5"
              required
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              Security deposit auto-derived as 3× the rent on payment
              creation. No need to enter it separately.
            </p>
          </div>

          {/* Tenant-preference toggles — same shape as the create form. */}
          <div>
            <Label>Open to</Label>
            <div className="mt-1.5 flex gap-4">
              <label className="flex items-center gap-2 text-sm cursor-pointer select-none">
                <input
                  type="checkbox"
                  className="size-4 rounded border-border accent-emerald-600"
                  checked={acceptsBachelor}
                  onChange={(e) => setAcceptsBachelor(e.target.checked)}
                />
                Bachelors
              </label>
              <label className="flex items-center gap-2 text-sm cursor-pointer select-none">
                <input
                  type="checkbox"
                  className="size-4 rounded border-border accent-emerald-600"
                  checked={acceptsFamily}
                  onChange={(e) => setAcceptsFamily(e.target.checked)}
                />
                Families
              </label>
            </div>
            {!tenantTypeValid && (
              <p className="text-xs text-destructive mt-1.5">
                Pick at least one — the listing must be visible to some
                tenant filter.
              </p>
            )}
          </div>

          {/* V10: Listed-for-rent toggle. When this is OFF, the flat
              is hidden from the public Browse Homes page entirely —
              even if it's vacant. Use OFF for owner-occupied flats,
              flats under renovation, or units you don't want
              advertised yet. */}
          <div className="rounded-lg border border-primary/30 bg-primary/5 p-3">
            <label className="flex items-start gap-3 cursor-pointer select-none">
              <input
                type="checkbox"
                className="mt-0.5 size-4 rounded border-border accent-primary"
                checked={availableForRent}
                onChange={(e) => setAvailableForRent(e.target.checked)}
              />
              <div className="flex-1">
                <span className="font-semibold text-sm">
                  Listed for rent
                </span>
                <p className="text-[11px] text-muted-foreground mt-0.5">
                  {availableForRent
                    ? "This flat appears on the public Browse Homes page. Tenants can apply to rent it."
                    : "Hidden from the public Browse Homes page. Switch on when you're ready to accept rental applications."}
                </p>
              </div>
            </label>
          </div>

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={updateM.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="gradient"
              disabled={!dirty || !tenantTypeValid || updateM.isPending}
            >
              {updateM.isPending && <Loader2 className="size-4 animate-spin" />}
              Save changes
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
