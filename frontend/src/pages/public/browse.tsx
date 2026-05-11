import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Bath,
  Bed,
  Building2,
  CalendarDays,
  Dog,
  Eye,
  IndianRupee,
  Layers,
  Loader2,
  LocateFixed,
  MapPin,
  Ruler,
  Search,
  SlidersHorizontal,
  Sofa,
  X,
} from "lucide-react";
import { toast } from "@/hooks/use-toast";
import { propertiesApi } from "@/lib/api/properties";
import { PropertyCard } from "@/components/property/property-card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import type { FlatResponseDTO } from "@/types/api";

/**
 * Advanced browse page — inspired by NoBroker / 99acres / Zillow filters.
 *
 * Surface:
 *  - Top filter bar: text search, city, BHK, budget preset, sort, "More filters"
 *  - More-filters dialog: bathrooms, area sq ft min/max, floor band,
 *    vacant-only toggle, custom rent min/max
 *  - Active-filter chips with individual X + a Clear-all reset
 *  - Result count
 *
 * State design: filters live in component state (the URL only carries
 * the free-text `q` so deep links from GlobalSearch still pre-fill).
 * Going fully URL-backed across 8+ filter knobs is a separate scope —
 * easy to layer on top of this if/when needed.
 *
 * Data shape: pulls all flats (page size 60) and filters client-side.
 * This is fine for the current scale; when the catalog grows past a
 * few hundred listings, the backend needs a real search endpoint
 * with these filters pushed down.
 */

type SortKey = "recent" | "priceLow" | "priceHigh" | "areaHigh" | "bedsHigh";

const DEFAULT_FILTERS = {
  city: "any",
  bhk: "any",
  bathrooms: "any",
  budget: "any",
  floor: "any",
  // Custom min/max are kept as strings so "" means "unset". Parsing
  // happens at filter time.
  rentMin: "",
  rentMax: "",
  areaMin: "",
  areaMax: "",
  vacantOnly: false,
  // Listing-attribute filters (NoBroker / 99acres style).
  // "any" = don't filter on this dimension.
  furnishing: "any" as "any" | "UNFURNISHED" | "SEMI_FURNISHED" | "FULLY_FURNISHED",
  petPolicy: "any" as "any" | "allowed" | "notAllowed",
  /** ISO yyyy-mm-dd; "" = no constraint. Filter is "available on or
   *  before this date" — flats whose availableFrom is null are
   *  excluded only when the user explicitly picks a date. */
  availableBy: "",
  sort: "recent" as SortKey,
};

type Filters = typeof DEFAULT_FILTERS;

