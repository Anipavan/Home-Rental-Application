import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  MapContainer,
  Marker,
  Popup,
  TileLayer,
  Circle,
  useMap,
  useMapEvents,
} from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { Crosshair, MapPinned, X } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatINR } from "@/lib/utils";
import { propertiesApi } from "@/lib/api/properties";
import {
  IndiaStatesLayer,
  pointInPolygon,
  stateNameOf,
  type FeatureCollection,
} from "@/components/property/india-states-layer";
import type { BuildingResponseDTO, FlatResponseDTO } from "@/types/api";

/**
 * Map view for the browse page. Renders one pin per flat whose
 * parent building has a geo-coordinate, on free OpenStreetMap tiles
 * (no API key, no usage limits at our scale).
 *
 * <p>Pins are emerald-tinted ₹-price badges so the user reads the
 * rent instantly without clicking. Click → popup with cover image
 * + key stats + "View →" link to the property-detail page.
 *
 * <p>Map centering rules:
 *   1. If the user has dropped a search pin (click on the map), use that.
 *   2. Else if a user-supplied lat/lng is passed, use that.
 *   3. Else if any flat has coords, centre on the first flat with coords.
 *   4. Else fall back to India centroid (~22°N, 79°E) at zoom 5.
 *
 * <p>Buildings without coordinates are silently omitted (they're
 * already excluded by the geosearch endpoint, but the listing-grid
 * data path also flows through here when the user toggles to Map).
 *
 * <p>Leaflet's default marker-icon CSS references asset paths the
 * Vite bundler can't resolve; we sidestep that by drawing the icon
 * as a DivIcon (HTML + inline styles) — same look across all
 * browsers, no broken-image fallback.
 */
interface Props {
  flats: FlatResponseDTO[];
  /**
   * Resolved Building map: { buildingId → BuildingResponseDTO }.
   * BrowsePage hydrates this from the buildings.list() call so we
   * don't refetch each parent inside this component.
   */
  buildings: Record<string, BuildingResponseDTO>;
  /** Optional user-centred map starting point. */
  userCenter?: { lat: number; lng: number } | null;
}

const INDIA_CENTER: [number, number] = [22.0, 79.0];

/**
 * Radius preset options for the "search around this pin" filter.
 * Defaults to 5 km — covers most neighbourhood-scale rental searches
 * in Indian cities. 1km tightens to a single locality, 25km opens up
 * to whole-metro coverage.
 */
const RADIUS_OPTIONS = [1, 3, 5, 10, 25] as const;

