import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Bath,
  Bed,
  BellPlus,
  Building2,
  CalendarDays,
  Dog,
  Eye,
  IndianRupee,
  LayoutGrid,
  Layers,
  Loader2,
  LocateFixed,
  Map as MapIcon,
  MapPin,
  Ruler,
  Search,
  SlidersHorizontal,
  Sofa,
  Sparkles,
  Users,
  X,
} from "lucide-react";
import { toast } from "@/hooks/use-toast";
import { propertiesApi, savedSearchesApi } from "@/lib/api/properties";
import { useAuthStore } from "@/stores/auth-store";
import { PropertyCard } from "@/components/property/property-card";
import { PropertyMapView } from "@/components/property/property-map";
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
import {
  cn,
  formatINR,
  canonicalCity,
  citySearchTokens,
  sameCity,
} from "@/lib/utils";
import type { BuildingResponseDTO, FlatResponseDTO } from "@/types/api";

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

/**
 * Canonical amenity keywords used by the multi-select chip filter.
 * Each entry pairs a display label with the lowercased keyword used
 * for fuzzy matching against the building's free-text `amenities`
 * column. Matching is substring + case-insensitive so a building
 * that wrote "Lifts available" still matches the "Lift" chip.
 *
 * Keep this list aligned with the icon picker on
 * pages/public/property-detail.tsx so the chips visually mirror
 * what the user will see on the listing.
 */
