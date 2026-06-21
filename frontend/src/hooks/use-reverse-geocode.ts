import { useState } from "react";

/**
 * One-shot browser-geolocation → reverse-geocode hook.
 *
 *  flow:
 *   1. Ask the browser for the current lat/lng via
 *      navigator.geolocation.getCurrentPosition (1 prompt per origin
 *      per session, the browser remembers the answer).
 *   2. Hit Nominatim (OpenStreetMap's free reverse-geocoder) with
 *      those coords. No API key, no signup; Nominatim's policy is
 *      attribution + reasonable rate-limit, which a registration
 *      form trivially fits inside.
 *   3. Return a single comma-separated address string that the
 *      caller drops into the form. The user can edit it before
 *      submitting — we're not pretending to be authoritative.
 *
 *  caveats:
 *   - Geolocation needs HTTPS (browser refuses on plain http).
 *     anirudhhomes.in is TLS, so prod is fine; localhost is also
 *     allowed by browsers as a special case.
 *   - Nominatim attribution requirement is satisfied by linking to
 *     openstreetmap.org somewhere in the app footer; for now we
 *     accept the policy via terse usage. If we cross 1 req/sec at
 *     peak, switch to Mapbox Geocoding (free up to 100k/month).
 *
 *  not handled (intentionally):
 *   - permission denied. Caller renders the hook's `error` field;
 *     no auto-retry.
 *   - very low-accuracy fixes (e.g. desktop IP geo). The user
 *     edits the field. Showing a map for pin-refinement is the
 *     follow-up "Level 2" upgrade if conversion data justifies it.
 */
export function useReverseGeocode() {
  const [isDetecting, setIsDetecting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function detect(): Promise<string | null> {
    setError(null);

    if (!("geolocation" in navigator)) {
      setError("Your browser doesn't support location detection.");
      return null;
    }

    setIsDetecting(true);
    try {
      const coords = await getCurrentPosition();
      const address = await reverseGeocode(coords.latitude, coords.longitude);
      return address;
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Couldn't get your location.";
      setError(msg);
      return null;
    } finally {
      setIsDetecting(false);
    }
  }

  return { detect, isDetecting, error };
}

/** Promise wrapper around the callback-based geolocation API. */
function getCurrentPosition(): Promise<GeolocationCoordinates> {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve(pos.coords),
      (err) => {
        // Map the browser's enum to something human.
        switch (err.code) {
          case err.PERMISSION_DENIED:
            reject(
              new Error(
                "Location permission was blocked. You can enable it in your browser's site settings, or just type the address manually.",
              ),
            );
            break;
          case err.POSITION_UNAVAILABLE:
            reject(new Error("Couldn't get a location fix. Try again outdoors, or type the address."));
            break;
          case err.TIMEOUT:
            reject(new Error("Location detection timed out. Try again, or type the address."));
            break;
          default:
            reject(new Error("Couldn't get your location."));
        }
      },
      {
        enableHighAccuracy: true,
        timeout: 10_000, // 10s — generous enough for a cold GPS fix on mobile
        maximumAge: 60_000, // accept a 1-minute-old cached fix
      },
    );
  });
}

/**
 * Reverse-geocode via Nominatim. Returns the {@code display_name}
 * field which is a comma-joined address string ready to drop into
 * a textarea. Locale=en so the response is consistently English
 * regardless of the user's browser accept-language.
 */
async function reverseGeocode(lat: number, lng: number): Promise<string> {
  const url = new URL("https://nominatim.openstreetmap.org/reverse");
  url.searchParams.set("format", "jsonv2");
  url.searchParams.set("lat", String(lat));
  url.searchParams.set("lon", String(lng));
  url.searchParams.set("addressdetails", "1");
  url.searchParams.set("accept-language", "en");

  const resp = await fetch(url.toString(), {
    headers: {
      // Nominatim's policy asks for a real User-Agent. Browsers
      // strip custom UA headers from fetch so we send Referer
      // implicitly via the request origin — which Nominatim also
      // accepts under its policy.
      Accept: "application/json",
    },
  });

  if (!resp.ok) {
    throw new Error("Address lookup failed. Type the address manually.");
  }

  const json = (await resp.json()) as {
    display_name?: string;
    address?: Record<string, string>;
  };

  if (!json.display_name) {
    throw new Error("Couldn't resolve an address from your location.");
  }

  return json.display_name;
}
