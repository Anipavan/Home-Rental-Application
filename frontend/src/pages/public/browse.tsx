import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Search, SlidersHorizontal } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { PropertyCard } from "@/components/property/property-card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";

export function BrowsePage() {
  // ?q= is honoured so deep-links from the AppShell GlobalSearch
  // ("Press Enter to see all results for X →") and the tenant nav's
  // Browse Homes link land on the page with the filter pre-applied.
  const [searchParams] = useSearchParams();
  const [q, setQ] = useState(searchParams.get("q") ?? "");
  const [bhk, setBhk] = useState("any");
  const [budget, setBudget] = useState("any");
  const [sort, setSort] = useState("recent");

  // Sync from URL → state whenever the query param changes. This
  // catches the case where a user is already on /app/browse and
  // searches again from the GlobalSearch popover — without this the
  // URL updates but the input doesn't.
  useEffect(() => {
    const next = searchParams.get("q") ?? "";
    setQ(next);
  }, [searchParams]);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["flats", "all"],
    queryFn: () => propertiesApi.flats.list(0, 60),
  });

  const filtered = useMemo(() => {
    let list = data?.content ?? [];
    if (q) {
      const needle = q.toLowerCase();
      // Match against everything a renter would actually type: flat
      // number, building name, address, city. The old filter only
      // looked at flatNumber + buildingId which never matched any
      // real-world query (nobody searches by flat #103 or building
      // UUID). FlatResponseDTO carries these joined fields from the
      // backend already.
      list = list.filter((f) => {
        const hay = [
          f.flatNumber,
          f.buildingName,
          f.buildingAddress,
          f.buildingCity,
        ]
          .filter(Boolean)
          .join(" ")
          .toLowerCase();
        return hay.includes(needle);
      });
    }
    if (bhk !== "any") list = list.filter((f) => f.bedrooms === Number(bhk));
    if (budget !== "any") {
      const [lo, hi] = budget.split("-").map(Number);
      list = list.filter((f) => {
        const r = Number(f.rentAmount);
        if (Number.isNaN(r)) return false;
        if (hi) return r >= lo && r <= hi;
        return r >= lo;
      });
    }
    if (sort === "low") list = [...list].sort((a, b) => a.rentAmount - b.rentAmount);
    if (sort === "high") list = [...list].sort((a, b) => b.rentAmount - a.rentAmount);
    return list;
  }, [data, q, bhk, budget, sort]);

  return (
    <div className="container py-8 lg:py-10">
      <PageHeader
        title="Find your next home"
        description="Verified listings. Direct from owners. Zero brokerage."
      />

      <Card className="p-3 mb-6 shadow-soft">
        <div className="grid grid-cols-1 sm:grid-cols-[1.5fr_1fr_1fr_1fr_auto] gap-2">
          <div className="relative">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
            <Input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search by area, building or flat number"
              className="pl-10"
            />
          </div>
          <Select value={bhk} onValueChange={setBhk}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="any">Any BHK</SelectItem>
              <SelectItem value="1">1 BHK</SelectItem>
              <SelectItem value="2">2 BHK</SelectItem>
              <SelectItem value="3">3 BHK</SelectItem>
              <SelectItem value="4">4+ BHK</SelectItem>
            </SelectContent>
          </Select>
          <Select value={budget} onValueChange={setBudget}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="any">Any budget</SelectItem>
              <SelectItem value="0-15000">Under ₹15K</SelectItem>
              <SelectItem value="15000-30000">₹15K – 30K</SelectItem>
              <SelectItem value="30000-60000">₹30K – 60K</SelectItem>
              <SelectItem value="60000-">₹60K +</SelectItem>
            </SelectContent>
          </Select>
          <Select value={sort} onValueChange={setSort}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="recent">Most recent</SelectItem>
              <SelectItem value="low">Price: low to high</SelectItem>
              <SelectItem value="high">Price: high to low</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" className="gap-1.5">
            <SlidersHorizontal /> Filters
          </Button>
        </div>
      </Card>

      {isLoading && (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Card key={i} className="overflow-hidden">
              <Skeleton className="aspect-[4/3] w-full rounded-none" />
              <div className="p-4 space-y-3">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/2" />
                <Skeleton className="h-5 w-1/3" />
              </div>
            </Card>
          ))}
        </div>
      )}

      {isError && (
        <Card className="p-12 text-center text-muted-foreground">
          We couldn't load listings right now. Please try again.
        </Card>
      )}

      {!isLoading && !isError && (
        <>
          <p className="text-sm text-muted-foreground mb-4">
            Showing <span className="font-semibold text-foreground">{filtered.length}</span> homes
          </p>
          {filtered.length === 0 ? (
            <Card className="p-12 text-center">
              <p className="font-display font-semibold text-lg">
                No homes match those filters.
              </p>
              <p className="text-muted-foreground text-sm mt-1">
                Try widening your budget or removing a filter.
              </p>
            </Card>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {filtered.map((flat) => (
                <PropertyCard key={flat.id} flat={flat} />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
