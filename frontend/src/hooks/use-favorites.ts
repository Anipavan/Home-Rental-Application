import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/stores/auth-store";
import { favoritesApi } from "@/lib/api/properties";

/**
 * Single-source-of-truth hook for the wishlist heart-icon state.
 *
 * <p>Used everywhere there's a flat card (browse grid, property
 * detail, saved-page, future "homes you might like"). Holds a single
 * Set<string> in React Query, optimistic-updates it on toggle, and
 * surfaces:
 *
 *  - {@code isFavorite(flatId)}     — for the heart-icon fill state
 *  - {@code toggle(flatId)}         — call from the button onClick
 *  - {@code isLoading}              — initial fetch in flight
 *  - {@code isMutating}             — a toggle is round-tripping
 *  - {@code authenticated}          — false → caller should redirect
 *                                     the user to /login instead of
 *                                     trying to save
 *
 * <p>Disabled when there's no signed-in user. We deliberately don't
 * fall back to localStorage for guests — the comparison/saved-
 * search features that build on this need server persistence anyway,
 * and merging anonymous + signed-in wishlists later is messy.
 */
const FAVORITES_KEY = ["favorites", "ids"] as const;

export function useFavorites() {
  const { authUserId, isAuthenticated } = useAuthStore();
  const qc = useQueryClient();
  const enabled = !!authUserId && isAuthenticated;

  const idsQ = useQuery({
    queryKey: FAVORITES_KEY,
    queryFn: () => favoritesApi.ids(),
    enabled,
    staleTime: 60_000,
  });

  // The query returns string[]; wrap in a Set so callers do O(1) lookups.
  const idSet = new Set<string>(idsQ.data ?? []);

  const toggleM = useMutation({
    mutationFn: async (flatId: string) => {
      const currentlySaved = idSet.has(flatId);
      if (currentlySaved) {
        await favoritesApi.remove(flatId);
      } else {
        await favoritesApi.add(flatId);
      }
      return { flatId, savedAfter: !currentlySaved };
    },
    onMutate: async (flatId) => {
      // Optimistic: flip the cache before the round-trip so the heart
      // animates instantly. Rollback on error.
      await qc.cancelQueries({ queryKey: FAVORITES_KEY });
      const previous = qc.getQueryData<string[]>(FAVORITES_KEY) ?? [];
      const next = previous.includes(flatId)
        ? previous.filter((id) => id !== flatId)
        : [...previous, flatId];
      qc.setQueryData<string[]>(FAVORITES_KEY, next);
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.previous) {
        qc.setQueryData(FAVORITES_KEY, ctx.previous);
      }
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: FAVORITES_KEY });
      // The "Saved" page lists hydrated flats — invalidate that too
      // so the page reflects the toggle without a manual refetch.
      qc.invalidateQueries({ queryKey: ["favorites", "list"] });
    },
  });

  return {
    authenticated: enabled,
    isLoading: idsQ.isLoading,
    isMutating: toggleM.isPending,
    isFavorite: (flatId: string) => idSet.has(flatId),
    toggle: (flatId: string) => toggleM.mutate(flatId),
  };
}
