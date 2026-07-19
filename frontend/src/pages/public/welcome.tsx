import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  AlertCircle,
  Building2,
  CheckCircle2,
  Home,
  Loader2,
  Users,
} from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { authApi } from "@/lib/api/auth";
import { claimsApi } from "@/lib/api/claims";
import { propertiesApi } from "@/lib/api/properties";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/auth-store";
import { cn } from "@/lib/utils";
import type { BuildingResponseDTO } from "@/types/api";

type WelcomeChoice = "TENANT" | "OWNER" | "MAINTAINER" | "MAINTAINEE";

/** Two flavours of "I'm an owner":
 *   NEW_BUILDING — user owns the whole building (or plans to
 *     register one). Standard OWNER-role bump + land on /owner
 *     where they add the building.
 *   EXISTING_FLAT — user owns a specific flat inside a building
 *     someone else already registered on the platform. Submits a
 *     FLAT_OWNER MembershipClaim; the building owner (not the
 *     maintainer — this is a legal ownership decision) approves.
 */
type OwnerMode = "NEW_BUILDING" | "EXISTING_FLAT";

/**
 * Phase 5 — "What brings you here today?" — the post-signup role
 * picker. Two clean worlds:
 *
 *   Rental marketplace:
 *     TENANT     → /app (no role change; TENANT is the signup default)
 *     OWNER      → POST /auth/me/role (OWNER) → /owner
 *
 *   Society management:
 *     MAINTAINER → POST /auth/me/role (MAINTAINER) → /setup-society →
 *                  register their building + flats, then /maintainer.
 *                  Maintainers can't see rent/tenant data; they only
 *                  see maintenance dues + the maintainees of their
 *                  building.
 *     MAINTAINEE → building picker (must be a maintainer-registered
 *                  building) + flat number → POST /society/claims
 *                  with requestedRole=RESIDENT → /pending-claim →
 *                  maintainer approves. After approval the user gets
 *                  a maintenance-only dashboard.
 */
