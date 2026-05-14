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