export function BrowsePage() {
  const [searchParams] = useSearchParams();
  const [q, setQ] = useState(searchParams.get("q") ?? "");
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS);
  const [moreOpen, setMoreOpen] = useState(false);
  const [locating, setLocating] = useState(false);
  // Resolved city name from the most recent geolocation attempt, kept
  // separately so we can show a friendly "Using your location: <City>"
  // banner even when the user hasn't explicitly set the city dropdown.
  const [geoCity, setGeoCity] = useState<string | null>(null);

  // Sync from URL → state. Catches the case where the user is already
  // on /app/browse and searches again from the AppShell GlobalSearch.
  useEffect(() => {
    const next = searchParams.get("q") ?? "";
    setQ(next);
  }, [searchParams]);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["flats", "all"],
    queryFn: () => propertiesApi.flats.list(0, 60),
  });

  // Derive the list of cities from results so the dropdown only offers
  // cities that actually have listings — no dead options.
  const cities = useMemo(() => {
    const set = new Set<string>();
    for (const f of data?.content ?? []) {
      if (f.buildingCity) set.add(f.buildingCity);
    }
    return [...set].sort();
  }, [data]);

  const filtered = useMemo(
    () => applyFilters(data?.content ?? [], q, filters),
    [data, q, filters],
  );

  const activeChips = describeActiveFilters(filters, q);

  function resetAll() {
    setFilters(DEFAULT_FILTERS);
    setQ("");
    setGeoCity(null);
  }

  /**
   * Ask the browser for the visitor's coordinates, reverse-geocode to
   * a city name, and set the city filter to whichever city in our
   * catalog best matches.
   *
   * Why reverse-geocode + match-to-catalog instead of distance-based
   * filtering: BuildingResponseDTO doesn't currently carry lat/lng,
   * so a true "homes within 5 km" radius isn't possible without a
   * data migration. City-name match is the next-best signal and aligns
   * with how NoBroker / 99acres / MagicBricks land users on a
   * "Homes in <Your City>" view.
   *
   * Uses BigDataCloud's free, key-less reverse-geocode-client endpoint
   * (CORS-friendly, no signup, ~99.5% city accuracy in India).
   */
  async function useMyLocation() {
    if (!("geolocation" in navigator)) {
      toast({
        variant: "destructive",
        title: "Geolocation not supported",
        description: "Your browser doesn't expose a location API.",
      });
      return;
    }
    setLocating(true);
    try {
      const position = await new Promise<GeolocationPosition>(
        (resolve, reject) =>
          navigator.geolocation.getCurrentPosition(resolve, reject, {
            enableHighAccuracy: false,
            timeout: 10_000,
            maximumAge: 60_000,
          }),
      );
      const { latitude, longitude } = position.coords;
      const url = new URL(
        "https://api.bigdatacloud.net/data/reverse-geocode-client",
      );
      url.searchParams.set("latitude", String(latitude));
      url.searchParams.set("longitude", String(longitude));
      url.searchParams.set("localityLanguage", "en");
      const resp = await fetch(url.toString());
      if (!resp.ok) throw new Error(`Reverse geocode failed (${resp.status})`);
      const body = (await resp.json()) as {
        city?: string;
        locality?: string;
        principalSubdivision?: string;
      };
      const detectedCity =
        body.city ?? body.locality ?? body.principalSubdivision ?? "";
      if (!detectedCity) {
        toast({
          variant: "destructive",
          title: "Couldn't read your city",
          description: "Try selecting a city from the dropdown instead.",
        });
        return;
      }
      setGeoCity(detectedCity);

      // Try to find a catalog city that matches case-insensitively.
      // If no exact match (e.g. user is in a town we don't list yet),
      // we still populate geoCity so the banner shows "Looking in
      // <city> — no homes there yet" and the rest of the catalog
      // stays visible.
      const matched = cities.find(
        (c) => c.toLowerCase() === detectedCity.toLowerCase(),
      );
      if (matched) {
        setFilters((f) => ({ ...f, city: matched }));
        toast({
          title: `Showing homes in ${matched}`,
          description: "Filter applied based on your current location.",
        });
      } else {
        toast({
          title: `You're in ${detectedCity}`,
          description: `We don't have listings there yet — showing every city instead.`,
        });
      }
    } catch (err) {
      const code = (err as GeolocationPositionError | undefined)?.code;
      const msg =
        code === 1
          ? "Location permission denied. You can still pick a city from the dropdown."
          : code === 2
            ? "Location unavailable. Try again or pick a city manually."
            : code === 3
              ? "Location request timed out. Please try again."
              : "Couldn't determine your location.";
      toast({ variant: "destructive", title: "Location failed", description: msg });
    } finally {
      setLocating(false);
    }
  }

  return (
    <div className="container py-8 lg:py-10">
      <PageHeader
        title="Find your next home"
        description="Verified listings. Direct from owners. Zero brokerage."
      />

      <Card className="p-3 mb-4 shadow-soft">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1fr_1fr_auto] gap-2">
          <div className="relative">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
            <Input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search by area, building or flat number"
              className="pl-10"
            />
          </div>
          <Select
            value={filters.city}
            onValueChange={(v) => setFilters((f) => ({ ...f, city: v }))}
          >
            <SelectTrigger aria-label="City">
              <SelectValue placeholder="Any city" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="any">Any city</SelectItem>
              {cities.map((c) => (
                <SelectItem key={c} value={c}>
                  {c}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={filters.bhk}
            onValueChange={(v) => setFilters((f) => ({ ...f, bhk: v }))}
          >
            <SelectTrigger aria-label="Bedrooms">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="any">Any BHK</SelectItem>
              <SelectItem value="1">1 BHK</SelectItem>
              <SelectItem value="2">2 BHK</SelectItem>
              <SelectItem value="3">3 BHK</SelectItem>
              <SelectItem value="4">4+ BHK</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={filters.budget}
            onValueChange={(v) => setFilters((f) => ({ ...f, budget: v }))}
          >
            <SelectTrigger aria-label="Budget">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="any">Any budget</SelectItem>
              <SelectItem value="0-15000">Under ₹15K</SelectItem>
              <SelectItem value="15000-30000">₹15K – 30K</SelectItem>
              <SelectItem value="30000-60000">₹30K – 60K</SelectItem>
              <SelectItem value="60000-100000">₹60K – 1L</SelectItem>
              <SelectItem value="100000-">₹1L +</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={filters.sort}
            onValueChange={(v) =>
              setFilters((f) => ({ ...f, sort: v as SortKey }))
            }
          >
            <SelectTrigger aria-label="Sort">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="recent">Most recent</SelectItem>
              <SelectItem value="priceLow">Price: low to high</SelectItem>
              <SelectItem value="priceHigh">Price: high to low</SelectItem>
              <SelectItem value="areaHigh">Largest area first</SelectItem>
              <SelectItem value="bedsHigh">Most bedrooms first</SelectItem>
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            className="gap-1.5"
            onClick={() => setMoreOpen(true)}
          >
            <SlidersHorizontal /> More filters
            {advancedCount(filters) > 0 && (
              <Badge variant="secondary" className="ml-1">
                {advancedCount(filters)}
              </Badge>
            )}
          </Button>
        </div>
      </Card>

      {/* Geolocation row — sits between the filter bar and the chips
          so users see it before scrolling. */}
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <Button
          variant="outline"
          size="sm"
          onClick={useMyLocation}
          disabled={locating}
          className="gap-1.5"
        >
          {locating ? (
            <Loader2 className="size-3.5 animate-spin" />
          ) : (
            <LocateFixed className="size-3.5" />
          )}
          {locating ? "Finding you…" : "Use my location"}
        </Button>
        {geoCity && (
          <span className="text-xs text-muted-foreground flex items-center gap-1">
            <MapPin className="size-3" />
            Detected: <span className="font-medium text-foreground">{geoCity}</span>
            <button
              type="button"
              onClick={() => setGeoCity(null)}
              className="text-muted-foreground/70 hover:text-foreground ml-1"
              aria-label="Forget my location"
            >
              <X className="size-3" />
            </button>
          </span>
        )}
        <span className="text-[11px] text-muted-foreground ml-auto">
          Your location stays on this device — we only use it to pre-fill the city filter.
        </span>
      </div>

      {/* Active filter chips */}
      {activeChips.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 mb-5">
          {activeChips.map((chip) => (
            <button
              key={chip.key}
              type="button"
              onClick={() => clearOne(chip.key, setFilters, setQ)}
              className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 text-primary text-xs font-medium pl-3 pr-2 py-1 hover:bg-primary/15 transition-colors"
            >
              {chip.label}
              <X className="size-3.5" />
            </button>
          ))}
          <button
            type="button"
            onClick={resetAll}
            className="text-xs text-muted-foreground hover:text-foreground underline underline-offset-2 ml-1"
          >
            Clear all
          </button>
        </div>
      )}

      {isLoading && (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Card key={i} className="overflow-hidden">
              <Skeleton className="aspect-[4/3] w-full rounded-none" />
              <div className="p-4 space-y-3">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/2" />
                <Skeleton className="h-5 w-1/3" />
              </div>
            </Card>
          ))}
        </div>
      )}

      {isError && (
        <Card className="p-12 text-center text-muted-foreground">
          We couldn't load listings right now. Please try again.
        </Card>
      )}

      {!isLoading && !isError && (
        <>
          <p className="text-sm text-muted-foreground mb-4">
            Showing{" "}
            <span className="font-semibold text-foreground">
              {filtered.length}
            </span>{" "}
            {filtered.length === 1 ? "home" : "homes"}
            {filters.city !== "any" && <> in {filters.city}</>}
          </p>
          {filtered.length === 0 ? (
            <Card className="p-12 text-center">
              <p className="font-display font-semibold text-lg">
                No homes match those filters.
              </p>
              <p className="text-muted-foreground text-sm mt-1">
                Try widening your budget or removing a filter.
              </p>
              {(activeChips.length > 0 || q) && (
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-4"
                  onClick={resetAll}
                >
                  Clear all filters
                </Button>
              )}
            </Card>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {filtered.map((flat) => (
                <PropertyCard key={flat.id} flat={flat} />
              ))}
            </div>
          )}
        </>
      )}

      <MoreFiltersDialog
        open={moreOpen}
        onClose={() => setMoreOpen(false)}
        filters={filters}
        setFilters={setFilters}
      />
    </div>
  );
}

