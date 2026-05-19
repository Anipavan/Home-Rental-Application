import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatINR(amount: number | string | undefined | null): string {
  const n = typeof amount === "string" ? Number(amount) : amount;
  if (n == null || Number.isNaN(n)) return "—";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(n);
}

export function formatDate(value: string | Date | undefined | null): string {
  if (!value) return "—";
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(d);
}

/**
 * Renders a floor number as its ordinal English word — the owner
 * enters a number (0, 1, 2, …) and the public detail page shows
 * "Ground", "First", "Second", …
 *
 * Beyond {@code FLOOR_WORDS.length - 1} we fall back to the
 * numeric ordinal ("21st", "22nd"). Negative values are treated as
 * basement floors ("Basement", "Basement 2", …) — uncommon but
 * supported for completeness so an owner who types {@code -1}
 * doesn't see a weird "negative first" string.
 *
 * Returns `"—"` for null/undefined/NaN so callers can drop it
 * straight into a label without first-checking.
 */
const FLOOR_WORDS = [
  "Ground",
  "First",
  "Second",
  "Third",
  "Fourth",
  "Fifth",
  "Sixth",
  "Seventh",
  "Eighth",
  "Ninth",
  "Tenth",
  "Eleventh",
  "Twelfth",
  "Thirteenth",
  "Fourteenth",
  "Fifteenth",
  "Sixteenth",
  "Seventeenth",
  "Eighteenth",
  "Nineteenth",
  "Twentieth",
];

export function floorLabel(floor: number | string | null | undefined): string {
  if (floor === null || floor === undefined || floor === "") return "—";
  const n = typeof floor === "string" ? Number(floor) : floor;
  if (Number.isNaN(n)) return "—";
  if (n < 0) {
    const abs = Math.abs(n);
    return abs === 1 ? "Basement" : `Basement ${abs}`;
  }
  if (n < FLOOR_WORDS.length) return FLOOR_WORDS[n];
  // 21st, 22nd, 23rd, 24th, … — English ordinal rules.
  const lastTwo = n % 100;
  const lastOne = n % 10;
  const suffix =
    lastTwo >= 11 && lastTwo <= 13
      ? "th"
      : lastOne === 1
        ? "st"
        : lastOne === 2
          ? "nd"
          : lastOne === 3
            ? "rd"
            : "th";
  return `${n}${suffix}`;
}

export function relativeFromNow(value: string | Date | undefined | null): string {
  if (!value) return "—";
  const d = typeof value === "string" ? new Date(value) : value;
  const diff = Date.now() - d.getTime();
  const sec = Math.round(diff / 1000);
  const min = Math.round(sec / 60);
  const hr = Math.round(min / 60);
  const day = Math.round(hr / 24);
  if (sec < 60) return "just now";
  if (min < 60) return `${min}m ago`;
  if (hr < 24) return `${hr}h ago`;
  if (day < 30) return `${day}d ago`;
  return formatDate(d);
}

export function initials(name?: string): string {
  if (!name) return "·";
  const parts = name.trim().split(/\s+/);
  return (parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "");
}

/**
 * Normalise a presigned document URL for use in an {@code <img src>}
 * attribute (Issue #1).
 *
 * <p>The bug this solves: document-service mints presigned URLs as
 * {@code /rentals/v1/documents/{id}/blob?…} — the canonical gateway
 * path. But the FE talks to the gateway through different prefixes
 * depending on the deployment:
 *
 * <ul>
 *   <li>Direct mode: axios baseURL = {@code http://localhost:8080/rentals/v1}.
 *       An {@code <img src="/rentals/v1/…">} resolves against the
 *       SPA origin ({@code :4200} in dev), which has no such path
 *       and returns the SPA fallback HTML — image silently fails.</li>
 *   <li>Proxy/tunnel mode: axios baseURL = {@code /api/rentals/v1}.
 *       Vite proxies {@code /api/**} to the gateway. A bare
 *       {@code /rentals/v1/…} src bypasses the proxy entirely.</li>
 * </ul>
 *
 * <p>The fix: derive the FE's "everything before /rentals/v1" prefix
 * from {@code VITE_API_BASE_URL} and prepend it to any signed URL
 * whose path starts with {@code /rentals/v1/}. The result is a URL
 * that hits the same code path as every other axios call — so dev
 * (proxy), tunnels (ngrok + proxy), and prod (absolute host) all
 * work without per-environment FE branching.
 *
 * <p>Returns undefined when input is null / empty so callers can
 * render the initials fallback unchanged.
 */
export function normalizeDocUrl(url?: string | null): string | undefined {
  if (!url) return undefined;
  // Pass through unchanged for URLs that aren't document-service
  // signed paths (e.g. an external CDN URL or some legacy data
  // shape we shouldn't touch).
  if (!url.includes("/rentals/v1/")) return url;

  // Step 1: figure out the FE's prefix-before-/rentals/v1 from its
  // own base URL. Works for both absolute bases ({@code http://host/rentals/v1})
  // and relative-proxy bases ({@code /api/rentals/v1}).
  const BASE =
    (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
    "http://localhost:8080/rentals/v1";
  const sliceAt = BASE.indexOf("/rentals/v1");
  // Unknown base URL shape — bail.
  if (sliceAt < 0) return url;
  const prefix = BASE.slice(0, sliceAt);

  // Step 2: canonicalize to a leading-slash path (strip any
  // scheme://host:port prefix from legacy absolute URLs).
  let path = url;
  const absMatch = url.match(/^https?:\/\/[^/]+(\/.*)$/);
  if (absMatch) path = absMatch[1];

  // Step 3: if the path is already correctly prefixed, leave it.
  if (prefix && path.startsWith(prefix + "/rentals/v1/")) return path;

  // Step 4: slice from the canonical "/rentals/v1/" segment and
  // rebuild with the FE's prefix. Handles bare paths
  // ({@code /rentals/v1/...}) and any wrong-prefix path
  // ({@code /other/rentals/v1/...}) alike.
  const canonIdx = path.indexOf("/rentals/v1/");
  if (canonIdx < 0) return path;
  return prefix + path.slice(canonIdx);
}
