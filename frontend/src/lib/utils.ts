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
 * <p>Backend now mints RELATIVE URLs (e.g.
 * {@code /rentals/v1/documents/{id}/blob?…}) so the browser resolves
 * against whatever origin is currently serving the SPA — works on
 * localhost, ngrok-tunneled dev sessions, and prod without any host
 * config.
 *
 * <p>This helper strips legacy {@code http://localhost:8080} (or any
 * host:port + scheme) prefix from URLs minted BEFORE the fix landed,
 * so users whose {@code User.profilePictureUrl} was persisted with an
 * absolute localhost URL don't see a permanently-broken avatar after
 * the deploy. Returns null when the input is null / empty so callers
 * can render the initials fallback unchanged.
 */
export function normalizeDocUrl(url?: string | null): string | undefined {
  if (!url) return undefined;
  // Already relative — pass through unchanged.
  if (url.startsWith("/")) return url;
  // Strip absolute scheme://host[:port] prefix; keep the path + query.
  const m = url.match(/^https?:\/\/[^/]+(\/.*)$/);
  return m ? m[1] : url;
}
