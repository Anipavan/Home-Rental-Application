import { Link } from "react-router-dom";
import { cn } from "@/lib/utils";

/**
 * Anirudh Homes brand mark.
 *
 * <p>Composition:
 *   - Rounded-square plate filled with the emerald → teal → sky brand
 *     gradient (matches {@code .gradient-brand} + the favicon).
 *   - White stylised house silhouette with a softly-tinted door cut-out
 *     to add a hint of depth at small sizes.
 *   - Wordmark beside it in Plus Jakarta Sans display.
 *   - Tiny "tag" pill underneath at {@code lg} size only — sets the
 *     marketing tone on the landing/login pages without cluttering
 *     the in-app header.
 */
export function Logo({
  className,
  size = "md",
  showTagline = false,
}: {
  className?: string;
  size?: "sm" | "md" | "lg";
  /**
   * When true, renders a single-line tagline beneath the wordmark.
   * Designed for the landing page hero / login page — not the in-app
   * sidebar (turn off via default).
   */
  showTagline?: boolean;
}) {
  const dim = size === "sm" ? 28 : size === "lg" ? 44 : 32;
  const text =
    size === "sm" ? "text-base" : size === "lg" ? "text-2xl" : "text-lg";
  return (
    <Link to="/" className={cn("inline-flex items-center gap-2.5 group", className)}>
      <span
        className="grid place-items-center rounded-xl gradient-brand text-white shadow-soft transition-transform group-hover:rotate-3 group-hover:scale-105"
        style={{ width: dim, height: dim }}
      >
        <svg
          viewBox="0 0 32 32"
          width={dim * 0.62}
          height={dim * 0.62}
          fill="none"
          aria-hidden="true"
        >
          {/* House silhouette */}
          <path
            d="M6 16 L16 6 L26 16 V25 a2 2 0 0 1-2 2 H19 V19 H13 V27 H8 a2 2 0 0 1-2-2 Z"
            fill="white"
          />
          {/* Door tint for a hint of depth */}
          <rect
            x="14.5"
            y="20.5"
            width="3"
            height="5"
            rx="0.5"
            fill="rgba(16,185,129,0.22)"
          />
        </svg>
      </span>
      <span className="flex flex-col leading-none">
        <span
          className={cn(
            "font-display font-bold tracking-tight text-foreground",
            text,
          )}
        >
          Anirudh Homes
        </span>
        {showTagline && size === "lg" && (
          <span className="text-[10px] font-medium uppercase tracking-[0.16em] text-muted-foreground mt-1">
            Homes · Simplified
          </span>
        )}
      </span>
    </Link>
  );
}
