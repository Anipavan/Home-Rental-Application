import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ImagePlus, Loader2, X } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { FileUpload } from "@/components/ui/file-upload";
import { PageHeader } from "@/components/layout/page-header";
import { StateCitySelect } from "@/components/common/state-city-select";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { RefCityDto, RefStateDto } from "@/types/api";

/**
 * Add Building (Day 7 stabilization rebuild).
 *
 * What changed vs. the prior version:
 *  - **Cascading state → city dropdown** powered by reference data seeded
 *    into property-service. Both name strings + FK ids go up to the API.
 *  - **Multi-image upload at creation time**. Building is created first;
 *    images are uploaded sequentially against the new building id. If the
 *    building creates but image upload fails, the user is still navigated
 *    to the detail page (the building isn't lost).
 *
 * The form continues to send {@code buildingCity} and {@code buildingState}
 * as strings for backwards compatibility — the new {@code stateId}/{@code cityId}
 * fields are appended.
 */
export function BuildingNewPage() {
  const { authUserId } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();
  // Phase 2 — when the user landed here from the "I'm starting a
  // society" signup card, show a softer onboarding banner. State is
  // passed via react-router from /register; falls back to null on
  // direct navigation or refresh.
  const location = useLocation();
  const fromSocietyFounder =
    (location.state as { fromSocietyFounder?: boolean } | null)?.fromSocietyFounder ===
    true;

  const [pickedState, setPickedState] = useState<RefStateDto | null>(null);
  const [pickedCity, setPickedCity] = useState<RefCityDto | null>(null);
  const [images, setImages] = useState<File[]>([]);
  const [uploadingImages, setUploadingImages] = useState(false);
  /**
   * Audit M26: per-file upload progress. `uploadedCount` is the
   * number of FILES finished (each completes atomically — we don't
   * stream individual byte counts because the underlying axios
   * POST gives us a Promise, not progress events). `uploadedFailed`
   * counts failures so the toast can summarize "3 of 5 uploaded".
   */
  const [uploadedCount, setUploadedCount] = useState(0);
  const [uploadedFailed, setUploadedFailed] = useState(0);

  const createM = useMutation({
    mutationFn: propertiesApi.buildings.create,
    onSuccess: async (created) => {
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      // Sequentially upload images — one at a time so a single
      // bad upload doesn't abort the rest. Per-file outcome is
      // recorded so the user sees real-time progress instead of a
      // single "uploading…" spinner.
      if (images.length > 0) {
        setUploadingImages(true);
        setUploadedCount(0);
        setUploadedFailed(0);
        for (const img of images) {
          try {
            await propertiesApi.buildings.uploadImage(created.buildingId, img);
            setUploadedCount((n) => n + 1);
          } catch (e) {
            setUploadedFailed((n) => n + 1);
            // Don't abort on one bad upload — the next attempt may
            // succeed, and the user just sees the failure count grow.
            // We surface a summary toast at the end.
            console.warn("Image upload failed:", e);
          }
        }
        setUploadingImages(false);
        if (uploadedFailed > 0) {
          toast({
            variant: "destructive",
            title: `${uploadedFailed} of ${images.length} images didn't upload`,
            description:
              "Add the rest from the building's detail page once you land there.",
          });
        }
      }
      toast({
        title: "Building added",
        description: `${created.buildingName} is live.`,
      });
      navigate(`/owner/buildings/${created.buildingId}`);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't add building",
        description: extractErrorMessage(e),
      }),
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!authUserId) return;
    if (!pickedState || !pickedCity) {
      toast({
        variant: "destructive",
        title: "Pick a state and city",
        description: "Both are required so listings show up in the right region.",
      });
      return;
    }
    const fd = new FormData(e.currentTarget);

    // Audit H30: optional lat/lng — fed through to the geosearch +
    // map view. Empty = building hides from /browse?nearMe and from
    // the Map view (Haversine on null is fine; the backend just
    // excludes pin-less rows). Validate range up-front so the
    // backend's blanket String columns aren't tasked with
    // semantic checks.
    const latRaw = String(fd.get("latitude") ?? "").trim();
    const lngRaw = String(fd.get("longitude") ?? "").trim();
    let latitude: number | null = null;
    let longitude: number | null = null;
    if (latRaw || lngRaw) {
      const latN = Number(latRaw);
      const lngN = Number(lngRaw);
      if (!Number.isFinite(latN) || latN < -90 || latN > 90
          || !Number.isFinite(lngN) || lngN < -180 || lngN > 180) {
        toast({
          variant: "destructive",
          title: "Coordinates look off",
          description:
            "Latitude must be between −90 and 90, longitude between −180 and 180. Leave both blank to skip.",
        });
        return;
      }
      latitude = latN;
      longitude = lngN;
    }

    createM.mutate({
      ownerId: authUserId,
      buildingName: String(fd.get("buildingName") ?? ""),
      buildingAddress: String(fd.get("buildingAddress") ?? ""),
      buildingCity: pickedCity.name,
      buildingState: pickedState.name,
      stateId: pickedState.id,
      cityId: pickedCity.id,
      buildingTotalFloors: Number(fd.get("buildingTotalFloors") ?? 0),
      buildingTotalFlats: Number(fd.get("buildingTotalFlats") ?? 0),
      amenities: String(fd.get("amenities") ?? ""),
      // "What's included" — flat-level fittings (kitchen, AC, etc.)
      // distinct from building-level amenities (lift, gym, etc.).
      includedItems: String(fd.get("includedItems") ?? ""),
      latitude,
      longitude,
    });
  }

  const submitting = createM.isPending || uploadingImages;

  return (
    <div className="animate-fade-in max-w-2xl">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/owner/buildings">
          <ArrowLeft /> Back
        </Link>
      </Button>
      <PageHeader
        title={fromSocietyFounder ? "Set up your society" : "Add a building"}
        description={
          fromSocietyFounder
            ? "Give your building a name and address. You can add flats and invite residents on the next screen."
            : "The big-picture details. You'll add flats next."
        }
      />

      {fromSocietyFounder ? (
        <Card className="mb-4 border-primary/30 bg-primary/5">
          <CardContent className="p-4 text-sm">
            <p className="font-medium">Step 1 of 3 — Building basics</p>
            <p className="text-muted-foreground text-xs mt-1">
              Next: add flats, then share the join link with residents.
              Every join request lands in your dashboard for approval.
            </p>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardContent className="p-6 sm:p-8">
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <Label htmlFor="buildingName">Building name</Label>
              <Input
                id="buildingName"
                name="buildingName"
                required
                className="mt-1.5"
                placeholder="Sunshine Apartments"
              />
            </div>

            <div>
              <Label htmlFor="buildingAddress">Street address</Label>
              <Input
                id="buildingAddress"
                name="buildingAddress"
                required
                className="mt-1.5"
                placeholder="42, MG Road, near Brigade Junction"
              />
            </div>

            <div className="grid sm:grid-cols-2 gap-4">
              <StateCitySelect
                state={pickedState}
                city={pickedCity}
                onChange={({ state, city }) => {
                  setPickedState(state);
                  setPickedCity(city);
                }}
                required
              />
            </div>

            <div className="grid sm:grid-cols-2 gap-4">
              <Field
                label="Total floors"
                name="buildingTotalFloors"
                type="number"
                min={1}
                required
              />
              <Field
                label="Total flats"
                name="buildingTotalFlats"
                type="number"
                min={1}
                required
              />
            </div>

            {/* Audit H30: optional geo-pin. Powers the /browse?nearMe
                geosearch and the Map view. Owners can leave blank — the
                building still lists, just without a map pin. We
                deliberately don't try to reverse-geocode the address
                client-side; OSM Nominatim has aggressive rate limits
                and a bad pin is worse than no pin. */}
            <div className="grid sm:grid-cols-2 gap-4">
              <Field
                label="Latitude (optional)"
                name="latitude"
                type="number"
                step="0.000001"
                placeholder="12.971599"
              />
              <Field
                label="Longitude (optional)"
                name="longitude"
                type="number"
                step="0.000001"
                placeholder="77.594566"
              />
            </div>
            <p className="text-[11px] text-muted-foreground -mt-3">
              Drop the pin to enable map-view discovery. Easiest source:
              Google Maps → right-click on the building → "What's here?" →
              copy the two numbers.
            </p>

            <div>
              <Label htmlFor="amenities">Amenities</Label>
              <Textarea
                id="amenities"
                name="amenities"
                rows={3}
                className="mt-1.5"
                placeholder="e.g. Lift, parking, gym, swimming pool"
              />
              <p className="text-[11px] text-muted-foreground mt-1">
                Building-level perks. Comma- or newline-separated. Leave blank
                to hide the section.
              </p>
            </div>

            <div>
              <Label htmlFor="includedItems">What's included</Label>
              <Textarea
                id="includedItems"
                name="includedItems"
                rows={3}
                className="mt-1.5"
                placeholder="e.g. Modular kitchen, RO water purifier, wardrobes in all bedrooms, AC"
              />
              <p className="text-[11px] text-muted-foreground mt-1">
                Flat-level fittings the tenant gets out-of-the-box. Comma- or
                newline-separated. Leave blank to hide the section.
              </p>
            </div>

            <ImagePicker images={images} onChange={setImages} />

            {/* Audit M26: live per-file progress. The image upload
                is sequential (one POST at a time) so a count-based
                progress is the most useful read; byte-level isn't
                available without intercepting axios's onUploadProgress
                per request. */}
            {uploadingImages && images.length > 0 && (
              <div
                role="status"
                aria-live="polite"
                className="rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground"
              >
                <div className="flex items-center justify-between gap-2">
                  <span>
                    Uploading photo{" "}
                    <span className="font-semibold text-foreground">
                      {Math.min(uploadedCount + uploadedFailed + 1, images.length)}
                    </span>{" "}
                    of {images.length}
                    {uploadedFailed > 0 && (
                      <>
                        {" · "}
                        <span className="text-destructive font-medium">
                          {uploadedFailed} failed
                        </span>
                      </>
                    )}
                  </span>
                  <Loader2 className="size-3.5 animate-spin" />
                </div>
                <div className="mt-2 h-1 w-full overflow-hidden rounded bg-muted">
                  <div
                    className="h-full bg-primary transition-all"
                    style={{
                      width: `${Math.round(
                        ((uploadedCount + uploadedFailed) / images.length) * 100,
                      )}%`,
                    }}
                  />
                </div>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button asChild variant="ghost" disabled={submitting}>
                <Link to="/owner/buildings">Cancel</Link>
              </Button>
              <Button type="submit" variant="gradient" disabled={submitting}>
                {submitting && <Loader2 className="size-4 animate-spin" />}
                {uploadingImages
                  ? `Uploading… ${uploadedCount + uploadedFailed}/${images.length}`
                  : "Add building"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({
  label,
  name,
  type = "text",
  required,
  min,
  max,
  step,
  placeholder,
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  min?: number;
  max?: number;
  step?: string;
  placeholder?: string;
}) {
  return (
    <div>
      <Label htmlFor={name}>{label}</Label>
      <Input
        id={name}
        name={name}
        type={type}
        required={required}
        min={min}
        max={max}
        step={step}
        placeholder={placeholder}
        className="mt-1.5"
      />
    </div>
  );
}

function ImagePicker({
  images,
  onChange,
}: {
  images: File[];
  onChange: (files: File[]) => void;
}) {
  // Inline previews using URL.createObjectURL — revoked when the array shrinks.
  return (
    <div>
      <Label className="mb-1.5 inline-flex items-center gap-2">
        <ImagePlus className="size-4 text-muted-foreground" />
        Photos
        <span className="text-xs text-muted-foreground font-normal">
          (optional, up to 6)
        </span>
      </Label>
      <FileUpload
        accept="image/png,image/jpeg,image/jpg,image/webp"
        multiple
        maxSizeMB={5}
        onFiles={(files) => {
          const total = [...images, ...files].slice(0, 6);
          onChange(total);
        }}
        hint="Drag & drop, or click to browse"
      />
      {images.length > 0 && (
        <div className="mt-3 grid grid-cols-3 sm:grid-cols-6 gap-2">
          {images.map((img, i) => (
            <div
              key={`${img.name}-${i}`}
              className="relative aspect-square rounded-lg overflow-hidden border bg-secondary/30"
            >
              <img
                src={URL.createObjectURL(img)}
                alt=""
                className="w-full h-full object-cover"
                onLoad={(e) => URL.revokeObjectURL((e.target as HTMLImageElement).src)}
              />
              <button
                type="button"
                aria-label="Remove image"
                onClick={() =>
                  onChange(images.filter((_, idx) => idx !== i))
                }
                className="absolute top-1 right-1 size-5 rounded-full bg-background/85 grid place-items-center hover:bg-destructive hover:text-destructive-foreground transition-colors"
              >
                <X className="size-3" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
