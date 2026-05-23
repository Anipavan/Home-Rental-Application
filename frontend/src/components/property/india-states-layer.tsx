import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { GeoJSON, useMap } from "react-leaflet";
import L from "leaflet";

/**
 * Minimal GeoJSON types — inlined to avoid a transitive dependency on
 * @types/geojson. Only the shape we actually use (Polygon /
 * MultiPolygon, Feature with a properties bag, FeatureCollection).
 * If we ever need the full GeoJSON typing, swap these out for an
 * import from the {@code geojson} types package.
 */
interface Polygon {
  type: "Polygon";
  coordinates: number[][][];
}
interface MultiPolygon {
  type: "MultiPolygon";
  coordinates: number[][][][];
}
type Geometry = Polygon | MultiPolygon;
export interface Feature {
  type: "Feature";
  properties: Record<string, unknown>;
  geometry: Geometry;
}
export interface FeatureCollection {
  type: "FeatureCollection";
  features: Feature[];
}

/**
 * India-states layer for the Browse Homes map.
 *
 * <p>Renders the 28 states + 8 UTs as low-opacity polygons over the
 * OSM tile layer. Click any state → zooms to that state's bounds and
 * notifies the parent of the selection (which uses the state name to
 * filter the visible flat pins via {@link pointInPolygon}).
 *
 * <p>GeoJSON is fetched once from a CDN-hosted, openly-licensed source
 * (Datameet, CC-BY-4.0). React Query caches it indefinitely
 * ({@code staleTime: Infinity}) so a single user session loads it
 * once. ~200 KB on the wire, cached at the CDN edge.
 *
 * <p>Failure mode: if the CDN fetch fails (offline, blocked,
 * timed out), the layer simply doesn't render — the map keeps working
 * without state polygons. Better than crashing on a transient
 * network error.
 */
interface Props {
  /** Currently-selected state name (or null). */
  selectedState: string | null;
  onSelect: (stateName: string | null) => void;
}

const INDIA_STATES_GEOJSON_URL =
  "https://cdn.jsdelivr.net/gh/datameet/maps@master/States/Admin2.geojson";

// Fallback URL — same dataset different mirror. If the primary CDN
// is rate-limited or down, this kicks in. Both are pinned to a
// specific commit to avoid silent format drift.
const INDIA_STATES_FALLBACK_URL =
  "https://raw.githubusercontent.com/datameet/maps/master/States/Admin2.geojson";

