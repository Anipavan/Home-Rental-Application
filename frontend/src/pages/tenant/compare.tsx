import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  Bath,
  Bed,
  Calendar,
  CheckCircle2,
  IndianRupee,
  MapPin,
  Plus,
  Ruler,
  Scale,
  Sofa,
  Square,
  X,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { favoritesApi, propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/layout/page-header";
import { getPlaceholderImage } from "@/components/property/property-card";
import { formatINR } from "@/lib/utils";
import { cn } from "@/lib/utils";
import type { FlatResponseDTO } from "@/types/api";

/**
 * Side-by-side comparison page for up to 3 flats.
 *
 * <p>Inspired by 99acres / Zillow comparison views. Two entry points:
 *   - {@code /app/compare?ids=a,b,c} — direct link from the Saved
 *     listings page ("Compare these 3"), or shareable URL between
 *     people house-hunting together.
 *   - {@code /app/compare} (no query) — empty-picker where the user
 *     adds flats from their wishlist via an inline picker.
 *
 * <p>Rendered as a horizontal scroll row of column-cards on mobile +
 * a 3-column grid on desktop. Each card carries the image, key stats
 * (rent, BHK, area, deposit, furnishing, pet policy, move-in date),
 * and a Compare-style row strip below highlighting which column
 * "wins" on each numeric dimension (cheapest rent, largest area,
 * etc.).
 *
 * <p>Adding a flat manipulates the {@code ids} query string so URLs
 * stay shareable.
 */
const MAX_COMPARE = 3;

export function ComparePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { isAuthenticated } = useAuthStore();
  const [pickerOpen, setPickerOpen] = useState(false);

  const ids = useMemo(() => {
    const raw = searchParams.get("ids") ?? "";
    return raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, MAX_COMPARE);
  }, [searchParams]);

  // Fetch each flat individually so a missing/dead flat surfaces as a
  // friendly "removed" tile instead of failing the whole page. Each
  // useQuery is keyed on the flatId so React Query dedupes if the
  // same flat is also shown elsewhere.
  const flats = useFlats(ids);

  // Wishlist as the "add column" source — we want users adding flats
  // they've already shown intent for, not random catalog entries.
  const wishlistQ = useQuery({
    queryKey: ["favorites", "list"],
    queryFn: () => favoritesApi.list(),
    enabled: isAuthenticated && pickerOpen,
  });

  function addId(id: string) {
    if (ids.includes(id)) {
      setPickerOpen(false);
      return;
    }
    const next = [...ids, id].slice(0, MAX_COMPARE);
    setSearchParams({ ids: next.join(",") });
    setPickerOpen(false);
  }

  function removeId(id: string) {
    const next = ids.filter((x) => x !== id);
    if (next.length === 0) {
      setSearchParams({}, { replace: true });
    } else {
      setSearchParams({ ids: next.join(",") });
    }
  }

  // Numeric "winner" indices per dimension. Used to highlight the
  // best cell in each row of the comparison table.
  const winners = useMemo(() => computeWinners(flats.data ?? []), [flats.data]);

  return (
    <div className="animate-fade-in">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/app/saved">
          <ArrowLeft /> Back to saved
        </Link>
      </Button>
      <PageHeader
        title="Compare homes"
        description={`Pick up to ${MAX_COMPARE} flats from your wishlist and weigh them side by side.`}
      />

      {ids.length === 0 ? (
        <EmptyState onAdd={() => setPickerOpen(true)} />
      ) : (
        <>
          {/* Header row: small thumbnails + remove + add */}
          <div className="grid grid-cols-3 gap-3 mb-5">
            {Array.from({ length: MAX_COMPARE }).map((_, slot) => {
              const id = ids[slot];
              const flat = id ? flats.byId[id] : undefined;
              if (!id) {
                return (
                  <button
                    key={`slot-${slot}`}
                    type="button"
                    onClick={() => setPickerOpen(true)}
                    className="aspect-[4/3] rounded-2xl border-2 border-dashed border-border hover:border-primary/40 hover:bg-secondary/30 transition-colors grid place-items-center text-muted-foreground"
                  >
                    <div className="flex flex-col items-center gap-1.5">
                      <Plus className="size-5" />
                      <span className="text-xs font-medium">Add a home</span>
                    </div>
                  </button>
                );
              }
              if (!flat) {
                return (
                  <Card key={id} className="aspect-[4/3] grid place-items-center text-muted-foreground text-xs">
                    Removed
                    <button
                      type="button"
                      onClick={() => removeId(id)}
                      className="absolute top-2 right-2 size-7 rounded-full bg-background/80 grid place-items-center"
                      aria-label="Remove"
                    >
                      <X className="size-3.5" />
                    </button>
                  </Card>
                );
              }
              return (
                <ColumnHeader
                  key={id}
                  flat={flat}
                  onRemove={() => removeId(id)}
                />
              );
            })}
          </div>

          {/* Comparison rows */}
          <Card className="overflow-hidden">
            <Row label="Monthly rent" icon={IndianRupee}>
              {ids.map((id, i) => {
                const f = flats.byId[id];
                return (
                  <Cell key={id} highlight={winners.rent === i}>
                    {f ? formatINR(f.rentAmount) : "—"}
                  </Cell>
                );
              })}
            </Row>
            <Row label="Deposit" icon={IndianRupee}>
              {ids.map((id, i) => {
                const f = flats.byId[id];
                const val =
                  f?.depositAmount != null
                    ? formatINR(f.depositAmount)
                    : f
                      ? `~${formatINR(f.rentAmount * 2)}`
                      : "—";
                return (
                  <Cell key={id} highlight={winners.deposit === i}>
                    {val}
                    {f?.depositAmount == null && f && (
                      <span className="text-[10px] text-muted-foreground ml-1">
                        (estimate)
                      </span>
                    )}
                  </Cell>
                );
              })}
            </Row>
            <Row label="Bedrooms" icon={Bed}>
              {ids.map((id, i) => (
                <Cell key={id} highlight={winners.bedrooms === i}>
                  {flats.byId[id]?.bedrooms ?? "—"} BHK
                </Cell>
              ))}
            </Row>
            <Row label="Bathrooms" icon={Bath}>
              {ids.map((id, i) => (
                <Cell key={id} highlight={winners.bathrooms === i}>
                  {flats.byId[id]?.bathrooms ?? "—"}
                </Cell>
              ))}
            </Row>
            <Row label="Carpet area" icon={Square}>
              {ids.map((id, i) => {
                const f = flats.byId[id];
                return (
                  <Cell key={id} highlight={winners.area === i}>
                    {f?.areaSqft ? `${f.areaSqft} sqft` : "—"}
                  </Cell>
                );
              })}
            </Row>
            <Row label="Floor" icon={Ruler}>
              {ids.map((id) => (
                <Cell key={id}>{flats.byId[id]?.floor ?? "—"}</Cell>
              ))}
            </Row>
            <Row label="Furnishing" icon={Sofa}>
              {ids.map((id) => {
                const v = flats.byId[id]?.furnishingStatus;
                return <Cell key={id}>{v ? prettyFurnishing(v) : "—"}</Cell>;
              })}
            </Row>
            <Row label="Pet policy" icon={CheckCircle2}>
              {ids.map((id) => {
                const v = flats.byId[id]?.petFriendly;
                return (
                  <Cell key={id}>
                    {v === true ? "Pets allowed" : v === false ? "No pets" : "—"}
                  </Cell>
                );
              })}
            </Row>
            <Row label="Available from" icon={Calendar}>
              {ids.map((id) => (
                <Cell key={id}>
                  {flats.byId[id]?.availableFrom ?? "—"}
                </Cell>
              ))}
            </Row>
            <Row label="Location" icon={MapPin}>
              {ids.map((id) => (
                <Cell key={id}>
                  {flats.byId[id]?.buildingCity ?? "—"}
                </Cell>
              ))}
            </Row>
            <Row label="Status" icon={CheckCircle2}>
              {ids.map((id) => {
                const f = flats.byId[id];
                if (!f) return <Cell key={id}>—</Cell>;
                return (
                  <Cell key={id}>
                    <Badge variant={f.isOccupied ? "secondary" : "success"}>
                      {f.isOccupied ? "Occupied" : "Available"}
                    </Badge>
                  </Cell>
                );
              })}
            </Row>
          </Card>

          <p className="text-xs text-muted-foreground mt-4 flex items-center gap-1.5">
            <Scale className="size-3.5" />
            Highlighted cells indicate the best value on each row (lowest
            rent / deposit, largest area, most rooms).
          </p>
        </>
      )}

      {/* Wishlist picker */}
      {pickerOpen && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 backdrop-blur-sm p-4">
          <Card className="w-full max-w-md max-h-[80vh] overflow-hidden flex flex-col">
            <div className="p-4 border-b flex items-center justify-between">
              <h3 className="font-display font-semibold text-base">
                Add from your wishlist
              </h3>
              <button
                type="button"
                onClick={() => setPickerOpen(false)}
                className="size-7 rounded-full hover:bg-secondary grid place-items-center"
                aria-label="Close"
              >
                <X className="size-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-3 space-y-2">
              {wishlistQ.isLoading && (
                <>
                  <Skeleton className="h-16 rounded-lg" />
                  <Skeleton className="h-16 rounded-lg" />
                </>
              )}
              {!wishlistQ.isLoading && (wishlistQ.data ?? []).length === 0 && (
                <div className="text-center text-sm text-muted-foreground py-8">
                  Your wishlist is empty.
                  <Button asChild variant="link" className="ml-1">
                    <Link to="/browse">Browse homes</Link>
                  </Button>
                </div>
              )}
              {(wishlistQ.data ?? [])
                .filter((f) => !ids.includes(f.id))
                .map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    onClick={() => addId(f.id)}
                    className="w-full flex items-center gap-3 p-2 rounded-lg hover:bg-secondary/60 text-left transition-colors"
                  >
                    <img
                      src={getPlaceholderImage(f.id)}
                      alt=""
                      className="size-14 rounded-md object-cover"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">
                        {f.buildingName ?? `Flat ${f.flatNumber}`}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {formatINR(f.rentAmount)} · {f.bedrooms ?? "—"} BHK ·{" "}
                        {f.buildingCity ?? "—"}
                      </p>
                    </div>
                  </button>
                ))}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

