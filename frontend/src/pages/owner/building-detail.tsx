import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Plus } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { PageHeader } from "@/components/layout/page-header";
import { FileUpload } from "@/components/ui/file-upload";
import { PropertyImage } from "@/components/property/property-image";
import { formatINR, formatDate } from "@/lib/utils";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

export function BuildingDetailPage() {
  const { id } = useParams();
  // Building.buildingId is a String UUID on the backend — DON'T coerce to Number.
  // The previous `Number(id)` produced NaN, the request went to /buildings/NaN,
  // and the server returned 404 → users saw "Not found" with no explanation.
  const buildingId = id ?? "";
  const qc = useQueryClient();

  const buildingQ = useQuery({
    queryKey: ["building", buildingId],
    queryFn: () => propertiesApi.buildings.get(buildingId),
    enabled: !!buildingId,
  });
  const flatsQ = useQuery({
    queryKey: ["flats-by-building", buildingId],
    queryFn: () => propertiesApi.flats.byBuilding(buildingId),
    enabled: !!buildingId,
  });
  const imagesQ = useQuery({
    queryKey: ["building-images", buildingId],
    queryFn: () => propertiesApi.buildings.images(buildingId),
    enabled: !!buildingId,
  });

  const uploadM = useMutation({
    mutationFn: (file: File) =>
      propertiesApi.buildings.uploadImage(buildingId, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["building-images", buildingId] });
      toast({ title: "Image uploaded" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Upload failed",
        description: extractErrorMessage(e),
      }),
  });

  if (buildingQ.isLoading) return <Skeleton className="h-64 rounded-2xl" />;
  if (!buildingQ.data) return <p>Not found</p>;

  const b = buildingQ.data;
  const flats = flatsQ.data ?? [];
  const occupied = flats.filter((f) => f.isOccupied).length;
  const occRate = flats.length ? Math.round((occupied / flats.length) * 100) : 0;

  return (
    <div className="animate-fade-in">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/owner/buildings">
          <ArrowLeft /> Buildings
        </Link>
      </Button>

      <PageHeader
        title={b.buildingName}
        description={`${b.buildingAddress}, ${b.buildingCity}, ${b.buildingState}`}
        actions={
          <Button asChild variant="gradient">
            <Link to={`/owner/flats/new?building=${b.buildingId}`}>
              <Plus /> Add flat
            </Link>
          </Button>
        }
      />

      <Card className="mb-6">
        <CardContent className="p-6 grid grid-cols-2 sm:grid-cols-4 gap-5">
          <Stat label="Total flats" value={String(flats.length)} />
          <Stat label="Occupied" value={String(occupied)} />
          <Stat label="Occupancy" value={`${occRate}%`} />
          <Stat
            label="Monthly potential"
            value={formatINR(flats.reduce((s, f) => s + Number(f.rentAmount ?? 0), 0))}
          />
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardContent className="p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="font-display font-semibold text-lg">Photos</h2>
              <p className="text-xs text-muted-foreground">
                Better photos rent faster.
              </p>
            </div>
          </div>
          {imagesQ.isLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="aspect-square rounded-xl" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {(imagesQ.data ?? []).map((img) => (
                <div
                  key={img.id}
                  className="aspect-square rounded-xl overflow-hidden bg-muted border"
                >
                  {/* The DB stores a server-side filesystem path that the
                      browser can't load directly, so PropertyImage fetches
                      the bytes through the API and renders a blob URL. */}
                  <PropertyImage
                    imageId={img.id}
                    alt={`Photo of ${b.buildingName}`}
                    className="w-full h-full"
                  />
                </div>
              ))}
              <FileUpload
                accept="image/*"
                maxSizeMB={5}
                loading={uploadM.isPending}
                onFiles={async (files) => {
                  for (const f of files) {
                    await uploadM.mutateAsync(f);
                  }
                }}
                hint="JPG/PNG · up to 5 MB"
                className="aspect-square"
              />
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <div className="p-6 flex items-center justify-between">
          <h2 className="font-display font-semibold text-lg">Flats</h2>
          <p className="text-xs text-muted-foreground">{flats.length} total</p>
        </div>
        <Separator />
        <div className="divide-y">
          {flatsQ.isLoading &&
            Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="px-6 py-4">
                <Skeleton className="h-12" />
              </div>
            ))}
          {!flatsQ.isLoading && flats.length === 0 && (
            <div className="p-10 text-center text-muted-foreground">
              No flats yet. Add the first one.
            </div>
          )}
          {flats.map((f) => (
            <div
              key={f.id}
              className="px-6 py-4 flex items-center justify-between gap-4"
            >
              <div className="flex items-center gap-3 min-w-0">
                <div className="size-10 rounded-lg bg-secondary grid place-items-center text-sm font-semibold">
                  {f.flatNumber}
                </div>
                <div className="min-w-0">
                  <p className="font-medium text-sm">
                    {f.bedrooms ?? 2}BHK · Floor {f.floor ?? "—"}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {f.tenantId ? `Tenant ${f.tenantId}` : "Vacant"}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span className="font-semibold text-sm hidden sm:block">
                  {formatINR(f.rentAmount)}
                </span>
                <Badge variant={f.isOccupied ? "secondary" : "success"}>
                  {f.isOccupied ? "Occupied" : "Vacant"}
                </Badge>
                {f.leaseEndDate && (
                  <span className="text-xs text-muted-foreground hidden md:block">
                    Lease ends {formatDate(f.leaseEndDate)}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-display font-semibold text-xl mt-0.5">{value}</p>
    </div>
  );
}
