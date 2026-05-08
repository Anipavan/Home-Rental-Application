import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
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
import type { RefCityDto, RefStateDto } from "@/types/api";

/**
 * Cascading state → city dropdown. Backed by the reference data seeded into
 * the property-service DB. Used on Add Building (and reusable on any other
 * place we need an India geo selector).
 *
 * <p>State changes reset the city. Both selections are reported back to the
 * parent via {@code onChange} as the picked entities (so the parent can
 * persist either id or name as needed).
 */
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
    queryFn: referenceApi.states,
    staleTime: 24 * 60 * 60 * 1000, // states list never really changes
  });

  const citiesQ = useQuery({
    queryKey: ["ref-cities", state?.id],
    queryFn: () => referenceApi.cities(state!.id),
    enabled: !!state?.id,
    staleTime: 24 * 60 * 60 * 1000,
  });

  // If the parent's selected city is for a different state, clear it.
  useEffect(() => {
    if (city && state && city.stateId !== state.id) {
      onChange({ state, city: null });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state?.id]);

  return (
    <>
      <div>
        <Label htmlFor="state">
          State{required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        {statesQ.isLoading ? (
          <Skeleton className="h-10 mt-1.5" />
        ) : (
          <Select
            value={state?.id ? String(state.id) : ""}
            onValueChange={(v) => {
              const picked = statesQ.data?.find((s) => String(s.id) === v) ?? null;
              onChange({ state: picked, city: null });
            }}
            disabled={disabled || statesQ.isError}
          >
            <SelectTrigger id="state" className="mt-1.5">
              <SelectValue placeholder="Select state" />
            </SelectTrigger>
            <SelectContent>
              {(statesQ.data ?? []).map((s) => (
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
          citiesQ.isLoading ? (
            <Skeleton className="h-10 mt-1.5" />
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