/* ────────────────────── helpers ────────────────────── */

function useFlats(ids: string[]) {
  // One query per id so the data parallelizes + a stale flat doesn't
  // break the page. React Query batches the renders.
  const queries = ids.map((id) =>
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useQuery({
      queryKey: ["flat", id],
      queryFn: () => propertiesApi.flats.get(id),
      enabled: !!id,
      retry: 0,
      staleTime: 30_000,
    }),
  );
  const data: FlatResponseDTO[] = queries
    .map((q) => q.data)
    .filter((f): f is FlatResponseDTO => !!f);
  const byId: Record<string, FlatResponseDTO> = {};
  for (const f of data) byId[f.id] = f;
  return { data, byId, isLoading: queries.some((q) => q.isLoading) };
}

function computeWinners(flats: FlatResponseDTO[]) {
  if (flats.length === 0)
    return { rent: -1, deposit: -1, area: -1, bedrooms: -1, bathrooms: -1 };
  const idxOfMin = (xs: (number | null | undefined)[]) => {
    let best = -1;
    let bestVal = Infinity;
    xs.forEach((v, i) => {
      if (v == null) return;
      if (v < bestVal) {
        bestVal = v;
        best = i;
      }
    });
    return best;
  };
  const idxOfMax = (xs: (number | null | undefined)[]) => {
    let best = -1;
    let bestVal = -Infinity;
    xs.forEach((v, i) => {
      if (v == null) return;
      if (v > bestVal) {
        bestVal = v;
        best = i;
      }
    });
    return best;
  };
  return {
    rent: idxOfMin(flats.map((f) => f.rentAmount)),
    deposit: idxOfMin(
      flats.map((f) =>
        f.depositAmount != null ? f.depositAmount : f.rentAmount * 2,
      ),
    ),
    area: idxOfMax(flats.map((f) => f.areaSqft)),
    bedrooms: idxOfMax(flats.map((f) => f.bedrooms)),
    bathrooms: idxOfMax(flats.map((f) => f.bathrooms)),
  };
}

