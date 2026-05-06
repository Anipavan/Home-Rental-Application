import { Link } from "react-router-dom";
import { cn } from "@/lib/utils";

export function Logo({
  className,
  size = "md",
}: {
  className?: string;
  size?: "sm" | "md" | "lg";
}) {
  const dim = size === "sm" ? 28 : size === "lg" ? 40 : 32;
  const text =
    size === "sm" ? "text-base" : size === "lg" ? "text-2xl" : "text-lg";
  return (
    <Link to="/" className={cn("flex items-center gap-2 group", className)}>
      <span
        className="grid place-items-center rounded-xl bg-gradient-to-br from-indigo-600 via-violet-600 to-fuchsia-600 text-white shadow-soft transition-transform group-hover:rotate-3"
        style={{ width: dim, height: dim }}
      >
        <svg
          viewBox="0 0 32 32"
          width={dim * 0.65}
          height={dim * 0.65}
          fill="none"
        >
          <path
            d="M6 16 L16 6 L26 16 V25 a2 2 0 0 1-2 2 H19 V19 H13 V27 H8 a2 2 0 0 1-2-2 Z"
            fill="white"
          />
        </svg>
      </span>
      <span
        className={cn(
          "font-display font-bold tracking-tight text-foreground",
          text,
        )}
      >
        Hearth
      </span>
    </Link>
  );
}
