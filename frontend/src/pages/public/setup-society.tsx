import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  AlertCircle,
  Building2,
  CheckCircle2,
  Loader2,
} from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { claimsApi } from "@/lib/api/claims";
import { propertiesApi } from "@/lib/api/properties";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { BuildingResponseDTO } from "@/types/api";

/**
 * Phase 5 — Society setup for a fresh MAINTAINER signup.
 *
 * <p>Maintainers can only pick from an already-registered building.
 * The "Register new building" path was retired — building
 * registration belongs to the owner flow (/owner/buildings/new), and
 * having a duplicate entry point on the maintainer signup was
 * producing orphan buildings owned by nobody plausible. Maintainers
 * search for their society, pick it, and submit a MAINTAINER
 * membership claim; the building owner (plus any existing
 * maintainer) approves.
 */
export function SetupSocietyPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-muted/30 p-4 py-10">
      <div className="w-full max-w-lg">
        <div className="flex justify-center mb-6">
          <Logo />
        </div>
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-2xl font-bold tracking-tight">
            Set up your society
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Find your building below and we'll send a maintainer-access
            request to the owner.
          </p>

          <div className="mt-6">
            <PickExistingForm />
          </div>
        </Card>
      </div>
    </div>
  );
}

function PickExistingForm() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState<BuildingResponseDTO[]>([]);
  const [picked, setPicked] = useState<BuildingResponseDTO | null>(null);
  const [flatNumber, setFlatNumber] = useState("");
  const [note, setNote] = useState("");

  // Debounced inline preview — mirrors the createClaim server-side
  // vacancy guard so the maintainer applicant knows upfront if the
  // flat they're entering is vacant / doesn't exist. Prevents a
  // "submit → server rejects" round-trip.
  const [debouncedFlat, setDebouncedFlat] = useState("");
  useEffect(() => {
    const t = setTimeout(() => setDebouncedFlat(flatNumber.trim()), 350);
    return () => clearTimeout(t);
  }, [flatNumber]);
  const previewQ = useQuery({
    queryKey: ["flat-preview", picked?.buildingId, debouncedFlat],
    queryFn: () =>
      propertiesApi.flats.preview(picked!.buildingId, debouncedFlat),
    enabled: !!picked && debouncedFlat.length >= 1,
    staleTime: 30_000,
    retry: false,
  });

  const claimM = useMutation({
    mutationFn: async () => {
      if (!picked) throw new Error("Search and pick your building first.");
      if (!flatNumber.trim()) {
        throw new Error(
          "Enter the flat you live in — the building owner uses it to verify you.",
        );
      }
      return claimsApi.create({
        buildingId: picked.buildingId,
        requestedRole: "MAINTAINER",
        claimedFlatNumber: flatNumber.trim(),
        applicantNote: note.trim() || undefined,
      });
    },
    onSuccess: () => {
      toast({
        title: "Request submitted",
        description:
          "The building owner (and current maintainer, if any) will review your request. You'll get access once approved.",
      });
      navigate("/pending-claim");
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't submit your request",
        description: extractErrorMessage(err),
      });
    },
  });

  async function handleSearch() {
    if (!query.trim() || query.trim().length < 2) return;
    setSearching(true);
    try {
      const found = await propertiesApi.buildings.search(query.trim());
      setResults(found.slice(0, 8));
    } catch (err) {
      toast({
        variant: "destructive",
        title: "Search failed",
        description: extractErrorMessage(err),
      });
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="building-search">Search for your building</Label>
        <div className="flex gap-2 mt-1.5">
          <Input
            id="building-search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Building name, address, or city"
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleSearch();
              }
            }}
          />
          <Button
            type="button"
            variant="outline"
            onClick={handleSearch}
            disabled={searching}
          >
            {searching ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              "Search"
            )}
          </Button>
        </div>

        {results.length > 0 && !picked && (
          <ul className="mt-2 space-y-1 border rounded-md p-1 max-h-56 overflow-auto">
            {results.map((b) => (
              <li key={b.buildingId}>
                <button
                  type="button"
                  className="w-full text-left px-2 py-1.5 rounded hover:bg-secondary text-sm"
                  onClick={() => {
                    setPicked(b);
                    setResults([]);
                  }}
                >
                  <div className="font-medium">{b.buildingName}</div>
                  <div className="text-xs text-muted-foreground">
                    {b.buildingCity ?? ""} {b.buildingState ?? ""}
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}

        {results.length === 0 &&
          query.trim().length >= 2 &&
          !picked &&
          !searching && (
            <p className="text-xs text-muted-foreground mt-2">
              Nothing matches. Ask the building's owner to register it
              from their own account first, then come back to this
              page.
            </p>
          )}

        {picked && (
          <div className="mt-2 flex items-start justify-between rounded-md border border-primary/40 bg-primary/10 px-3 py-2 text-sm">
            <div>
              <div className="font-medium">{picked.buildingName}</div>
              <div className="text-xs text-muted-foreground">
                {picked.buildingCity ?? ""} {picked.buildingState ?? ""}
              </div>
            </div>
            <button
              type="button"
              onClick={() => setPicked(null)}
              className="text-xs text-muted-foreground hover:text-foreground"
            >
              Change
            </button>
          </div>
        )}
      </div>

      <div>
        <Label htmlFor="flat-number">
          Your flat number <span className="text-destructive">*</span>
        </Label>
        <Input
          id="flat-number"
          value={flatNumber}
          onChange={(e) => setFlatNumber(e.target.value)}
          placeholder="e.g. 203"
          maxLength={32}
          className="mt-1.5"
          disabled={!picked}
          aria-invalid={
            previewQ.data ? !previewQ.data.exists || !previewQ.data.occupied : false
          }
        />
        {picked && debouncedFlat.length >= 1 && previewQ.data && (
          !previewQ.data.exists ? (
            <p className="text-[11px] text-destructive mt-1 flex items-start gap-1">
              <AlertCircle className="size-3 mt-0.5 shrink-0" />
              No flat "{debouncedFlat}" in {picked.buildingName}. Double-check
              the flat number.
            </p>
          ) : !previewQ.data.occupied ? (
            <p className="text-[11px] text-destructive mt-1 flex items-start gap-1">
              <AlertCircle className="size-3 mt-0.5 shrink-0" />
              Flat {debouncedFlat} is currently vacant. Only a current
              resident can apply to maintain the society.
            </p>
          ) : (
            <p className="text-[11px] text-success mt-1 flex items-start gap-1">
              <CheckCircle2 className="size-3 mt-0.5 shrink-0" />
              Flat {debouncedFlat} found.
            </p>
          )
        )}
        {(!picked || debouncedFlat.length === 0) && (
          <p className="text-[11px] text-muted-foreground mt-1">
            The building owner uses your flat number to verify you actually
            live there before granting maintainer access.
          </p>
        )}
      </div>

      <div>
        <Label htmlFor="note">Note to the owner (optional)</Label>
        <Textarea
          id="note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Anything the building owner should know?"
          rows={2}
          maxLength={500}
          className="mt-1.5"
        />
      </div>

      <Button
        type="button"
        size="lg"
        variant="gradient"
        className="w-full"
        disabled={!picked || !flatNumber.trim() || claimM.isPending}
        onClick={() => claimM.mutate()}
      >
        {claimM.isPending && <Loader2 className="size-4 animate-spin mr-2" />}
        <Building2 className="size-4 mr-2" />
        Request maintainer access
      </Button>
    </div>
  );
}
