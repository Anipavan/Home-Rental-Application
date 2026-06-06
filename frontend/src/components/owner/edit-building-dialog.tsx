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
import { Textarea } from "@/components/ui/textarea";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { BuildingResponseDTO } from "@/types/api";

/**
 * Edit-in-place dialog for a building's core attributes. Image
 * management and add/remove flat operations stay on the building
 * detail page — this dialog handles only the metadata an owner
 * typically wants to tweak after creation (name typo, amenity list
 * fixes, geo-pin update).
 *
 * <p>Why a Dialog instead of a separate /edit route?
 * <ul>
 *   <li>One round-trip — open, save, close. No nav jump that loses
 *       the user's scroll position on the list.</li>
 *   <li>The create form's StateCitySelect, image gallery, and bulk
 *       upload UX are heavy and unneeded for a simple edit pass.</li>
 *   <li>State + city are immutable post-create (the listing's
 *       location stamp shouldn't drift) — so the form is small.</li>
 * </ul>
 */
export function EditBuildingDialog({
  open,
  onOpenChange,
  building,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  building: BuildingResponseDTO;
}) {
  const qc = useQueryClient();

  // Form state mirrors the editable fields. Initialized from the
  // building prop and reset every time the dialog re-opens so a
  // mid-edit close doesn't leave stale values for the next open.
  const [buildingName, setBuildingName] = useState(building.buildingName ?? "");
  const [buildingAddress, setBuildingAddress] = useState(
    building.buildingAddress ?? "",
  );
  const [totalFloors, setTotalFloors] = useState<string>(
    String(building.buildingTotalFloors ?? ""),
  );
  const [totalFlats, setTotalFlats] = useState<string>(
    String(building.buildingTotalFlats ?? ""),
  );
  const [amenities, setAmenities] = useState(building.amenities ?? "");
  const [latitude, setLatitude] = useState<string>(
    building.latitude == null ? "" : String(building.latitude),
  );
  const [longitude, setLongitude] = useState<string>(
    building.longitude == null ? "" : String(building.longitude),
  );

  useEffect(() => {
    if (open) {
      setBuildingName(building.buildingName ?? "");
      setBuildingAddress(building.buildingAddress ?? "");
      setTotalFloors(String(building.buildingTotalFloors ?? ""));
      setTotalFlats(String(building.buildingTotalFlats ?? ""));
      setAmenities(building.amenities ?? "");
      setLatitude(building.latitude == null ? "" : String(building.latitude));
      setLongitude(
        building.longitude == null ? "" : String(building.longitude),
      );
    }
  }, [open, building]);

  const updateM = useMutation({
    mutationFn: () => {
      // Re-validate lat/lng in the same shape the create form does —
      // empty → null, non-finite or out-of-range → reject.
      let lat: number | null = null;
      let lng: number | null = null;
      const latT = latitude.trim();
      const lngT = longitude.trim();
      if (latT || lngT) {
        const latN = Number(latT);
        const lngN = Number(lngT);
        if (
          !Number.isFinite(latN) ||
          latN < -90 ||
          latN > 90 ||
          !Number.isFinite(lngN) ||
          lngN < -180 ||
          lngN > 180
        ) {
          throw new Error(
            "Latitude must be between −90 and 90, longitude between −180 and 180. Leave both blank to skip.",
          );
        }
        lat = latN;
        lng = lngN;
      }
      // City + state are immutable post-create — pass through the
      // existing values from the building prop. We re-send them so the
      // backend's @Valid DTO doesn't fail on missing required fields.
      return propertiesApi.buildings.update(building.buildingId, {
        ownerId: building.ownerId,
        buildingName: buildingName.trim(),
        buildingAddress: buildingAddress.trim(),
        buildingCity: building.buildingCity,
        buildingState: building.buildingState,
        buildingTotalFloors: Number(totalFloors) || 0,
        buildingTotalFlats: Number(totalFlats) || 0,
        amenities: amenities.trim(),
        latitude: lat,
        longitude: lng,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      qc.invalidateQueries({ queryKey: ["building", building.buildingId] });
      toast({ title: "Building updated" });
      onOpenChange(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't save changes",
        description: extractErrorMessage(e),
      }),
  });

  const dirty =
    buildingName.trim() !== (building.buildingName ?? "") ||
    buildingAddress.trim() !== (building.buildingAddress ?? "") ||
    Number(totalFloors) !== (building.buildingTotalFloors ?? 0) ||
    Number(totalFlats) !== (building.buildingTotalFlats ?? 0) ||
    amenities.trim() !== (building.amenities ?? "") ||
    latitude.trim() !==
      (building.latitude == null ? "" : String(building.latitude)) ||
    longitude.trim() !==
      (building.longitude == null ? "" : String(building.longitude));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <PencilLine className="size-4 text-primary" />
            Edit {building.buildingName}
          </DialogTitle>
          <DialogDescription>
            City and state are locked once a building is listed — they
            stamp the property's location on the listing. To change them,
            delete and recreate the building.
          </DialogDescription>
        </DialogHeader>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateM.mutate();
          }}
          className="space-y-4"
        >
          <div>
            <Label htmlFor="ed-name">Building name</Label>
            <Input
              id="ed-name"
              value={buildingName}
              onChange={(e) => setBuildingName(e.target.value)}
              className="mt-1.5"
              required
              maxLength={120}
            />
          </div>

          <div>
            <Label htmlFor="ed-address">Street address</Label>
            <Input
              id="ed-address"
              value={buildingAddress}
              onChange={(e) => setBuildingAddress(e.target.value)}
              className="mt-1.5"
              required
              maxLength={300}
            />
          </div>

          {/* Read-only city + state for visual confirmation. */}
          <div className="grid sm:grid-cols-2 gap-3">
            <div>
              <Label>City</Label>
              <p className="mt-1.5 px-3 py-2 rounded-md border bg-secondary/30 text-sm">
                {building.buildingCity}
              </p>
            </div>
            <div>
              <Label>State</Label>
              <p className="mt-1.5 px-3 py-2 rounded-md border bg-secondary/30 text-sm">
                {building.buildingState}
              </p>
            </div>
          </div>

          <div className="grid sm:grid-cols-2 gap-3">
            <div>
              <Label htmlFor="ed-floors">Total floors</Label>
              <Input
                id="ed-floors"
                type="number"
                min={1}
                value={totalFloors}
                onChange={(e) => setTotalFloors(e.target.value)}
                className="mt-1.5"
                required
              />
            </div>
            <div>
              <Label htmlFor="ed-flats">Total flats</Label>
              <Input
                id="ed-flats"
                type="number"
                min={1}
                value={totalFlats}
                onChange={(e) => setTotalFlats(e.target.value)}
                className="mt-1.5"
                required
              />
            </div>
          </div>

          <div className="grid sm:grid-cols-2 gap-3">
            <div>
              <Label htmlFor="ed-lat">Latitude (optional)</Label>
              <Input
                id="ed-lat"
                type="number"
                step="0.000001"
                value={latitude}
                onChange={(e) => setLatitude(e.target.value)}
                className="mt-1.5"
                placeholder="12.971599"
              />
            </div>
            <div>
              <Label htmlFor="ed-lng">Longitude (optional)</Label>
              <Input
                id="ed-lng"
                type="number"
                step="0.000001"
                value={longitude}
                onChange={(e) => setLongitude(e.target.value)}
                className="mt-1.5"
                placeholder="77.594566"
              />
            </div>
          </div>

          <div>
            <Label htmlFor="ed-amenities">Amenities</Label>
            <Textarea
              id="ed-amenities"
              rows={3}
              value={amenities}
              onChange={(e) => setAmenities(e.target.value)}
              className="mt-1.5"
              placeholder="e.g. Lift, parking, gym, swimming pool"
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              Comma- or newline-separated. Leave blank to hide the
              section on the listing.
            </p>
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
              disabled={!dirty || updateM.isPending}
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
