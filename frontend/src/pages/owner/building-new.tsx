import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
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

  const [pickedState, setPickedState] = useState<RefStateDto | null>(null);
  const [pickedCity, setPickedCity] = useState<RefCityDto | null>(null);
  const [images, setImages] = useState<File[]>([]);
  const [uploadingImages, setUploadingImages] = useState(false);

  const createM = useMutation({
    mutationFn: propertiesApi.buildings.create,
    onSuccess: async (created) => {
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      // Sequentially upload images — one at a time so the user sees a
      // clean error if any single upload fails. Building stays live either way.
      if (images.length > 0) {
        setUploadingImages(true);
        try {
          for (const img of images) {
            await propertiesApi.buildings.uploadImage(created.buildingId, img);
          }
        } catch (e) {
          toast({
            variant: "destructive",
            title: "Some images didn't upload",
            description: extractErrorMessage(
              e,
              "You can add the rest from the building's detail page.",
            ),
          });
        } finally {
          setUploadingImages(false);
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
        title="Add a building"
        description="The big-picture details. You'll add flats next."
      />

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
                label="Total flats (6–20)"
                name="buildingTotalFlats"
                type="number"
                min={6}
                max={20}
                required
              />
            </div>

            <div>
              <Label htmlFor="amenities">Amenities</Label>
              <Textarea
                id="amenities"
                name="amenities"
                rows={3}
                className="mt-1.5"
                placeholder="e.g. Lift, parking, gym, swimming pool"
              />
            </div>

            <ImagePicker images={images} onChange={setImages} />

            <div className="flex justify-end gap-2 pt-2">
              <Button asChild variant="ghost" disabled={submitting}>
                <Link to="/owner/buildings">Cancel</Link>
              </Button>
              <Button type="submit" variant="gradient" disabled={submitting}>
                {submitting && <Loader2 className="size-4 animate-spin" />}
                {uploadingImages ? "Uploading photos…" : "Add building"}
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
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  min?: number;
  max?: number;
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
