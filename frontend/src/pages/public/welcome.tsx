import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Building2, Home, Loader2, Users } from "lucide-react";
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

type WelcomeChoice = "TENANT" | "OWNER" | "SOCIETY_FOUNDER" | "MAINTAINER";

/**
 * Phase 4 — "What brings you here today?" — the post-signup
 * differentiation screen. Users pick from four cards; each dispatches
 * a role-appropriate follow-up:
 *
 *   TENANT           → /app (no role change; the default at signup).
 *   OWNER            → POST /auth/me/role (OWNER) → /owner.
 *   SOCIETY_FOUNDER  → POST /auth/me/role (OWNER) → /owner/buildings/new
 *                      (framed as "set up your society").
 *   MAINTAINER       → building picker + flat number → POST
 *                      /society/claims (MAINTAINER) → /pending-claim
 *                      (existing claim-approval flow, unchanged).
 *
 * Reachable by anyone signed in — new signups land here from the
 * simplified register flow; existing users can bookmark it if they
 * ever want to switch roles.
 */
export function WelcomePage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [choice, setChoice] = useState<WelcomeChoice | null>(null);
  const [pickedBuilding, setPickedBuilding] =
    useState<BuildingResponseDTO | null>(null);
  const [flatNumber, setFlatNumber] = useState("");
  const [note, setNote] = useState("");
  const [buildingQuery, setBuildingQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<BuildingResponseDTO[]>([]);

  const roleMut = useMutation({
    mutationFn: async (target: WelcomeChoice) => {
      if (target === "TENANT") {
        return { destination: "/app" as const };
      }
      if (target === "OWNER" || target === "SOCIETY_FOUNDER") {
        const auth = await authApi.setPrimaryRole("OWNER");
        setSession(auth);
        return {
          destination:
            target === "SOCIETY_FOUNDER"
              ? ("/owner/buildings/new" as const)
              : ("/owner" as const),
          societyFounder: target === "SOCIETY_FOUNDER",
        };
      }
      // MAINTAINER — submit the claim as the currently-logged-in TENANT.
      if (!pickedBuilding) {
        throw new Error("Search and pick your society's building first.");
      }
      if (!flatNumber.trim()) {
        throw new Error("Enter your flat number so the maintainer can find you.");
      }
      await claimsApi.create({
        buildingId: pickedBuilding.buildingId,
        requestedRole: "MAINTAINER",
        claimedFlatNumber: flatNumber.trim(),
        applicantNote: note.trim() || undefined,
      });
      return { destination: "/pending-claim" as const };
    },
    onSuccess: (result) => {
      if ("societyFounder" in result && result.societyFounder) {
        toast({
          title: "Welcome — let's set up your society",
          description:
            "Add your building details next. Invite residents once a few flats are listed.",
        });
        navigate(result.destination, { state: { fromSocietyFounder: true } });
        return;
      }
      if (result.destination === "/pending-claim") {
        toast({
          title: "Request submitted",
          description:
            "The building owner will review your request. You'll get access once they approve.",
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
              label="I'm starting a society"
              desc="Set up a new RWA / society and invite residents."
              icon={Building2}
              active={choice === "SOCIETY_FOUNDER"}
              onClick={() => setChoice("SOCIETY_FOUNDER")}
            />
            <WelcomeCard
              label="I'm a maintainer"
              desc="Join an existing society's books, dues, expenses."
              icon={Users}
              active={choice === "MAINTAINER"}
              onClick={() => setChoice("MAINTAINER")}
            />
          </div>

          {choice === "MAINTAINER" && (
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
                />
                <p className="text-[11px] text-muted-foreground mt-1">
                  Required so the building owner can verify you live there.
                </p>
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