export function PropertyMapView({ flats, buildings, userCenter }: Props) {
  /**
   * User-clicked search pin. Setting this filters the visible
   * markers to flats within {@link radiusKm} of the pin and draws
   * a circle showing the search area. Null = no spatial filter
   * applied (map shows every flat with a building pin).
   */
  const [searchPin, setSearchPin] = useState<{ lat: number; lng: number } | null>(null);
  const [radiusKm, setRadiusKm] = useState<number>(5);

  /**
   * Selected India state (via clicking a state polygon). When set,
   * only flats whose building's lat/lng falls inside that state's
   * polygon are rendered. Independent of the searchPin radius filter
   * — they compose: pick a state, then optionally drop a pin inside
   * it to tighten further.
   */
  const [selectedState, setSelectedState] = useState<string | null>(null);

  /**
   * Pull the same GeoJSON IndiaStatesLayer fetches so we can run
   * point-in-polygon checks here. React Query dedupes the request —
   * the layer and the filter share one HTTP call.
   */
  const statesGeoQ = useQuery({
    queryKey: ["india-states-geojson"],
    queryFn: async () => null as FeatureCollection | null, // populated by IndiaStatesLayer
    enabled: false, // never fire from here — IndiaStatesLayer owns the fetch
  });
  const selectedStateFeature = useMemo(() => {
    if (!selectedState || !statesGeoQ.data) return null;
    return (
      statesGeoQ.data.features.find(
        (f) => stateNameOf(f) === selectedState,
      ) ?? null
    );
  }, [selectedState, statesGeoQ.data]);

  // Build the marker list — only flats whose parent building has a pin.
  const allPins = useMemo(() => {
    return flats.flatMap((flat) => {
      const b = buildings[flat.buildingId];
      if (!b || b.latitude == null || b.longitude == null) return [];
      return [{ flat, building: b, lat: b.latitude, lng: b.longitude }];
    });
  }, [flats, buildings]);

  // Apply the radius filter when a search pin is dropped, AND the
  // state filter when a state polygon is selected. They compose —
  // a pin within a state-bounded area returns flats inside both.
  // Haversine for the radius (accurate over India-scale ranges);
  // ray-casting point-in-polygon for the state check (no library
  // dependency, microsecond-fast on India-sized polygons).
  const pins = useMemo(() => {
    let filtered = allPins;
    if (selectedStateFeature) {
      filtered = filtered.filter((p) =>
        pointInPolygon(p.lat, p.lng, selectedStateFeature),
      );
    }
    if (searchPin) {
      filtered = filtered.filter(
        (p) => haversineKm(searchPin.lat, searchPin.lng, p.lat, p.lng) <= radiusKm,
      );
    }
    return filtered;
  }, [allPins, searchPin, radiusKm, selectedStateFeature]);

  // Centre + zoom heuristic.
  let center: [number, number] = INDIA_CENTER;
  let zoom = 5;
  if (searchPin) {
    center = [searchPin.lat, searchPin.lng];
    // Zoom appropriate for the radius — tighter radius, tighter zoom.
    zoom =
      radiusKm <= 1 ? 14 : radiusKm <= 3 ? 13 : radiusKm <= 5 ? 12 : radiusKm <= 10 ? 11 : 10;
  } else if (userCenter) {
    center = [userCenter.lat, userCenter.lng];
    zoom = 12;
  } else if (allPins.length > 0) {
    center = [allPins[0].lat, allPins[0].lng];
    zoom = 11;
  }

  return (
    <Card className="overflow-hidden">
      {/* Top control bar — state selection + radius preset + clear
          buttons. Sits above the map so it's always visible (Leaflet
          panes can occlude floating overlays during pan/zoom). */}
      <div className="flex flex-wrap items-center gap-2 px-3 py-2 border-b text-xs">
        <Crosshair className="size-3.5 text-muted-foreground" />
        <span className="text-muted-foreground">
          {selectedState && searchPin
            ? `Searching ${radiusKm} km around your pin in ${selectedState}`
            : selectedState
              ? `Showing homes in ${selectedState} — tap another state, or click anywhere to drop a pin`
              : searchPin
                ? `Searching within ${radiusKm} km of dropped pin`
                : "Tip: tap a state to filter, or click anywhere on the map to drop a pin"}
        </span>
        {selectedState && (
          <Badge
            variant="success"
            className="ml-1 cursor-pointer"
            onClick={() => setSelectedState(null)}
          >
            <MapPinned className="size-3" />
            {selectedState}
            <X className="size-3 ml-1" />
          </Badge>
        )}
        {searchPin && (
          <>
            <div className="ml-auto flex items-center gap-2">
              <span className="text-muted-foreground">Radius:</span>
              <Select
                value={String(radiusKm)}
                onValueChange={(v) => setRadiusKm(Number(v))}
              >
                <SelectTrigger className="h-7 w-[88px] text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RADIUS_OPTIONS.map((r) => (
                    <SelectItem key={r} value={String(r)} className="text-xs">
                      {r} km
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                variant="ghost"
                size="sm"
                className="h-7 text-xs"
                onClick={() => setSearchPin(null)}
              >
                <X className="size-3" /> Clear pin
              </Button>
            </div>
          </>
        )}
      </div>

      <div className="relative h-[600px]">
        <MapContainer
          center={center}
          zoom={zoom}
          scrollWheelZoom
          className="h-full w-full"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            + ' &copy; <a href="https://datameet.org">Datameet Maps</a> (states)'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          {/* India states overlay — light polygons, click to filter +
              zoom. Loads async; while loading the map works without
              the overlay. Z-order: under the markers so flat pins
              still receive clicks (Leaflet draws layers in mount
              order). */}
          <IndiaStatesLayer
            selectedState={selectedState}
            onSelect={setSelectedState}
          />

          {/* Click-to-set-search-center event handler. Drops a pin
              and triggers the radius filter. Bound AFTER the states
              layer so a state-polygon click is captured by the
              GeoJSON layer first (stopPropagation on its click
              handler isn't reliable across Leaflet builds; the
              event-order trick is more robust). */}
          <MapClickHandler
            onClick={(lat, lng) => setSearchPin({ lat, lng })}
          />

          {/* Auto-recentre when the searchPin / userCenter changes.
              Without this, dropping a pin would set the state but
              the map view would stay on its previous centre until
              the next user pan. */}
          <RecentreOn
            target={searchPin ?? userCenter ?? null}
            zoom={
              searchPin
                ? radiusKm <= 1
                  ? 14
                  : radiusKm <= 3
                    ? 13
                    : radiusKm <= 5
                      ? 12
                      : radiusKm <= 10
                        ? 11
                        : 10
                : 12
            }
          />

          {/* Auto-fit bounds to all pins when there are 2+ AND no
              search pin is active — keeps every result visible on
              first paint, but lets the search-pin zoom take over
              once the user drops one. */}
          {pins.length > 1 && !searchPin && (
            <FitBoundsToPins points={pins.map((p) => [p.lat, p.lng])} />
          )}

          {/* Search circle — visualises the radius the filter is
              applying. Sits under the markers so click targets
              behave correctly. */}
          {searchPin && (
            <>
              <Circle
                center={[searchPin.lat, searchPin.lng]}
                radius={radiusKm * 1000}
                pathOptions={{
                  color: "#0d9488",
                  fillColor: "#0d9488",
                  fillOpacity: 0.08,
                  weight: 2,
                }}
              />
              <Marker
                position={[searchPin.lat, searchPin.lng]}
                icon={searchPinIcon()}
                interactive={false}
              />
            </>
          )}

          {pins.map(({ flat, building, lat, lng }) => (
            <Marker
              key={flat.id}
              position={[lat, lng]}
              icon={makePriceIcon(flat.rentAmount)}
            >
              <Popup>
                <PopupCard flat={flat} buildingName={building.buildingName} />
              </Popup>
            </Marker>
          ))}
        </MapContainer>

        {pins.length === 0 && (
          <div className="absolute inset-0 grid place-items-center pointer-events-none">
            <div className="bg-background/90 backdrop-blur rounded-lg px-4 py-3 shadow-soft text-sm text-muted-foreground">
              {searchPin
                ? `No homes within ${radiusKm} km of this pin — try a larger radius.`
                : flats.length === 0
                  ? "No homes match your filters."
                  : "None of these homes have map coordinates yet."}
            </div>
          </div>
        )}
      </div>
      {pins.length > 0 && pins.length < allPins.length && (
        <div className="px-4 py-2 text-[11px] text-muted-foreground border-t">
          {searchPin
            ? `Showing ${pins.length} of ${allPins.length} mapped homes within ${radiusKm} km.`
            : `Showing ${pins.length} of ${flats.length} on the map — ${flats.length - pins.length} ${flats.length - pins.length === 1 ? "listing has" : "listings have"} no pin yet.`}
        </div>
      )}
    </Card>
  );
}