export const AMENITY_OPTIONS: Array<{ key: string; label: string; match: RegExp }> = [
  { key: "wifi", label: "Wi-Fi", match: /wi[-\s]?fi|internet|broadband/i },
  { key: "parking", label: "Car parking", match: /car park|parking|garage/i },
  { key: "gym", label: "Gym", match: /gym|fitness/i },
  { key: "pool", label: "Swimming pool", match: /pool|swim/i },
  { key: "garden", label: "Garden", match: /garden|lawn|park\b/i },
  { key: "lift", label: "Lift", match: /lift|elevator/i },
  { key: "security", label: "24/7 security", match: /security|cctv|guard/i },
  { key: "power", label: "Power back-up", match: /power[-\s]?back|generator|inverter|backup/i },
];

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
  /** Tenant-preference filter. "any" = ignore. "bachelor" = only
   *  flats where acceptsBachelor=true. "family" = only flats where
   *  acceptsFamily=true. Single-pick rather than multi because a
   *  renter is one or the other for any given search. */
  tenantPref: "any" as "any" | "bachelor" | "family",
  /**
   * Multi-select amenity keys (see {@link AMENITY_OPTIONS}). A flat
   * matches when its parent building's free-text `amenities` field
   * contains EVERY selected keyword (AND semantics, not OR). Empty
   * array = no amenity filter applied.
   */
  amenityKeys: [] as string[],
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
  // List vs Map view — defaults to list because that's what users
  // expect to see first; the map toggle is for spatially-oriented
  // search (commute-time, neighborhood feel) that NoBroker/99acres
  // also lead with after a city is picked.
  const [viewMode, setViewMode] = useState<"list" | "map">("list");
  // Coords from the geolocation flow; passed to the map so it can
  // centre on the user when they hit "Use my location" + switch to map.
  const [userCoords, setUserCoords] = useState<{ lat: number; lng: number } | null>(null);

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

  // Buildings query — needed by:
  //   1. The Map view, to resolve each flat's lat/lng (the geo-pin
  //      lives on the parent Building, not the Flat).
  //   2. The amenity filter on the list view (amenities are a
  //      free-text field on Building too).
  // Always-fires now — buildings are a small table (~200 rows max
  // at this scale), cached 5min, and the marginal payload is
  // dwarfed by the flat list itself.
  const buildingsQ = useQuery({
    queryKey: ["buildings", "all"],
    queryFn: () => propertiesApi.buildings.list(0, 200),
    staleTime: 5 * 60_000,
  });
  const buildingsById = useMemo(() => {
    const map: Record<string, BuildingResponseDTO> = {};
    for (const b of buildingsQ.data?.content ?? []) {
      map[b.buildingId] = b;
    }
    return map;
  }, [buildingsQ.data]);

  // Derive the list of cities from results so the dropdown only offers
  // cities that actually have listings — no dead options. Dedupe by
  // canonical city name so "Bengaluru" and "Bangalore" collapse into a
  // single row (we keep the first spelling we see — usually the modern
  // canonical form that the create-building dropdown enforces).
  const cities = useMemo(() => {
    const seen = new Map<string, string>();
    for (const f of data?.content ?? []) {
      if (!f.buildingCity) continue;
      const key = canonicalCity(f.buildingCity);
      if (!seen.has(key)) seen.set(key, f.buildingCity);
    }
    return [...seen.values()].sort();
  }, [data]);

  const filtered = useMemo(
    () => applyFilters(data?.content ?? [], q, filters, buildingsById),
    [data, q, filters, buildingsById],
  );

  const activeChips = describeActiveFilters(filters, q);
  const { isAuthenticated } = useAuthStore();

  /**
   * Save-this-search → POST to /properties/saved-searches with the
   * current filter state mapped to the backend's predicate shape.
   * Sign-in is required (the controller reads the user id from
   * X-Auth-User-Id) — for anonymous visitors the CTA collapses to
   * a "Sign in to save" link.
   */
  const saveMut = useMutation({
    mutationFn: () => savedSearchesApi.create(filtersToSavedSearch(filters)),
    onSuccess: () =>
      toast({
        title: "Alert saved!",
        description:
          "We'll email you when new homes match this search. Manage alerts under My Saved Searches.",
      }),
    onError: () =>
      toast({
        variant: "destructive",
        title: "Couldn't save",
        description: "Please try again in a moment.",
      }),
  });
  const canSaveSearch = activeChips.length > 0; // pointless to save an empty search

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

    // ────────── Phase 1: get browser coordinates ──────────
    // The reverse-geocode fetch was bundled into this same try/catch
    // before, which meant a BigDataCloud rate-limit / DNS error /
    // CORS hiccup looked the same as the user denying permission.
    // Splitting them: if we get coords but the city lookup fails,
    // we still hand the user useful behaviour (map view centred on
    // them) instead of bailing out entirely. A more diagnostic toast
    // is also surfaced so support knows whether the issue is
    // permissions, timeout, or the third-party geocoder.
    let position: GeolocationPosition;
    try {
      position = await new Promise<GeolocationPosition>((resolve, reject) =>
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: false,
          // Increased from 10s → 20s. On mobile India networks the
          // initial GPS lock can take 12-15s, and the previous 10s
          // ceiling tipped a real-but-slow request into a timeout
          // error indistinguishable from "location unavailable".
          timeout: 20_000,
          maximumAge: 60_000,
        }),
      );
    } catch (err) {
      const code = (err as GeolocationPositionError | undefined)?.code;
      const msg =
        code === 1
          ? "Location permission denied. You can still pick a city from the dropdown."
          : code === 2
            ? "Location unavailable — your device may not have GPS or the signal is weak."
            : code === 3
              ? "Location request timed out after 20 seconds. Move to an open area or try again."
              : "Couldn't determine your location.";
      toast({ variant: "destructive", title: "Location failed", description: msg });
      setLocating(false);
      return;
    }

    const { latitude, longitude } = position.coords;
    // Stash the precise coords for the map view — the city filter
    // is coarse, but the map can centre exactly on the user. Done
    // BEFORE the reverse-geocode call so the map still works even
    // when BigDataCloud is rate-limited / unreachable.
    setUserCoords({ lat: latitude, lng: longitude });

    // ────────── Phase 2: reverse-geocode to a city name ──────────
    // Best-effort: when the third-party API fails we just tell the
    // user "got your location but couldn't name the city" and let
    // them switch to map view manually.
    try {
      const url = new URL(
        "https://api.bigdatacloud.net/data/reverse-geocode-client",
      );
      url.searchParams.set("latitude", String(latitude));
      url.searchParams.set("longitude", String(longitude));
      url.searchParams.set("localityLanguage", "en");
      const resp = await fetch(url.toString());
      if (!resp.ok) {
        throw new Error(`Reverse geocode failed (HTTP ${resp.status})`);
      }
      const body = (await resp.json()) as {
        city?: string;
        locality?: string;
        principalSubdivision?: string;
      };
      const detectedCity =
        body.city ?? body.locality ?? body.principalSubdivision ?? "";
      if (!detectedCity) {
        // Got an HTTP 200 with no recognisable city field — surfaces
        // the same UX as a hard failure but keeps the coords cached
        // so the map view still works.
        toast({
          title: "Got your location",
          description:
            "Couldn't resolve your city — try the map view to see homes near you.",
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
          description: `We don't have listings there yet — try the map view to see what's near you.`,
        });
      }
    } catch (geocodeErr) {
      // Reverse-geocoding failed but we DO have coordinates — the
      // map view will still work, we just can't auto-filter by city.
      console.warn("Reverse geocode failed:", geocodeErr);
      toast({
        title: "Got your location",
        description:
          "City lookup service is unavailable right now — switch to map view to see homes near you.",
      });
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
              // Audit M30: chip buttons mix label text + an X icon
              // — explicit aria-label tells screen readers what
              // clicking the chip does. The X glyph alone is
              // ambiguous out of context.
              aria-label={`Remove filter: ${chip.label}`}
              className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 text-primary text-xs font-medium pl-3 pr-2 py-1 hover:bg-primary/15 transition-colors"
            >
              {chip.label}
              <X className="size-3.5" aria-hidden="true" />
            </button>
          ))}
          <button
            type="button"
            onClick={resetAll}
            className="text-xs text-muted-foreground hover:text-foreground underline underline-offset-2 ml-1"
          >
            Clear all
          </button>
          {/* Save-this-search CTA. Sits at the end of the chip row so
              users see it after refining their filters — same flow as
              99acres / housing.com. */}
          {canSaveSearch && (
            isAuthenticated ? (
              <Button
                size="sm"
                variant="outline"
                className="ml-auto gap-1.5 h-7"
                onClick={() => saveMut.mutate()}
                disabled={saveMut.isPending}
              >
                {saveMut.isPending ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <BellPlus className="size-3.5" />
                )}
                Save this search
              </Button>
            ) : (
              <Link
                to="/login"
                className="ml-auto inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:text-foreground hover:bg-muted/40 transition-colors"
              >
                <BellPlus className="size-3.5" /> Sign in to save this search
              </Link>
            )
          )}
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
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <p className="text-sm text-muted-foreground">
              Showing{" "}
              <span className="font-semibold text-foreground">
                {filtered.length}
              </span>{" "}
              {filtered.length === 1 ? "home" : "homes"}
              {filters.city !== "any" && <> in {filters.city}</>}
            </p>
            {/* List / Map view toggle. Map mode lazy-fetches the
                buildings catalog (200 max) to resolve geo pins. */}
            <div className="inline-flex rounded-lg border bg-background p-0.5">
              <button
                type="button"
                onClick={() => setViewMode("list")}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium transition-colors",
                  viewMode === "list"
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <LayoutGrid className="size-3.5" /> List
              </button>
              <button
                type="button"
                onClick={() => setViewMode("map")}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium transition-colors",
                  viewMode === "map"
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <MapIcon className="size-3.5" /> Map
              </button>
            </div>
          </div>
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
          ) : viewMode === "map" ? (
            // Audit M23: handle the map's buildings-query failure /
            // empty-state explicitly. Without this the map renders
            // with zero pins and no signal to the user that something
            // went wrong upstream.
            buildingsQ.isError ? (
              <Card className="p-12 text-center">
                <p className="font-display font-semibold text-lg">
                  Couldn't load the map data
                </p>
                <p className="text-muted-foreground text-sm mt-1">
                  Try the list view, or refresh the page in a minute.
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-4"
                  onClick={() => setViewMode("list")}
                >
                  Switch to list view
                </Button>
              </Card>
            ) : (
              <PropertyMapView
                flats={filtered}
                buildings={buildingsById}
                userCenter={userCoords}
              />
            )
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
  buildingsById: Record<string, BuildingResponseDTO>,
): FlatResponseDTO[] {
  let list = rows;

  if (q) {
    const needle = q.toLowerCase();
    list = list.filter((flat) => {
      // The haystack includes the stored city plus every known alias —
      // so a tenant typing "bangalore" finds flats stored as "Bengaluru"
      // (and vice-versa). citySearchTokens returns ["bengaluru","bangalore"]
      // for either input, which we splat in next to the other fields.
      const hay = [
        flat.flatNumber,
        flat.buildingName,
        flat.buildingAddress,
        flat.buildingCity,
        ...citySearchTokens(flat.buildingCity),
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return hay.includes(needle);
    });
  }

  if (f.city !== "any") {
    // Alias-aware equality — picking "Bengaluru" from the dropdown also
    // matches a building stored as "Bangalore" (could happen for any
    // owner-typed value that bypassed the create-form's canonical
    // dropdown, or for legacy data seeded before normalization).
    list = list.filter((flat) => sameCity(flat.buildingCity, f.city));
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

  // Tenant preference. Legacy rows where the field is null are
  // treated as TRUE (matches the backend default-open contract).
  if (f.tenantPref === "bachelor") {
    list = list.filter((flat) => flat.acceptsBachelor !== false);
  } else if (f.tenantPref === "family") {
    list = list.filter((flat) => flat.acceptsFamily !== false);
  }

  // Amenity multi-select. AND semantics — a flat matches when its
  // parent building's amenities text matches EVERY selected
  // keyword. Substring + case-insensitive so "Lifts available"
  // matches the "Lift" chip and "Free Wi-Fi" matches "Wi-Fi".
  if (f.amenityKeys.length > 0) {
    const selectedPatterns = f.amenityKeys
      .map((k) => AMENITY_OPTIONS.find((o) => o.key === k))
      .filter((o): o is (typeof AMENITY_OPTIONS)[number] => Boolean(o))
      .map((o) => o.match);
    list = list.filter((flat) => {
      const haystack = buildingsById[flat.buildingId]?.amenities ?? "";
      if (!haystack) return false;
      return selectedPatterns.every((p) => p.test(haystack));
    });
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

/**
 * Map the in-memory filter state to the backend SavedSearchRequest
 * shape. The backend predicate model is intentionally narrower than
 * the UI (e.g. no floor band, no availableBy) — those advanced
 * filters are still applied on the public browse page in real-time,
 * but the alerter only fires on the core (city / BHK / rent / area /
 * furnishing / pet) predicates that 99% of renters actually use.
 */
function filtersToSavedSearch(f: Filters) {
  const [lo, hi] = f.budget !== "any" ? f.budget.split("-").map(Number) : [];
  const rentMin = parseNumber(f.rentMin) ?? (Number.isFinite(lo) ? lo! : null);
  const rentMax = parseNumber(f.rentMax) ?? (Number.isFinite(hi) ? hi! : null);
  return {
    city: f.city !== "any" ? f.city : null,
    bedrooms: f.bhk !== "any" ? Number(f.bhk) : null,
    minRent: rentMin,
    maxRent: rentMax,
    minAreaSqft: parseNumber(f.areaMin),
    furnishingStatus: f.furnishing !== "any" ? f.furnishing : null,
    petFriendly: f.petPolicy === "allowed" ? true : null,
    isActive: true,
  };
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
  | "availableBy"
  | "tenantPref"
  | "amenityKeys";

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
  if (f.tenantPref !== "any") {
    chips.push({
      key: "tenantPref",
      label: f.tenantPref === "bachelor" ? "Bachelor-friendly" : "Family-friendly",
    });
  }
  if (f.amenityKeys.length > 0) {
    const labels = f.amenityKeys
      .map((k) => AMENITY_OPTIONS.find((o) => o.key === k)?.label)
      .filter(Boolean) as string[];
    chips.push({
      key: "amenityKeys",
      label:
        labels.length === 1
          ? labels[0]
          : `${labels.length} amenities`,
    });
  }
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
  if (f.tenantPref !== "any") n++;
  if (f.amenityKeys.length > 0) n++;
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
      case "tenantPref":
        next.tenantPref = "any";
        break;
      case "amenityKeys":
        next.amenityKeys = [];
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
      {/* On viewports shorter than the filter list, the dialog used
          to overflow off the bottom of the screen with no way to
          scroll inside it. Cap at 85vh, make the body scrollable. */}
      <DialogContent className="max-w-xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>More filters</DialogTitle>
          <DialogDescription>
            Dial in the exact home — bathrooms, area, floor, and more.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5 overflow-y-auto pr-1 -mr-1 flex-1">
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

          {/* Tenant preference — who the listing is open to. Renders
              as a 3-button segmented control: Any / Bachelor / Family.
              Matches the owner-side toggle on the flat-new form. */}
          <div>
            <Label className="flex items-center gap-1.5 mb-2">
              <Users className="size-3.5 text-muted-foreground" />
              Tenant preference
            </Label>
            <div className="grid grid-cols-3 gap-1.5 text-sm">
              {([
                { v: "any", label: "Any" },
                { v: "bachelor", label: "Bachelor" },
                { v: "family", label: "Family" },
              ] as const).map((opt) => (
                <button
                  key={opt.v}
                  type="button"
                  onClick={() =>
                    setDraft((d) => ({ ...d, tenantPref: opt.v }))
                  }
                  className={cn(
                    "rounded-lg border px-2 py-2 transition-colors",
                    draft.tenantPref === opt.v
                      ? "border-primary bg-primary/5 text-foreground font-medium"
                      : "border-border hover:bg-secondary/40 text-muted-foreground",
                  )}
                >
                  {opt.label}
                </button>
              ))}
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">
              Filters by what the owner has marked as acceptable. "Any"
              shows every listing.
            </p>
          </div>

          {/* Amenity multi-select chips. Each chip toggles independently;
              selecting multiple applies AND semantics — flats must have
              ALL chosen amenities in their parent building's free-text
              amenity list. */}
          <div>
            <Label className="flex items-center gap-1.5 mb-2">
              <Sparkles className="size-3.5 text-muted-foreground" />
              Amenities (all selected required)
            </Label>
            <div className="flex flex-wrap gap-1.5">
              {AMENITY_OPTIONS.map((opt) => {
                const on = draft.amenityKeys.includes(opt.key);
                return (
                  <button
                    key={opt.key}
                    type="button"
                    onClick={() =>
                      setDraft((d) => ({
                        ...d,
                        amenityKeys: on
                          ? d.amenityKeys.filter((k) => k !== opt.key)
                          : [...d.amenityKeys, opt.key],
                      }))
                    }
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                      on
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-border text-muted-foreground hover:bg-secondary/40",
                    )}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
            {draft.amenityKeys.length > 1 && (
              <p className="text-[11px] text-muted-foreground mt-1.5">
                {draft.amenityKeys.length} selected — only flats with ALL
                of these will appear.
              </p>
            )}
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
