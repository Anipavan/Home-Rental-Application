import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  AlertCircle,
  Building2,
  CheckCircle2,
  Loader2,
  Plus,
  Search,
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
import { useAuthStore } from "@/stores/auth-store";
import { cn } from "@/lib/utils";
import type { BuildingResponseDTO } from "@/types/api";

type Mode = "pick" | "create";

/**
 * Phase 5 — Society setup for a fresh MAINTAINER signup.
 *
 * <p>Two paths in one page:
 *   1. "My society is already registered" → search + pick → submit a
 *      MAINTAINER membership claim. The backend either auto-approves
 *      (no existing maintainer) or holds it for dual approval (owner
 *      + current maintainer both need to say yes).
 *   2. "It's not registered yet" → the original small form that hits
 *      POST /properties/buildings/create/building. The row's ownerId
 *      = maintainer's own userId (schema requires non-null); frontend
 *      role-routing keeps maintainer-only accounts on /maintainer.
 */
export function SetupSocietyPage() {
  const { authUserId } = useAuthStore();
  const [mode, setMode] = useState<Mode>("pick");

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
            {mode === "pick"
              ? "If your building is already registered here, pick it below and we'll send a maintainer-access request."
              : "Not registered yet? Fill in the basics and we'll create the building for you."}
          </p>

          <div className="grid grid-cols-2 gap-2 mt-5">
            <ModeCard
              label="Pick existing"
              desc="Society is already here"
              icon={Search}
              active={mode === "pick"}
              onClick={() => setMode("pick")}
            />
            <ModeCard
              label="Register new"
              desc="Add my building"
              icon={Plus}
              active={mode === "create"}
              onClick={() => setMode("create")}
            />
          </div>

          <div className="mt-6">
            {mode === "pick" ? (
              <PickExistingForm />
            ) : (
              <CreateNewForm authUserId={authUserId} />
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}

function ModeCard({
  label,
  desc,
  icon: Icon,
  active,
  onClick,
}: {
  label: string;
  desc: string;
  icon: typeof Search;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "text-left p-3 rounded-xl border-2 transition-all",
        active
          ? "border-primary bg-primary/5 ring-2 ring-primary/20"
          : "border-border bg-card hover:border-primary/40",
      )}
    >
      <Icon className="size-4 text-primary mb-1.5" />
      <div className="font-semibold text-sm">{label}</div>
      <div className="text-xs text-muted-foreground mt-0.5">{desc}</div>
    </button>
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
              Type your building name and press Search. Nothing matches?
              Switch to "Register new" above.
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

function CreateNewForm({ authUserId }: { authUserId: string | null }) {
  const navigate = useNavigate();
  const [clientError, setClientError] = useState<string | null>(null);

  const createM = useMutation({
    mutationFn: async (body: {
      name: string;
      address: string;
      city: string;
      state: string;
      totalFlats: number;
    }) => {
      if (!authUserId) throw new Error("Not signed in.");
      return propertiesApi.buildings.create({
        buildingName: body.name,
        ownerId: authUserId,
        buildingAddress: body.address,
        buildingCity: body.city,
        buildingState: body.state,
        buildingTotalFloors: 1,
        buildingTotalFlats: body.totalFlats,
      });
    },
    onSuccess: () => {
      toast({
        title: "Society registered",
        description:
          "Add flats next so residents can join. You'll see join requests on your dashboard.",
      });
      navigate("/maintainer");
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't register your society",
        description: extractErrorMessage(err),
      });
    },
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setClientError(null);
    const fd = new FormData(e.currentTarget);
    const name = String(fd.get("name") ?? "").trim();
    const address = String(fd.get("address") ?? "").trim();
    const city = String(fd.get("city") ?? "").trim();
    const state = String(fd.get("state") ?? "").trim();
    const totalFlatsRaw = String(fd.get("totalFlats") ?? "").trim();
    const totalFlats = Number(totalFlatsRaw);

    if (!name || !address || !city || !state || !totalFlatsRaw) {
      setClientError("Fill every field.");
      return;
    }
    if (!Number.isFinite(totalFlats) || totalFlats < 1) {
      setClientError("Total flats must be a positive number.");
      return;
    }
    createM.mutate({ name, address, city, state, totalFlats });
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <div>
        <Label htmlFor="name">Building / society name</Label>
        <Input
          id="name"
          name="name"
          required
          placeholder="Sunshine Valley"
          className="mt-1.5"
        />
      </div>

      <div>
        <Label htmlFor="address">Address</Label>
        <Textarea
          id="address"
          name="address"
          required
          rows={2}
          placeholder="12th Main Road, Sector 5"
          className="mt-1.5"
        />
      </div>

      <div className="grid sm:grid-cols-2 gap-4">
        <div>
          <Label htmlFor="city">City</Label>
          <Input
            id="city"
            name="city"
            required
            placeholder="Bengaluru"
            className="mt-1.5"
          />
        </div>
        <div>
          <Label htmlFor="state">State</Label>
          <Input
            id="state"
            name="state"
            required
            placeholder="Karnataka"
            className="mt-1.5"
          />
        </div>
      </div>

      <div>
        <Label htmlFor="totalFlats">Total flats</Label>
        <Input
          id="totalFlats"
          name="totalFlats"
          type="number"
          required
          min={1}
          placeholder="e.g. 24"
          className="mt-1.5"
        />
        <p className="text-[11px] text-muted-foreground mt-1">
          Approximate is fine — you can add each flat's exact number later.
        </p>
      </div>

      {clientError && (
        <p className="text-sm text-destructive">{clientError}</p>
      )}

      <Button
        type="submit"
        size="lg"
        variant="gradient"
        className="w-full"
        disabled={createM.isPending}
      >
        {createM.isPending && (
          <Loader2 className="size-4 animate-spin mr-2" />
        )}
        Register society
      </Button>
    </form>
  );
}
