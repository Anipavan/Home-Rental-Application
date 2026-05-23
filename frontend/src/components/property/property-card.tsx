import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Bed, Bath, Square, MapPin } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { FavoriteButton } from "@/components/property/favorite-button";
import { propertiesApi } from "@/lib/api/properties";
import { formatINR } from "@/lib/utils";
import type { FlatResponseDTO } from "@/types/api";

const placeholders = [
  "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=900&q=70",
  "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=900&q=70",
  "https://images.unsplash.com/photo-1493809842364-78817add7ffb?auto=format&fit=crop&w=900&q=70",
  "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=900&q=70",
  "https://images.unsplash.com/photo-1505691938895-1758d7feb511?auto=format&fit=crop&w=900&q=70",
  "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=900&q=70",
];

function pickImage(seed: number | string) {
  const n = typeof seed === "number"
    ? seed
    : Array.from(String(seed)).reduce((a, c) => a + c.charCodeAt(0), 0);
  return placeholders[Math.abs(n) % placeholders.length];
}

export function PropertyCard({
  flat,
  buildingName,
  city,
}: {
  flat: FlatResponseDTO;
  buildingName?: string;
  city?: string;
}) {
  // Pull the building's gallery so we can use the cover image when
  // the owner has uploaded one. Falls back to the deterministic
  // Unsplash placeholder so listings without photos still render
  // visually distinct cards. The query is cached for 5min — the
  // browse grid renders many cards but they all share parent buildings,
  // so React Query dedupes the fetches.
  const imgsQ = useQuery({
    queryKey: ["building", flat.buildingId, "images"],
    queryFn: () => propertiesApi.buildings.images(flat.buildingId),
    enabled: !!flat.buildingId,
    staleTime: 5 * 60_000,
  });

  /**
   * Self-fetch the parent building so we can show the REAL city under
   * each listing. Previously this card had a hardcoded "Bengaluru"
   * fallback in the city pill — when the caller didn't pass `city`
   * (which browse.tsx never does), every listing showed Bengaluru
   * regardless of actual location. React Query dedupes by buildingId,
   * so a grid with 50 flats across 10 buildings makes 10 requests, not
   * 50.
   */
  const buildingQ = useQuery({
    queryKey: ["building", flat.buildingId, "summary"],
    queryFn: () => propertiesApi.buildings.get(flat.buildingId),
    enabled: !!flat.buildingId && !city,
    staleTime: 5 * 60_000,
  });

  const resolvedCity =
    city ?? buildingQ.data?.buildingCity ?? "Location not specified";

  const cover = imgsQ.data?.find((img) => img.isCover) ?? imgsQ.data?.[0];
  const img = cover
    ? propertiesApi.buildings.imageRawUrl(cover.id)
    : pickImage(flat.id);

  return (
    <Link
      to={`/property/${flat.id}`}
      className="group block overflow-hidden rounded-2xl bg-card border border-border/60 shadow-soft hover:shadow-lift transition-all hover:-translate-y-1"
    >
      <div className="relative aspect-[4/3] overflow-hidden bg-muted">
        <img
          src={img}
          alt={buildingName || flat.flatNumber}
          loading="lazy"
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
        <FavoriteButton
          flatId={flat.id}
          variant="card"
          className="absolute top-3 right-3"
        />
        <Badge
          variant={flat.isOccupied ? "secondary" : "success"}
          className="absolute top-3 left-3 bg-white/90 backdrop-blur"
        >
          {flat.isOccupied ? "Occupied" : "Available"}
        </Badge>
      </div>
      <div className="p-4 space-y-2.5">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h3 className="font-display font-semibold leading-tight truncate">
              {buildingName ? `${buildingName} · ${flat.flatNumber}` : `Flat ${flat.flatNumber}`}
            </h3>
            <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
              <MapPin className="size-3" />
              {resolvedCity}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <Bed className="size-3.5" /> {flat.bedrooms ?? 2} BHK
          </span>
          <span className="flex items-center gap-1">
            <Bath className="size-3.5" /> {flat.bathrooms ?? 2}
          </span>
          {flat.areaSqft && (
            <span className="flex items-center gap-1">
              <Square className="size-3.5" /> {flat.areaSqft} sqft
            </span>
          )}
        </div>
        <div className="flex items-baseline justify-between pt-1">
          <span className="font-display text-xl font-bold">
            {formatINR(flat.rentAmount)}
            <span className="text-xs font-normal text-muted-foreground">
              {" "}
              /month
            </span>
          </span>
          <span className="text-xs text-primary font-medium">View →</span>
        </div>
      </div>
    </Link>
  );
}

export { pickImage as getPlaceholderImage };