export function WelcomePage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [choice, setChoice] = useState<WelcomeChoice | null>(null);
  const [ownerMode, setOwnerMode] = useState<OwnerMode>("NEW_BUILDING");
  const [pickedBuilding, setPickedBuilding] =
    useState<BuildingResponseDTO | null>(null);
  const [flatNumber, setFlatNumber] = useState("");
  const [note, setNote] = useState("");
  const [buildingQuery, setBuildingQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<BuildingResponseDTO[]>([]);

  // Both the OWNER-EXISTING_FLAT flow and the MAINTAINEE flow need a
  // picked building + flat number, so they reuse the SAME sub-panel.
  // Compute which flow is active for readability.
  const showBuildingFlatPanel =
    choice === "MAINTAINEE"
    || (choice === "OWNER" && ownerMode === "EXISTING_FLAT");

  const roleMut = useMutation({
    mutationFn: async (target: WelcomeChoice) => {
      if (target === "TENANT") {
        return { destination: "/app" as const };
      }
      if (target === "OWNER") {
        if (ownerMode === "NEW_BUILDING") {
          // Standard OWNER onboarding — promote role, land on /owner
          // where they register their building.
          const auth = await authApi.setPrimaryRole("OWNER");
          setSession(auth);
          return { destination: "/owner" as const };
        }
        // EXISTING_FLAT — user owns a specific flat inside an already
        // registered building. Submit a FLAT_OWNER claim; the building
        // owner approves. Role stays TENANT until approval flips
        // flat.flatOwnerId — after approval they get access to their
        // flat via the tenant dashboard (owner-occupier semantics).
        if (!pickedBuilding) {
          throw new Error("Search and pick the building your flat is in.");
        }
        if (!flatNumber.trim()) {
          throw new Error("Enter your flat number so the building owner can verify.");
        }
        await claimsApi.create({
          buildingId: pickedBuilding.buildingId,
          requestedRole: "FLAT_OWNER",
          claimedFlatNumber: flatNumber.trim(),
          applicantNote: note.trim() || undefined,
        });
        return { destination: "/pending-claim" as const };
      }
      if (target === "MAINTAINER") {
        const auth = await authApi.setPrimaryRole("MAINTAINER");
        setSession(auth);
        return { destination: "/setup-society" as const };
      }
      // MAINTAINEE — submit a RESIDENT claim on a maintainer-registered
      // building. The building's maintainer (not owner) approves.
      if (!pickedBuilding) {
        throw new Error("Search and pick your society's building first.");
      }
      if (!flatNumber.trim()) {
        throw new Error("Enter your flat number so the maintainer can find you.");
      }
      await claimsApi.create({
        buildingId: pickedBuilding.buildingId,
        requestedRole: "RESIDENT",
        claimedFlatNumber: flatNumber.trim(),
        applicantNote: note.trim() || undefined,
      });
      return { destination: "/pending-claim" as const };
    },
    onSuccess: (result) => {
      if (result.destination === "/setup-society") {
        toast({
          title: "Welcome, maintainer",
          description:
            "Register your society building next so residents can join.",
        });
      } else if (result.destination === "/pending-claim") {
        toast({
          title: "Request submitted",
          description: choice === "OWNER"
            ? "The building's current owner will review your flat-ownership claim. You'll get access once they approve."
            : "The building maintainer will review your request. You'll get access once they approve.",
        });
      } else {
        toast({ title: "All set" });
      }
      navigate(result.destination);
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't finish",
        description: extractErrorMessage(err),
      });
    },
  });

  async function handleSearch() {
    if (!buildingQuery.trim() || buildingQuery.trim().length < 2) return;
    setSearching(true);
    try {
      const results = await propertiesApi.buildings.search(buildingQuery.trim());
      setSearchResults(results.slice(0, 8));
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

  // Debounced flat-number → building lookup so we can show inline
  // exists/occupied feedback as the maintainee types. Prevents them
  // from submitting a claim that the backend will reject at approve
  // time ("this flat is currently vacant" / "no flat X in building").
  // Backend flats/preview is a public endpoint; safe to call without
  // any auth setup here.
  const [debouncedFlat, setDebouncedFlat] = useState("");
  useEffect(() => {
    const t = setTimeout(() => setDebouncedFlat(flatNumber.trim()), 350);
    return () => clearTimeout(t);
  }, [flatNumber]);
  const previewQ = useQuery({
    queryKey: ["flat-preview", pickedBuilding?.buildingId, debouncedFlat],
    queryFn: () =>
      propertiesApi.flats.preview(pickedBuilding!.buildingId, debouncedFlat),
    enabled: !!pickedBuilding && debouncedFlat.length >= 1,
    staleTime: 30_000,
    retry: false,
  });

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted/30 p-4 py-10">
      <div className="w-full max-w-3xl">
        <div className="flex justify-center mb-6">
          <Logo />
        </div>
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-2xl font-bold tracking-tight">
            What brings you here today?
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Pick one. You can add more later from your profile.
          </p>

          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3 mt-6">
            <WelcomeCard
              label="I'm renting"
              desc="Find a home and pay rent online."
              icon={Home}
              active={choice === "TENANT"}
              onClick={() => setChoice("TENANT")}
            />
            <WelcomeCard
              label="I'm an owner"
              desc="List my property and manage tenants."
              icon={Building2}
              active={choice === "OWNER"}
              onClick={() => setChoice("OWNER")}
            />
            <WelcomeCard
              label="I'm a maintainer"
              desc="Register my society building and collect maintenance dues."
              icon={Building2}
              active={choice === "MAINTAINER"}
              onClick={() => setChoice("MAINTAINER")}
            />
            <WelcomeCard
              label="I'm a maintainee"
              desc="I live in a society-managed building and want to pay dues."
              icon={Users}
              active={choice === "MAINTAINEE"}
              onClick={() => setChoice("MAINTAINEE")}
            />
          </div>

          {choice === "OWNER" && (
            <div className="mt-4 rounded-xl border border-primary/30 bg-primary/5 p-4 space-y-4">
              <div>
                <p className="text-sm font-semibold">
                  Which one describes you?
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Both paths get you owner-level access — the second one
                  needs the building's existing owner to approve.
                </p>
              </div>
              <div className="grid sm:grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setOwnerMode("NEW_BUILDING")}
                  className={cn(
                    "text-left p-3 rounded-lg border-2 transition-all",
                    ownerMode === "NEW_BUILDING"
                      ? "border-primary bg-primary/10 ring-2 ring-primary/20"
                      : "border-border bg-card hover:border-primary/40",
                  )}
                >
                  <div className="font-semibold text-sm">
                    I'm registering a new building
                  </div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    Own the whole building — list flats and manage tenants.
                  </div>
                </button>
                <button
                  type="button"
                  onClick={() => setOwnerMode("EXISTING_FLAT")}
                  className={cn(
                    "text-left p-3 rounded-lg border-2 transition-all",
                    ownerMode === "EXISTING_FLAT"
                      ? "border-primary bg-primary/10 ring-2 ring-primary/20"
                      : "border-border bg-card hover:border-primary/40",
                  )}
                >
                  <div className="font-semibold text-sm">
                    I own a flat in an existing building
                  </div>
                  <div className="text-xs text-muted-foreground mt-0.5">
                    Building's already on the platform — claim your flat.
                  </div>
                </button>
              </div>
            </div>
          )}

          {showBuildingFlatPanel && (
            <div className="mt-4 rounded-xl border border-primary/30 bg-primary/5 p-4 space-y-4">
              <div>
                <Label htmlFor="building-search">Search for your building</Label>
                <div className="flex gap-2 mt-1.5">
                  <Input
                    id="building-search"
                    value={buildingQuery}
                    onChange={(e) => setBuildingQuery(e.target.value)}
                    placeholder="Building name or landmark"
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

                {searchResults.length > 0 && !pickedBuilding && (
                  <ul className="mt-2 space-y-1 border rounded-md p-1 max-h-56 overflow-auto">
                    {searchResults.map((b) => (
                      <li key={b.buildingId}>
                        <button
                          type="button"
                          className="w-full text-left px-2 py-1.5 rounded hover:bg-secondary text-sm"
                          onClick={() => {
                            setPickedBuilding(b);
                            setSearchResults([]);
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

                {pickedBuilding && (
                  <div className="mt-2 flex items-start justify-between rounded-md border border-primary/40 bg-primary/10 px-3 py-2 text-sm">
                    <div>
                      <div className="font-medium">
                        {pickedBuilding.buildingName}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {pickedBuilding.buildingCity ?? ""}{" "}
                        {pickedBuilding.buildingState ?? ""}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setPickedBuilding(null)}
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
                  aria-invalid={
                    previewQ.data ? !previewQ.data.exists || !previewQ.data.occupied : false
                  }
                />
                {/* Inline vacancy preview — mirrors the createClaim
                    server-side guard so the user finds out upfront,
                    not at approval time. */}
                {pickedBuilding && debouncedFlat.length >= 1 && previewQ.data && (
                  !previewQ.data.exists ? (
                    <p className="text-[11px] text-destructive mt-1 flex items-start gap-1">
                      <AlertCircle className="size-3 mt-0.5 shrink-0" />
                      No flat "{debouncedFlat}" in {pickedBuilding.buildingName}.
                      Double-check the flat number.
                    </p>
                  ) : (
                    // Vacant is a hard block for MAINTAINEE (society
                    // residency requires someone actually living there),
                    // but a legit case for FLAT_OWNER (buying an empty
                    // flat to move into). Different messaging per flow.
                    !previewQ.data.occupied && choice === "MAINTAINEE" ? (
                      <p className="text-[11px] text-destructive mt-1 flex items-start gap-1">
                        <AlertCircle className="size-3 mt-0.5 shrink-0" />
                        Flat {debouncedFlat} is currently vacant. Ask the
                        owner to assign a tenant to it first, then submit.
                      </p>
                    ) : (
                      <p className="text-[11px] text-success mt-1 flex items-start gap-1">
                        <CheckCircle2 className="size-3 mt-0.5 shrink-0" />
                        Flat {debouncedFlat} found
                        {!previewQ.data.occupied && " (currently vacant — that's fine)"}.
                      </p>
                    )
                  )
                )}
                {(!pickedBuilding || debouncedFlat.length === 0) && (
                  <p className="text-[11px] text-muted-foreground mt-1">
                    {choice === "OWNER"
                      ? "Required so the current building owner can verify your claim."
                      : "Required so the building owner can verify you live there."}
                  </p>
                )}
              </div>

              <div>
                <Label htmlFor="note">Note (optional)</Label>
                <Textarea
                  id="note"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Anything the owner should know?"
                  rows={2}
                  maxLength={500}
                  className="mt-1.5"
                />
              </div>
            </div>
          )}

          <div className="flex gap-3 justify-end mt-6">
            <Button
              variant="outline"
              onClick={() => navigate("/app")}
              disabled={roleMut.isPending}
            >
              Skip for now
            </Button>
            <Button
              variant="gradient"
              disabled={!choice || roleMut.isPending}
              onClick={() => choice && roleMut.mutate(choice)}
            >
              {roleMut.isPending && (
                <Loader2 className="size-4 animate-spin mr-2" />
              )}
              Continue
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}

function WelcomeCard({
  label,
  desc,
  icon: Icon,
  active,
  onClick,
}: {
  label: string;
  desc: string;
  icon: typeof Home;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "text-left p-4 rounded-xl border-2 transition-all",
        active
          ? "border-primary bg-primary/5 ring-2 ring-primary/20"
          : "border-border bg-card hover:border-primary/40",
      )}
    >
      <Icon className="size-5 text-primary mb-2" />
      <div className="font-semibold text-sm">{label}</div>
      <div className="text-xs text-muted-foreground mt-0.5">{desc}</div>
    </button>
  );
}
