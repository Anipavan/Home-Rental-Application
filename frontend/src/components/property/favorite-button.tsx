import { useNavigate } from "react-router-dom";
import { Heart } from "lucide-react";
import { useFavorites } from "@/hooks/use-favorites";
import { toast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";

/**
 * Heart-icon toggle for the wishlist feature. Drop on any flat card
 * or detail page — handles auth state, optimistic toggling, and the
 * accessibility bits (aria-pressed, aria-label).
 *
 * <p>Variants:
 *   - {@code "card"} — small 32px button with a translucent backdrop,
 *     designed to sit on top of a card's cover image.
 *   - {@code "detail"} — full-button rendering with a label, for use
 *     in the property-detail page action row beside Contact / Visit.
 *
 * <p>Click behaviour:
 *   - Signed-in tenant → optimistic toggle via {@link useFavorites}.
 *   - Anyone else → routes to {@code /login?next=<current-url>}. We
 *     deliberately don't shadow-save to localStorage; the comparison
 *     and saved-search features that build on this need server
 *     persistence and merging anonymous + signed-in lists later is
 *     messy.
 */
export function FavoriteButton({
  flatId,
  variant = "card",
  className,
}: {
  flatId: string;
  variant?: "card" | "detail";
  className?: string;
}) {
  const { authenticated, isFavorite, toggle, isMutating } = useFavorites();
  const navigate = useNavigate();
  const saved = isFavorite(flatId);

  function onClick(e: React.MouseEvent<HTMLButtonElement>) {
    e.preventDefault();
    e.stopPropagation();
    if (!authenticated) {
      // Stash the path the user was on so the post-login flow lands
      // them right back here. /login already supports ?next=.
      const next = encodeURIComponent(
        window.location.pathname + window.location.search,
      );
      toast({
        title: "Sign in to save listings",
        description: "Your wishlist syncs across devices once you're signed in.",
      });
      navigate(`/login?next=${next}`);
      return;
    }
    toggle(flatId);
  }

  const label = saved ? "Remove from wishlist" : "Save to wishlist";

  if (variant === "detail") {
    return (
      <button
        type="button"
        onClick={onClick}
        disabled={isMutating}
        aria-pressed={saved}
        aria-label={label}
        className={cn(
          "inline-flex items-center gap-2 rounded-lg border px-4 py-2 text-sm font-medium transition-colors",
          saved
            ? "border-rose-200 bg-rose-50 text-rose-600 hover:bg-rose-100 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
            : "border-border bg-background hover:bg-secondary/60",
          className,
        )}
      >
        <Heart
          className={cn(
            "size-4 transition-transform",
            saved && "fill-current scale-110",
          )}
        />
        {saved ? "Saved" : "Save"}
      </button>
    );
  }

  // "card" variant — small floating button on top of a card image.
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isMutating}
      aria-pressed={saved}
      aria-label={label}
      className={cn(
        "grid place-items-center size-8 rounded-full backdrop-blur-md transition-all shadow-soft",
        saved
          ? "bg-rose-500/95 text-white hover:bg-rose-600"
          : "bg-white/85 text-foreground hover:bg-white",
        className,
      )}
    >
      <Heart
        className={cn(
          "size-4 transition-transform",
          saved ? "fill-current scale-110" : "scale-100",
        )}
      />
    </button>
  );
}