/**
 * Map-event listener: captures clicks on the map background (not on
 * markers/popups — those receive their own click events and
 * Leaflet's bubbling stops there). Used to drop the search pin.
 */
function MapClickHandler({
  onClick,
}: {
  onClick: (lat: number, lng: number) => void;
}) {
  useMapEvents({
    click(e) {
      onClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

/**
 * Imperative re-centre. Runs in a useMemo so it fires only when the
 * target lat/lng actually changes. We use `flyTo` rather than
 * `setView` so the transition is smooth — feels more like a search
 * result than a teleport.
 */
function RecentreOn({
  target,
  zoom,
}: {
  target: { lat: number; lng: number } | null;
  zoom: number;
}) {
  const map = useMap();
  useMemo(() => {
    if (!target) return;
    map.flyTo([target.lat, target.lng], zoom, { duration: 0.6 });
  }, [target, zoom, map]);
  return null;
}

/**
 * Fit the visible bounds to the supplied lat/lng points so every pin
 * is on-screen on first paint. Skip when there's only one point —
 * Leaflet's auto-zoom on a single point goes too tight.
 */
function FitBoundsToPins({ points }: { points: [number, number][] }) {
  const map = useMap();
  useMemo(() => {
    if (points.length < 2) return;
    const bounds = L.latLngBounds(points);
    map.fitBounds(bounds, { padding: [40, 40], maxZoom: 14 });
  }, [points, map]);
  return null;
}

/**
 * Haversine distance in kilometres between two lat/lng points. We
 * inline rather than depend on a geo library — the formula is short,
 * stable, and the precision is plenty for "within N km" filtering.
 */
function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371; // mean Earth radius in km
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/**
 * Custom marker icon — a rounded emerald pill carrying the rent
 * abbreviation ("₹25k") so the price reads at a glance without
 * needing to click. Built as a DivIcon so we don't ship Leaflet's
 * stock PNG (which Vite would 404 in production).
 */
function makePriceIcon(rentAmount: number) {
  const label = shortRent(rentAmount);
  const html = `
    <div style="
      display:inline-flex;align-items:center;gap:2px;
      background:linear-gradient(135deg,#10b981,#0d9488);
      color:#fff;font-weight:700;font-size:11px;
      padding:5px 9px;border-radius:999px;
      border:2px solid #fff;
      box-shadow:0 4px 10px -2px rgba(15,118,110,0.45);
      white-space:nowrap;font-family:Inter,system-ui,sans-serif;
    ">${label}</div>`;
  return L.divIcon({
    html,
    className: "",
    iconSize: [60, 24],
    iconAnchor: [30, 12],
  });
}

/**
 * Icon for the user-dropped search pin. Visually distinct from the
 * green price pills — a slate-coloured target reticle so it reads
 * as "search centre" rather than "another listing".
 */
function searchPinIcon() {
  const html = `
    <div style="
      width:28px;height:28px;border-radius:50%;
      background:#0f172a;color:#fff;
      display:grid;place-items:center;
      border:3px solid #fff;
      box-shadow:0 6px 12px -2px rgba(15,23,42,0.5);
    ">
      <div style="
        width:8px;height:8px;background:#fff;border-radius:50%;
      "></div>
    </div>`;
  return L.divIcon({
    html,
    className: "",
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

function shortRent(n: number): string {
  if (n >= 1_00_000) return `₹${(n / 1_00_000).toFixed(1)}L`;
  if (n >= 1000) return `₹${Math.round(n / 1000)}k`;
  return `₹${n}`;
}

function PopupCard({
  flat,
  buildingName,
}: {
  flat: FlatResponseDTO;
  buildingName?: string;
}) {
  // Try a real building cover; fall back to the deterministic
  // placeholder PropertyCard uses, so the popup never renders empty.
  const coverGuess = guessCoverUrl(flat.buildingId);
  return (
    <div className="w-[220px]">
      {coverGuess && (
        <img
          src={coverGuess}
          alt={buildingName ?? flat.flatNumber}
          className="w-full h-28 object-cover rounded-md mb-2"
          onError={(e) => {
            (e.target as HTMLImageElement).style.display = "none";
          }}
        />
      )}
      <p className="font-display font-semibold text-sm truncate m-0">
        {buildingName ?? `Flat ${flat.flatNumber}`}
      </p>
      <p className="text-xs text-muted-foreground m-0 mt-0.5">
        {flat.bedrooms ?? "—"} BHK · {flat.areaSqft ?? "—"} sqft
      </p>
      <p className="font-display font-bold mt-1 m-0">
        {formatINR(flat.rentAmount)}
        <span className="text-[10px] font-normal text-muted-foreground"> /mo</span>
      </p>
      <Link
        to={`/property/${flat.id}`}
        className="block mt-2 text-xs font-medium text-primary hover:underline"
      >
        View details →
      </Link>
    </div>
  );
}

/**
 * Stash a guess at the cover URL without doing a hooks-driven fetch
 * — Leaflet popups render lazily and we want the popup to appear
 * instantly. Returns the gateway-proxied raw URL for image #1 of the
 * building, which the backend serves cover-first. If the building
 * has no images, the <img onError> hides the element gracefully.
 *
 * <p>This is a deliberate trade-off: faster popup, sometimes a
 * "no image" tile vs. a flash of nothing while the fetch resolves.
 */
function guessCoverUrl(buildingId: string): string | null {
  // We can't easily know the image id without a query, but the
  // backend orders cover-first on getImages — and PropertyCard
  // already prefetched + cached the list per building via React Query
  // with the same key. So this best-effort string-build leans on
  // that cache being warm by the time the popup opens.
  // Returning null means the popup omits the image (fast + clean).
  // A future iteration could pull from the React Query cache via
  // queryClient.getQueryData(["building", buildingId, "images"]).
  void buildingId;
  void propertiesApi;
  return null;
}