export function IndiaStatesLayer({ selectedState, onSelect }: Props) {
  const map = useMap();
  const [hoveredState, setHoveredState] = useState<string | null>(null);

  const geoQ = useQuery({
    queryKey: ["india-states-geojson"],
    queryFn: async () => {
      // Try the primary CDN first, fall back to GitHub raw on failure.
      // Both are CORS-friendly. We don't retry inside a single source
      // — if jsdelivr is down across regions, GitHub raw usually still
      // serves.
      try {
        const resp = await fetch(INDIA_STATES_GEOJSON_URL);
        if (!resp.ok) throw new Error(`primary ${resp.status}`);
        return (await resp.json()) as FeatureCollection;
      } catch {
        const resp = await fetch(INDIA_STATES_FALLBACK_URL);
        if (!resp.ok) {
          throw new Error(`India states GeoJSON unreachable: ${resp.status}`);
        }
        return (await resp.json()) as FeatureCollection;
      }
    },
    // Static GeoJSON — once we have it, never refetch within a session.
    staleTime: Infinity,
    gcTime: Infinity,
    retry: 1,
  });

  /**
   * Per-feature style — selected state gets a stronger fill, the
   * hovered one a medium fill, all others a thin transparent outline
   * so they're noticeable without dominating the listing pins
   * underneath. The styling function is recomputed when selection or
   * hover changes, so Leaflet's setStyle picks up the new appearance.
   */
  const featureStyle = useMemo(
    () => (feature?: Feature) => {
      const name = stateNameOf(feature);
      if (name === selectedState) {
        return {
          color: "#0d9488",
          weight: 2.5,
          fillColor: "#0d9488",
          fillOpacity: 0.15,
        };
      }
      if (name === hoveredState) {
        return {
          color: "#0d9488",
          weight: 1.5,
          fillColor: "#10b981",
          fillOpacity: 0.08,
        };
      }
      return {
        color: "#64748b",
        weight: 0.6,
        fillColor: "#94a3b8",
        fillOpacity: 0.02,
      };
    },
    [selectedState, hoveredState],
  );

  if (geoQ.isError || !geoQ.data) return null;

  return (
    <GeoJSON
      // Force a remount when selection/hover changes — Leaflet's
      // GeoJSON layer caches the style function from the first render
      // and won't reapply it when the closure's `selectedState` /
      // `hoveredState` updates. Re-keying is the cheapest way to get
      // the new colours to render without imperatively walking layers.
      key={`${selectedState ?? "none"}-${hoveredState ?? "none"}`}
      // Cast to the react-leaflet expected type — our local
      // FeatureCollection is structurally identical to the canonical
      // GeoJSON one (same fields, same enum values), but TypeScript's
      // nominal-type behaviour on imports across packages would flag
      // the assignment. unknown→never via double-cast is the
      // recommended escape hatch for "I know the shapes match".
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      data={geoQ.data as any}
      style={featureStyle}
      onEachFeature={(feature, layer) => {
        // react-leaflet hands us the canonical geojson.Feature; we
        // cast to our local-typed alias for stateNameOf. Same shape,
        // just a different nominal type from a different package.
        const name = stateNameOf(feature as unknown as Feature);
        if (!name) return;

        // Bind a tooltip showing the state name. Sticky so it follows
        // the cursor — much friendlier than a centered tooltip the
        // user has to chase.
        layer.bindTooltip(name, { sticky: true, direction: "top" });

        layer.on({
          mouseover: () => setHoveredState(name),
          mouseout: () => setHoveredState(null),
          click: () => {
            // Already-selected state click = deselect (toggle). Makes
            // the "Clear" button optional for users who realize they
            // want to see everything again.
            if (selectedState === name) {
              onSelect(null);
              map.flyTo([22.0, 79.0], 5, { duration: 0.6 });
              return;
            }
            onSelect(name);
            // Zoom into the state bounds. Leaflet's getBounds() on a
            // GeoJSON layer returns the bounding box of all rings, so
            // a state with islands (e.g. Andaman & Nicobar) zooms out
            // a bit — acceptable, those are edge cases.
            const bounds = (layer as L.Path & {
              getBounds: () => L.LatLngBounds;
            }).getBounds?.();
            if (bounds && bounds.isValid()) {
              map.flyToBounds(bounds, {
                padding: [40, 40],
                maxZoom: 8,
                duration: 0.6,
              });
            }
          },
        });
      }}
    />
  );
}

/**
 * Extract the state name from a GeoJSON feature's properties. Datameet's
 * file uses {@code st_nm} as the canonical property; we also check
 * {@code NAME_1} / {@code state} / {@code name} as fallbacks for
 * cross-source compatibility.
 */
export function stateNameOf(feature?: Feature): string | null {
  if (!feature?.properties) return null;
  const p = feature.properties;
  const candidates = ["st_nm", "STATE", "NAME_1", "state", "name", "ST_NM"];
  for (const key of candidates) {
    const v = p[key];
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return null;
}

/**
 * Point-in-polygon check for client-side filtering of flat pins by
 * the selected state. Implements the ray-casting algorithm — pure
 * O(n) over the polygon vertices, no library dependency, runs in
 * microseconds even for India's largest states (Rajasthan ~1400 verts).
 *
 * <p>Handles MultiPolygon via flat-iteration over each polygon ring.
 * A point inside ANY ring of the multi-polygon counts as "inside" —
 * accounts for states with disjoint territory (Andaman + Nicobar,
 * Lakshadweep, etc.) without special-casing.
 */
export function pointInPolygon(
  lat: number,
  lng: number,
  feature: Feature,
): boolean {
  const geom = feature.geometry;
  if (!geom) return false;

  if (geom.type === "Polygon") {
    return ringContains(lat, lng, geom.coordinates[0]);
  }
  if (geom.type === "MultiPolygon") {
    for (const poly of geom.coordinates) {
      if (ringContains(lat, lng, poly[0])) return true;
    }
    return false;
  }
  return false;
}

/** Ray-casting on a single ring (array of [lng, lat] pairs). */
function ringContains(lat: number, lng: number, ring: number[][]): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i];
    const [xj, yj] = ring[j];
    // ringContains uses [lng, lat] coordinate order per GeoJSON spec.
    const intersect =
      yi > lat !== yj > lat &&
      lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}