function prettyFurnishing(v: string): string {
  switch (v) {
    case "UNFURNISHED":
      return "Unfurnished";
    case "SEMI_FURNISHED":
      return "Semi-furnished";
    case "FULLY_FURNISHED":
      return "Fully furnished";
    default:
      return v;
  }
}

/* ────────────────────── building blocks ────────────────────── */

function EmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <Card className="p-12 text-center">
      <div className="size-14 rounded-full bg-primary/10 grid place-items-center mx-auto">
        <Scale className="size-6 text-primary" />
      </div>
      <p className="font-display font-semibold text-lg mt-4">
        Nothing to compare yet
      </p>
      <p className="text-muted-foreground text-sm mt-1 max-w-sm mx-auto">
        Save a few homes you like, then bring them here side-by-side
        to weigh rent, area, and amenities.
      </p>
      <div className="mt-5 flex justify-center gap-2">
        <Button variant="gradient" onClick={onAdd}>
          <Plus /> Add from wishlist
        </Button>
        <Button asChild variant="outline">
          <Link to="/browse">Browse homes</Link>
        </Button>
      </div>
    </Card>
  );
}

function ColumnHeader({
  flat,
  onRemove,
}: {
  flat: FlatResponseDTO;
  onRemove: () => void;
}) {
  return (
    <Card className="relative overflow-hidden">
      <button
        type="button"
        onClick={onRemove}
        className="absolute top-2 right-2 z-10 size-7 grid place-items-center rounded-full bg-background/85 backdrop-blur hover:bg-background shadow-soft"
        aria-label="Remove"
      >
        <X className="size-3.5" />
      </button>
      <Link to={`/property/${flat.id}`}>
        <div className="aspect-[4/3] bg-muted">
          <img
            src={getPlaceholderImage(flat.id)}
            alt=""
            className="h-full w-full object-cover"
          />
        </div>
        <div className="p-3">
          <p className="text-sm font-medium truncate">
            {flat.buildingName ?? `Flat ${flat.flatNumber}`}
          </p>
          <p className="text-xs text-muted-foreground truncate">
            {flat.buildingCity ?? "—"}
          </p>
        </div>
      </Link>
    </Card>
  );
}

function Row({
  label,
  icon: Icon,
  children,
}: {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-[1.2fr_repeat(3,1fr)] sm:grid-cols-[1fr_repeat(3,1fr)] border-b border-border/40 last:border-b-0 text-sm">
      <div className="bg-secondary/30 px-3 sm:px-4 py-3 text-muted-foreground font-medium flex items-center gap-1.5">
        <Icon className="size-3.5" />
        {label}
      </div>
      {children}
    </div>
  );
}

function Cell({
  highlight,
  children,
}: {
  highlight?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "px-3 sm:px-4 py-3 border-l border-border/40",
        highlight && "bg-primary/5 font-semibold text-foreground",
      )}
    >
      {children}
    </div>
  );
}