/* ──────────────────────── Pure functions ──────────────────────── */

function applyFilters(
  rows: FlatResponseDTO[],
  q: string,
  f: Filters,
): FlatResponseDTO[] {
  let list = rows;

  if (q) {
    const needle = q.toLowerCase();
    list = list.filter((flat) => {
      const hay = [
        flat.flatNumber,
        flat.buildingName,
        flat.buildingAddress,
        flat.buildingCity,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return hay.includes(needle);
    });
  }

  if (f.city !== "any") {
    list = list.filter(
      (flat) =>
        flat.buildingCity &&
        flat.buildingCity.toLowerCase() === f.city.toLowerCase(),
    );
  }

  if (f.bhk !== "any") {
    const target = Number(f.bhk);
    list = list.filter((flat) =>
      // "4+" is encoded as 4 — match >= for that case.
      target === 4 ? (flat.bedrooms ?? 0) >= 4 : flat.bedrooms === target,
    );
  }

  if (f.bathrooms !== "any") {
    const target = Number(f.bathrooms);
    list = list.filter((flat) =>
      target === 3 ? (flat.bathrooms ?? 0) >= 3 : flat.bathrooms === target,
    );
  }

  // Preset budget bands take precedence; min/max from the advanced
  // sheet stack on top so a user can refine a band.
  if (f.budget !== "any") {
    const [lo, hi] = f.budget.split("-").map(Number);
    list = list.filter((flat) => {
      const r = Number(flat.rentAmount);
      if (Number.isNaN(r)) return false;
      if (!Number.isNaN(hi)) return r >= lo && r <= hi;
      return r >= lo;
    });
  }
  const rentMin = parseNumber(f.rentMin);
  const rentMax = parseNumber(f.rentMax);
  if (rentMin != null) list = list.filter((flat) => flat.rentAmount >= rentMin);
  if (rentMax != null) list = list.filter((flat) => flat.rentAmount <= rentMax);

  const areaMin = parseNumber(f.areaMin);
  const areaMax = parseNumber(f.areaMax);
  if (areaMin != null)
    list = list.filter((flat) => (flat.areaSqft ?? 0) >= areaMin);
  if (areaMax != null)
    list = list.filter((flat) => (flat.areaSqft ?? Infinity) <= areaMax);

  if (f.floor !== "any") {
    list = list.filter((flat) => {
      const fl = flat.floor ?? 0;
      switch (f.floor) {
        case "ground":
          return fl === 0;
        case "1-3":
          return fl >= 1 && fl <= 3;
        case "4-7":
          return fl >= 4 && fl <= 7;
        case "8+":
          return fl >= 8;
        default:
          return true;
      }
    });
  }

  if (f.vacantOnly) {
    list = list.filter((flat) => !flat.isOccupied);
  }

  // Listing attributes — only filter when the user has actively
  // picked a value. "any" / "" treat the dimension as ignored, so
  // legacy rows with null attributes still surface.
  if (f.furnishing !== "any") {
    list = list.filter((flat) => flat.furnishingStatus === f.furnishing);
  }
  if (f.petPolicy === "allowed") {
    list = list.filter((flat) => flat.petFriendly === true);
  } else if (f.petPolicy === "notAllowed") {
    list = list.filter((flat) => flat.petFriendly === false);
  }
  if (f.availableBy) {
    // Show flats available on OR before the chosen date. A null
    // availableFrom means the owner hasn't specified — when the user
    // does pick a date, those un-specified flats are excluded to
    // keep the filter strict.
    const cutoff = f.availableBy;
    list = list.filter(
      (flat) => flat.availableFrom != null && flat.availableFrom <= cutoff,
    );
  }

  // Sort last so it operates on the filtered set.
  switch (f.sort) {
    case "priceLow":
      list = [...list].sort((a, b) => a.rentAmount - b.rentAmount);
      break;
    case "priceHigh":
      list = [...list].sort((a, b) => b.rentAmount - a.rentAmount);
      break;
    case "areaHigh":
      list = [...list].sort(
        (a, b) => (b.areaSqft ?? 0) - (a.areaSqft ?? 0),
      );
      break;
    case "bedsHigh":
      list = [...list].sort(
        (a, b) => (b.bedrooms ?? 0) - (a.bedrooms ?? 0),
      );
      break;
    case "recent":
    default:
      // Backend already returns rows newest-first.
      break;
  }
  return list;
}

function parseNumber(s: string): number | null {
  if (s.trim() === "") return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

interface Chip {
  key: ChipKey;
  label: string;
}
type ChipKey =
  | "q"
  | "city"
  | "bhk"
  | "bathrooms"
  | "budget"
  | "rentMin"
  | "rentMax"
  | "areaMin"
  | "areaMax"
  | "floor"
  | "vacantOnly"
  | "furnishing"
  | "petPolicy"
  | "availableBy";

function describeActiveFilters(f: Filters, q: string): Chip[] {
  const chips: Chip[] = [];
  if (q) chips.push({ key: "q", label: `"${q}"` });
  if (f.city !== "any") chips.push({ key: "city", label: f.city });
  if (f.bhk !== "any")
    chips.push({
      key: "bhk",
      label: f.bhk === "4" ? "4+ BHK" : `${f.bhk} BHK`,
    });
  if (f.bathrooms !== "any")
    chips.push({
      key: "bathrooms",
      label:
        f.bathrooms === "3"
          ? "3+ bathrooms"
          : `${f.bathrooms} bathroom${f.bathrooms === "1" ? "" : "s"}`,
    });
  if (f.budget !== "any") {
    const [lo, hi] = f.budget.split("-").map(Number);
    chips.push({
      key: "budget",
      label: Number.isNaN(hi)
        ? `${formatINR(lo)}+`
        : `${formatINR(lo)} – ${formatINR(hi)}`,
    });
  }
  if (f.rentMin) chips.push({ key: "rentMin", label: `Rent ≥ ${f.rentMin}` });
  if (f.rentMax) chips.push({ key: "rentMax", label: `Rent ≤ ${f.rentMax}` });
  if (f.areaMin) chips.push({ key: "areaMin", label: `Area ≥ ${f.areaMin} sqft` });
  if (f.areaMax) chips.push({ key: "areaMax", label: `Area ≤ ${f.areaMax} sqft` });
  if (f.floor !== "any") chips.push({ key: "floor", label: prettyFloor(f.floor) });
  if (f.vacantOnly) chips.push({ key: "vacantOnly", label: "Vacant only" });
  if (f.furnishing !== "any")
    chips.push({ key: "furnishing", label: prettyFurnishing(f.furnishing) });
  if (f.petPolicy !== "any")
    chips.push({
      key: "petPolicy",
      label: f.petPolicy === "allowed" ? "Pet friendly" : "No pets",
    });
  if (f.availableBy) chips.push({ key: "availableBy", label: `Move-in by ${f.availableBy}` });
  return chips;
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

function prettyFloor(v: string): string {
  switch (v) {
    case "ground":
      return "Ground floor";
    case "1-3":
      return "Floor 1 – 3";
    case "4-7":
      return "Floor 4 – 7";
    case "8+":
      return "Floor 8+";
    default:
      return v;
  }
}

/** Count of filters set via the "More filters" dialog (not the bar). */
function advancedCount(f: Filters): number {
  let n = 0;
  if (f.bathrooms !== "any") n++;
  if (f.floor !== "any") n++;
  if (f.rentMin) n++;
  if (f.rentMax) n++;
  if (f.areaMin) n++;
  if (f.areaMax) n++;
  if (f.vacantOnly) n++;
  if (f.furnishing !== "any") n++;
  if (f.petPolicy !== "any") n++;
  if (f.availableBy) n++;
  return n;
}

function clearOne(
  key: ChipKey,
  setFilters: React.Dispatch<React.SetStateAction<Filters>>,
  setQ: React.Dispatch<React.SetStateAction<string>>,
) {
  if (key === "q") {
    setQ("");
    return;
  }
  setFilters((prev) => {
    const next = { ...prev };
    switch (key) {
      case "city":
      case "bhk":
      case "bathrooms":
      case "budget":
      case "floor":
        next[key] = "any";
        break;
      case "rentMin":
      case "rentMax":
      case "areaMin":
      case "areaMax":
        next[key] = "";
        break;
      case "vacantOnly":
        next.vacantOnly = false;
        break;
      case "furnishing":
        next.furnishing = "any";
        break;
      case "petPolicy":
        next.petPolicy = "any";
        break;
      case "availableBy":
        next.availableBy = "";
        break;
    }
    return next;
  });
}

/* ──────────────────────── More-filters dialog ──────────────────────── */

function MoreFiltersDialog({
  open,
  onClose,
  filters,
  setFilters,
}: {
  open: boolean;
  onClose: () => void;
  filters: Filters;
  setFilters: React.Dispatch<React.SetStateAction<Filters>>;
}) {
  // Local draft so the user can tweak knobs and apply atomically (cancel
  // restores). Mirrors NoBroker / Zillow "apply" UX.
  const [draft, setDraft] = useState<Filters>(filters);
  useEffect(() => {
    if (open) setDraft(filters);
  }, [open, filters]);

  function apply() {
    setFilters(draft);
    onClose();
  }
  function reset() {
    setDraft({
      ...DEFAULT_FILTERS,
      // Preserve the basic-bar selections — only reset the advanced knobs.
      city: filters.city,
      bhk: filters.bhk,
      budget: filters.budget,
      sort: filters.sort,
    });
  }

  return (
    <Dialog open={open} onOpenChange={(o) => (!o ? onClose() : undefined)}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>More filters</DialogTitle>
          <DialogDescription>
            Dial in the exact home — bathrooms, area, floor, and more.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          {/* Rent range */}
          <div>
            <Label className="flex items-center gap-1.5 mb-2">
              <IndianRupee className="size-3.5 text-muted-foreground" />
              Custom rent range
            </Label>
            <div className="grid grid-cols-2 gap-2">
              <Input
                type="number"
                inputMode="numeric"
                placeholder="Min ₹"
                value={draft.rentMin}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, rentMin: e.target.value }))
                }
              />
              <Input
                type="number"
                inputMode="numeric"
                placeholder="Max ₹"
                value={draft.rentMax}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, rentMax: e.target.value }))
                }
              />
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">
              Overrides the preset budget band when set.
            </p>
          </div>

          {/* Area range */}
          <div>
            <Label className="flex items-center gap-1.5 mb-2">
              <Ruler className="size-3.5 text-muted-foreground" />
              Carpet area (sq ft)
            </Label>
            <div className="grid grid-cols-2 gap-2">
              <Input
                type="number"
                inputMode="numeric"
                placeholder="Min sqft"
                value={draft.areaMin}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, areaMin: e.target.value }))
                }
              />
              <Input
                type="number"
                inputMode="numeric"
                placeholder="Max sqft"
                value={draft.areaMax}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, areaMax: e.target.value }))
                }
              />
            </div>
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            {/* Bathrooms */}
            <div>
              <Label className="flex items-center gap-1.5 mb-2">
                <Bath className="size-3.5 text-muted-foreground" />
                Bathrooms
              </Label>
              <Select
                value={draft.bathrooms}
                onValueChange={(v) =>
                  setDraft((d) => ({ ...d, bathrooms: v }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any">Any</SelectItem>
                  <SelectItem value="1">1</SelectItem>
                  <SelectItem value="2">2</SelectItem>
                  <SelectItem value="3">3+</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Floor */}
            <div>
              <Label className="flex items-center gap-1.5 mb-2">
                <Layers className="size-3.5 text-muted-foreground" />
                Floor
              </Label>
              <Select
                value={draft.floor}
                onValueChange={(v) => setDraft((d) => ({ ...d, floor: v }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any">Any floor</SelectItem>
                  <SelectItem value="ground">Ground</SelectItem>
                  <SelectItem value="1-3">1 – 3</SelectItem>
                  <SelectItem value="4-7">4 – 7</SelectItem>
                  <SelectItem value="8+">8+</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            {/* Furnishing */}
            <div>
              <Label className="flex items-center gap-1.5 mb-2">
                <Sofa className="size-3.5 text-muted-foreground" />
                Furnishing
              </Label>
              <Select
                value={draft.furnishing}
                onValueChange={(v) =>
                  setDraft((d) => ({
                    ...d,
                    furnishing: v as Filters["furnishing"],
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any">Any</SelectItem>
                  <SelectItem value="UNFURNISHED">Unfurnished</SelectItem>
                  <SelectItem value="SEMI_FURNISHED">Semi-furnished</SelectItem>
                  <SelectItem value="FULLY_FURNISHED">Fully furnished</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Pet policy */}
            <div>
              <Label className="flex items-center gap-1.5 mb-2">
                <Dog className="size-3.5 text-muted-foreground" />
                Pet policy
              </Label>
              <Select
                value={draft.petPolicy}
                onValueChange={(v) =>
                  setDraft((d) => ({
                    ...d,
                    petPolicy: v as Filters["petPolicy"],
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any">Any</SelectItem>
                  <SelectItem value="allowed">Pets allowed</SelectItem>
                  <SelectItem value="notAllowed">No pets</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Move-in date — flats available on or before. */}
          <div>
            <Label className="flex items-center gap-1.5 mb-2">
              <CalendarDays className="size-3.5 text-muted-foreground" />
              Move-in by
            </Label>
            <Input
              type="date"
              value={draft.availableBy}
              onChange={(e) =>
                setDraft((d) => ({ ...d, availableBy: e.target.value }))
              }
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              Shows flats available on or before this date. Listings
              without a move-in date are hidden when a date is set.
            </p>
          </div>

          {/* Availability — vacant only. */}
          <div>
            <Label className="flex items-center gap-1.5 mb-2">
              <Eye className="size-3.5 text-muted-foreground" />
              Availability
            </Label>
            <button
              type="button"
              onClick={() =>
                setDraft((d) => ({ ...d, vacantOnly: !d.vacantOnly }))
              }
              className={`w-full text-left rounded-lg border px-3 py-2.5 text-sm transition-colors ${
                draft.vacantOnly
                  ? "border-primary bg-primary/5 text-foreground"
                  : "border-border hover:bg-secondary/40 text-muted-foreground"
              }`}
            >
              <span className="font-medium">Show only vacant homes</span>
              <p className="text-[11px] mt-0.5">
                Hide flats that already have a tenant.
              </p>
            </button>
          </div>

          {/* Helpful preview — what city / BHK / budget are currently set
              outside this dialog. Stops the user from over-filtering and
              wondering why nothing matches. */}
          <div className="rounded-lg bg-secondary/40 p-3 text-xs text-muted-foreground">
            <p className="font-medium text-foreground mb-1.5 flex items-center gap-1.5">
              <Building2 className="size-3.5" /> Also applying:
            </p>
            <div className="flex flex-wrap gap-1.5">
              <ContextChip
                icon={MapPin}
                label={filters.city === "any" ? "Any city" : filters.city}
              />
              <ContextChip
                icon={Bed}
                label={
                  filters.bhk === "any"
                    ? "Any BHK"
                    : filters.bhk === "4"
                      ? "4+ BHK"
                      : `${filters.bhk} BHK`
                }
              />
              <ContextChip
                icon={IndianRupee}
                label={
                  filters.budget === "any"
                    ? "Any budget"
                    : prettyBudget(filters.budget)
                }
              />
            </div>
          </div>
        </div>

        <div className="flex justify-between gap-2 pt-2">
          <Button variant="ghost" size="sm" onClick={reset}>
            Reset advanced
          </Button>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="gradient" size="sm" onClick={apply}>
              Apply filters
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function ContextChip({
  icon: Icon,
  label,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-background border px-2 py-0.5">
      <Icon className="size-3" />
      {label}
    </span>
  );
}

function prettyBudget(v: string): string {
  const [lo, hi] = v.split("-").map(Number);
  if (Number.isNaN(hi)) return `${formatINR(lo)}+`;
  return `${formatINR(lo)} – ${formatINR(hi)}`;
}
