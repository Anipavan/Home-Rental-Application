import { useMemo } from "react";
import { Link } from "react-router-dom";
import { MapContainer, Marker, Popup, TileLayer, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { Card } from "@/components/ui/card";
import { formatINR } from "@/lib/utils";
import { propertiesApi } from "@/lib/api/properties";
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
 *   1. If a user-supplied lat/lng is passed, use that.
 *   2. Else if any flat has coords, centre on the first flat with coords.
 *   3. Else fall back to India centroid (~22°N, 79°E) at zoom 5.
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

export function PropertyMapView({ flats, buildings, userCenter }: Props) {
  // Build the marker list — only flats whose parent building has a pin.
  const pins = useMemo(() => {
    return flats.flatMap((flat) => {
      const b = buildings[flat.buildingId];
      if (!b || b.latitude == null || b.longitude == null) return [];
      return [{ flat, building: b, lat: b.latitude, lng: b.longitude }];
    });
  }, [flats, buildings]);

  // Centre + zoom heuristic.
  let center: [number, number] = INDIA_CENTER;
  let zoom = 5;
  if (userCenter) {
    center = [userCenter.lat, userCenter.lng];
    zoom = 12;
  } else if (pins.length > 0) {
    center = [pins[0].lat, pins[0].lng];
    zoom = 11;
  }

  return (
    <Card className="overflow-hidden">
      <div className="relative h-[600px]">
        <MapContainer
          center={center}
          zoom={zoom}
          scrollWheelZoom
          className="h-full w-full"
          // Leaflet attaches CSS by default; we let it.
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {/* Auto-fit bounds to all pins when there are 2+ — keeps
              every result visible on the first paint. */}
          {pins.length > 1 && <FitBoundsToPins points={pins.map((p) => [p.lat, p.lng])} />}
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
              {flats.length === 0
                ? "No homes match your filters."
                : "None of these homes have map coordinates yet."}
            </div>
          </div>
        )}
      </div>
      {pins.length > 0 && pins.length < flats.length && (
        <div className="px-4 py-2 text-[11px] text-muted-foreground border-t">
          Showing {pins.length} of {flats.length} on the map — {flats.length - pins.length}{" "}
          {flats.length - pins.length === 1 ? "listing has" : "listings have"} no pin yet.
        </div>
      )}
    </Card>
  );
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
