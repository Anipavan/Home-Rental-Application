import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Pause, Play, Search, Trash2 } from "lucide-react";
import { toast } from "@/hooks/use-toast";
import {
  savedSearchesApi,
  type SavedSearchResponse,
} from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";

/**
 * Tenant alerts management page. Lists every saved search the user
 * has created from /browse, lets them pause (toggle isActive) or
 * delete an alert, and explains how the matcher fires.
 *
 * <p>The list isn't paginated — saved-searches are a sparse,
 * user-curated set (typically &lt; 10 per renter). When that scales
 * we can swap in a Page&lt;T&gt; the same way the wishlist does.
 */
export function SavedSearchesPage() {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["saved-searches", "list"],
    queryFn: () => savedSearchesApi.list(),
    staleTime: 30_000,
  });

  const toggleMut = useMutation({
    mutationFn: (s: SavedSearchResponse) =>
      savedSearchesApi.update(s.id, { isActive: !s.isActive }),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: ["saved-searches"] });
      toast({
        title: vars.isActive ? "Alert paused" : "Alert resumed",
        description: vars.isActive
          ? "You won't get emails for this search until you resume it."
          : "We'll start emailing you again on new matches.",
      });
    },
    onError: () =>
      toast({
        variant: "destructive",
        title: "Couldn't update",
        description: "Please try again.",
      }),
  });

  const removeMut = useMutation({
    mutationFn: (id: string) => savedSearchesApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["saved-searches"] });
      toast({ title: "Alert deleted" });
    },
    onError: () =>
      toast({
        variant: "destructive",
        title: "Couldn't delete",
        description: "Please try again.",
      }),
  });

  const items = q.data ?? [];

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Saved searches"
        description="Get an email the moment a new home matches a filter you've saved."
      />

      {q.isLoading && (
        <div className="grid gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Card key={i} className="p-5">
              <Skeleton className="h-5 w-1/3 mb-2" />
              <Skeleton className="h-4 w-2/3" />
            </Card>
          ))}
        </div>
      )}

      {!q.isLoading && items.length === 0 && (
        <Card className="p-12 text-center">
          <div className="size-14 rounded-full bg-primary/10 grid place-items-center mx-auto">
            <BellRing className="size-6 text-primary" />
          </div>
          <p className="font-display font-semibold text-lg mt-4">
            No saved searches yet
          </p>
          <p className="text-muted-foreground text-sm mt-1 max-w-md mx-auto">
            Set a few filters on the browse page (city, BHK, budget…) and tap
            <span className="font-semibold text-foreground"> Save this search</span>.
            We'll email you the moment a matching home is listed.
          </p>
          <Button asChild variant="gradient" className="mt-5">
            <Link to="/browse">
              <Search /> Browse homes
            </Link>
          </Button>
        </Card>
      )}

      {!q.isLoading && items.length > 0 && (
        <div className="grid gap-4">
          {items.map((s) => (
            <SavedSearchRow
              key={s.id}
              s={s}
              onToggle={() => toggleMut.mutate(s)}
              onDelete={() => removeMut.mutate(s.id)}
              busy={toggleMut.isPending || removeMut.isPending}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SavedSearchRow({
  s,
  onToggle,
  onDelete,
  busy,
}: {
  s: SavedSearchResponse;
  onToggle: () => void;
  onDelete: () => void;
  busy: boolean;
}) {
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-display font-semibold text-base truncate">
              {s.name ?? "Saved search"}
            </p>
            {s.isActive ? (
              <Badge variant="secondary" className="bg-emerald-100 text-emerald-700">
                Active
              </Badge>
            ) : (
              <Badge variant="outline">Paused</Badge>
            )}
          </div>
          <PredicateChips s={s} />
          <p className="text-xs text-muted-foreground mt-2">
            Created {fmtDate(s.createdAt)}
            {s.lastMatchedAt && (
              <> · Last checked {fmtDate(s.lastMatchedAt)}</>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={onToggle}
            disabled={busy}
            className="gap-1.5"
          >
            {s.isActive ? <Pause className="size-3.5" /> : <Play className="size-3.5" />}
            {s.isActive ? "Pause" : "Resume"}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onDelete}
            disabled={busy}
            className="text-destructive hover:text-destructive gap-1.5"
          >
            <Trash2 className="size-3.5" /> Delete
          </Button>
        </div>
      </div>
    </Card>
  );
}

function PredicateChips({ s }: { s: SavedSearchResponse }) {
  const chips: string[] = [];
  if (s.city) chips.push(s.city);
  if (s.bedrooms != null) chips.push(`${s.bedrooms} BHK`);
  if (s.minRent != null && s.maxRent != null) {
    chips.push(`${formatINR(s.minRent)} – ${formatINR(s.maxRent)}`);
  } else if (s.maxRent != null) {
    chips.push(`Under ${formatINR(s.maxRent)}`);
  } else if (s.minRent != null) {
    chips.push(`${formatINR(s.minRent)}+`);
  }
  if (s.minAreaSqft != null) chips.push(`≥ ${s.minAreaSqft} sqft`);
  if (s.furnishingStatus) chips.push(prettyFurnishing(s.furnishingStatus));
  if (s.petFriendly) chips.push("Pet friendly");
  if (chips.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1.5 mt-2">
      {chips.map((c) => (
        <span
          key={c}
          className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground"
        >
          {c}
        </span>
      ))}
    </div>
  );
}

function prettyFurnishing(v: string) {
  switch (v) {
    case "FULLY_FURNISHED":
      return "Fully furnished";
    case "SEMI_FURNISHED":
      return "Semi furnished";
    case "UNFURNISHED":
      return "Unfurnished";
    default:
      return v;
  }
}

function fmtDate(iso: string) {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  } catch {
    return iso;
  }
}
