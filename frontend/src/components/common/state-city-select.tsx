import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { AlertCircle, RefreshCw } from "lucide-react";
import { referenceApi } from "@/lib/api/reference";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import type { RefCityDto, RefStateDto } from "@/types/api";

/**
 * Cascading state → city dropdown. Backed by the reference data seeded into
 * the property-service DB. Used on Add Building (and reusable on any other
 * place we need an India geo selector).
 *
 * <p>Resilience layers (added after the reference-data endpoint was
 * silently disabling the dropdown every time property-service restarted):
 *
 * <ol>
 *   <li><b>localStorage cache</b> survives backend downtime + hard
 *       reload. State + city lists are seeded from a static CSV and
 *       never change, so a stale cached copy is always safe to render.
 *       Background refresh updates the cache when the server comes back.</li>
 *   <li><b>React Query retries</b> (5 attempts, 1s → 16s backoff) cover
 *       short windows where the gateway is re-registering after a
 *       service restart.</li>
 *   <li><b>Inline retry button</b> on hard failure — operator can
 *       force a re-fetch without page reload, so a transient blip
 *       doesn't leave the form unusable.</li>
 * </ol>
 *
 * <p>The previous version showed {@code disabled={statesQ.isError}}
 * which left the dropdown stuck disabled with no recovery path —
 * users saw a non-clickable "Select state" placeholder and had to
 * F5 the whole page.
 */

const STATES_CACHE_KEY = "anirudhhomes.ref.states.v1";
const CITIES_CACHE_PREFIX = "anirudhhomes.ref.cities.v1.";

function readCache<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function writeCache(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Quota / private-mode — silent, the in-memory cache still works
    // for the rest of the session.
  }
}

interface Props {
  state?: RefStateDto | null;
  city?: RefCityDto | null;
  onChange: (next: { state: RefStateDto | null; city: RefCityDto | null }) => void;
  disabled?: boolean;
  required?: boolean;
}

export function StateCitySelect({
  state,
  city,
  onChange,
  disabled,
  required,
}: Props) {
  const statesQ = useQuery({
    queryKey: ["ref-states"],
    queryFn: async () => {
      const fresh = await referenceApi.states();
      writeCache(STATES_CACHE_KEY, fresh);
      return fresh;
    },
    // Reference data never changes within a single session. Setting
    // staleTime to Infinity prevents pointless background refetches
    // and lets the cached value (if any) carry the UI even when the
    // backend is down.
    staleTime: Infinity,
    // Initial data from localStorage — instant render on hard reload
    // even when property-service hasn't booted yet.
    initialData: () => readCache<RefStateDto[]>(STATES_CACHE_KEY) ?? undefined,
    // Aggressive retries — covers short backend-restart windows
    // (society MVP redeploy, V3 migration, etc.) without leaving
    // the user staring at a disabled dropdown.
    retry: 5,
    retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 16_000),
  });

  const citiesQ = useQuery({
    queryKey: ["ref-cities", state?.id],
    queryFn: async () => {
      const fresh = await referenceApi.cities(state!.id);
      writeCache(CITIES_CACHE_PREFIX + state!.id, fresh);
      return fresh;
    },
    enabled: !!state?.id,
    staleTime: Infinity,
    initialData: () =>
      state?.id
        ? readCache<RefCityDto[]>(CITIES_CACHE_PREFIX + state.id) ?? undefined
        : undefined,
    retry: 5,
    retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 16_000),
  });

  // If the parent's selected city is for a different state, clear it.
  useEffect(() => {
    if (city && state && city.stateId !== state.id) {
      onChange({ state, city: null });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state?.id]);

  // Treat "loading with no cached fallback" as a real loading state;
  // once we have data (from server OR cache), render the dropdown.
  const statesData = statesQ.data;
  const statesHasData = !!statesData && statesData.length > 0;
  const statesShowError =
    statesQ.isError && !statesHasData && !statesQ.isFetching;

  return (
    <>
      <div>
        <Label htmlFor="state">
          State{required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        {statesQ.isLoading && !statesHasData ? (
          <Skeleton className="h-10 mt-1.5" />
        ) : statesShowError ? (
          <div className="mt-1.5 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm">
            <AlertCircle className="size-4 text-destructive shrink-0" />
            <span className="flex-1 text-destructive">
              Couldn't load states. Backend may be restarting.
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => statesQ.refetch()}
              disabled={statesQ.isFetching}
            >
              <RefreshCw
                className={`size-3.5 ${statesQ.isFetching ? "animate-spin" : ""}`}
              />
              Retry
            </Button>
          </div>
        ) : (
          <Select
            value={state?.id ? String(state.id) : ""}
            onValueChange={(v) => {
              const picked = statesData?.find((s) => String(s.id) === v) ?? null;
              onChange({ state: picked, city: null });
            }}
            disabled={disabled}
          >
            <SelectTrigger id="state" className="mt-1.5">
              <SelectValue placeholder="Select state" />
            </SelectTrigger>
            <SelectContent>
              {(statesData ?? []).map((s) => (
                <SelectItem key={s.id} value={String(s.id)}>
                  {s.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      <div>
        <Label htmlFor="city">
          City{required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        {state ? (
          citiesQ.isLoading && !citiesQ.data ? (
            <Skeleton className="h-10 mt-1.5" />
          ) : citiesQ.isError && !citiesQ.data ? (
            <div className="mt-1.5 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm">
              <AlertCircle className="size-4 text-destructive shrink-0" />
              <span className="flex-1 text-destructive">
                Couldn't load cities.
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => citiesQ.refetch()}
                disabled={citiesQ.isFetching}
              >
                <RefreshCw
                  className={`size-3.5 ${citiesQ.isFetching ? "animate-spin" : ""}`}
                />
                Retry
              </Button>
            </div>
          ) : (
            <Select
              value={city?.id ? String(city.id) : ""}
              onValueChange={(v) => {
                const picked = citiesQ.data?.find((c) => String(c.id) === v) ?? null;
                onChange({ state, city: picked });
              }}
              disabled={disabled}
            >
              <SelectTrigger id="city" className="mt-1.5">
                <SelectValue placeholder="Select city" />
              </SelectTrigger>
              <SelectContent>
                {(citiesQ.data ?? []).map((c) => (
                  <SelectItem key={c.id} value={String(c.id)}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )
        ) : (
          <Select disabled>
            <SelectTrigger id="city" className="mt-1.5">
              <SelectValue placeholder="Pick a state first" />
            </SelectTrigger>
            <SelectContent />
          </Select>
        )}
      </div>
    </>
  );
}
