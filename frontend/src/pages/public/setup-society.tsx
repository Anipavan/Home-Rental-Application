import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { propertiesApi } from "@/lib/api/properties";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/auth-store";

/**
 * Phase 5 — Society setup for a fresh MAINTAINER signup.
 *
 * <p>Small form: building name + address + city + state + total flats
 * (informational). Server-side we reuse the existing
 * {@code POST /properties/buildings/create/building} endpoint — it
 * doesn't enforce OWNER role so a MAINTAINER can call it. The
 * building row has {@code ownerId = maintainer's own userId} (schema
 * requires non-null), which is fine because the frontend
 * role-routing sends MAINTAINER users only to /maintainer — they
 * never see /owner even though they technically "own" the row.
 *
 * <p>Follow-up: automatic SocietyConfig creation with default per-flat
 * amount + public-view token. Today the maintainer sets those up
 * from the /maintainer dashboard once the building exists.
 */
export function SetupSocietyPage() {
  const navigate = useNavigate();
  const { authUserId } = useAuthStore();
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
    <div className="min-h-screen flex items-center justify-center bg-muted/30 p-4 py-10">
      <div className="w-full max-w-lg">
        <div className="flex justify-center mb-6">
          <Logo />
        </div>
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-2xl font-bold tracking-tight">
            Register your society
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Basic building details. You can add flats and configure the
            monthly maintenance amount from your dashboard next.
          </p>

          <form onSubmit={onSubmit} className="space-y-4 mt-6">
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
                Approximate is fine — you can add each flat's exact number
                later.
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
        </Card>
      </div>
    </div>
  );
}
