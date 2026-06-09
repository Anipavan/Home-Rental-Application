import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useState } from "react";

export function FlatNewPage() {
  const { authUserId } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const [buildingId, setBuildingId] = useState<string>(
    params.get("building") ?? "",
  );
  // Tenant-preference toggles. Default ON so an owner who doesn't
  // care to restrict is maximally inclusive — they have to actively
  // un-check to filter renters out. Common Indian rental UX
  // ("bachelor not allowed" / "family only" tags) maps to flipping
  // exactly one of these to OFF; flipping both off would hide the
  // listing from everyone using either filter, so the form blocks
  // that pathological combo via the disabled-submit guard below.
  const [acceptsBachelor, setAcceptsBachelor] = useState(true);
  const [acceptsFamily, setAcceptsFamily] = useState(true);

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const m = useMutation({
    mutationFn: propertiesApi.flats.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-all-flats"] });
      qc.invalidateQueries({ queryKey: ["flats-by-building"] });
      toast({ title: "Flat added" });
      navigate("/owner/flats");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't add flat",
        description: extractErrorMessage(e),
      }),
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!buildingId) {
      toast({ variant: "destructive", title: "Pick a building first" });
      return;
    }
    const fd = new FormData(e.currentTarget);
    m.mutate({
      buildingId,
      flatNumber: String(fd.get("flatNumber") ?? ""),
      floor: Number(fd.get("floor") ?? 0),
      bedrooms: Number(fd.get("bedrooms") ?? 1),
      bathrooms: Number(fd.get("bathrooms") ?? 1),
      areaSqft: Number(fd.get("areaSqft") ?? 0),
      rentAmount: Number(fd.get("rentAmount") ?? 0),
      acceptsBachelor,
      acceptsFamily,
      // V11: explicit listed-for-rent on create. Server default is
      // also TRUE so omitting would work, but being explicit makes
      // the intent obvious to anyone reading the payload in a
      // network tab — and shields us from any future server-side
      // default flip. Owners hide the flat post-create via the
      // EditFlatDialog toggle.
      availableForRent: true,
    });
  }

  return (
    <div className="animate-fade-in max-w-2xl">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/owner/flats">
          <ArrowLeft /> Back
        </Link>
      </Button>
      <PageHeader title="Add a flat" />

      <Card>
        <CardContent className="p-6 sm:p-8">
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <Label>Building</Label>
              <Select value={buildingId} onValueChange={setBuildingId}>
                <SelectTrigger className="mt-1.5">
                  <SelectValue placeholder="Pick a building" />
                </SelectTrigger>
                <SelectContent>
                  {(buildingsQ.data ?? []).map((b) => (
                    <SelectItem key={b.buildingId} value={b.buildingId}>
                      {b.buildingName} — {b.buildingCity}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <Field label="Flat number" name="flatNumber" required />
              <Field label="Floor" name="floor" type="number" />
            </div>
            <div className="grid sm:grid-cols-3 gap-4">
              <Field label="Bedrooms" name="bedrooms" type="number" />
              <Field label="Bathrooms" name="bathrooms" type="number" />
              <Field label="Area (sqft)" name="areaSqft" type="number" />
            </div>
            <Field label="Monthly rent (₹)" name="rentAmount" type="number" required />

            {/* Tenant-preference toggles. Default both ON so the
                listing is visible to every renter. Owners who only
                want one type of tenant un-check the other — that
                flips the listing out of the corresponding browse
                filter on /app/browse. */}
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">
                Who can rent this flat?
              </legend>
              <p className="text-[11px] text-muted-foreground -mt-1">
                Both ticked = open to everyone (default). Uncheck to
                restrict — your listing will be hidden from renters
                who don't match.
              </p>
              <label className="flex items-center gap-2.5 text-sm cursor-pointer">
                <input
                  type="checkbox"
                  checked={acceptsBachelor}
                  onChange={(e) => setAcceptsBachelor(e.target.checked)}
                  className="size-4 rounded border-input accent-primary cursor-pointer"
                />
                <span>Open to bachelors (single tenants, PG-style)</span>
              </label>
              <label className="flex items-center gap-2.5 text-sm cursor-pointer">
                <input
                  type="checkbox"
                  checked={acceptsFamily}
                  onChange={(e) => setAcceptsFamily(e.target.checked)}
                  className="size-4 rounded border-input accent-primary cursor-pointer"
                />
                <span>Open to families (married couples / with children)</span>
              </label>
              {!acceptsBachelor && !acceptsFamily && (
                <p
                  role="alert"
                  className="text-[11px] text-destructive bg-destructive/10 border border-destructive/20 rounded-md px-2 py-1.5"
                >
                  At least one box should be ticked — otherwise the
                  listing will be invisible to every renter using these
                  filters.
                </p>
              )}
            </fieldset>

            <div className="flex justify-end gap-2 pt-2">
              <Button asChild variant="ghost">
                <Link to="/owner/flats">Cancel</Link>
              </Button>
              <Button type="submit" variant="gradient" disabled={m.isPending}>
                {m.isPending && <Loader2 className="animate-spin" />}
                Add flat
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({
  label,
  name,
  type = "text",
  required,
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
}) {
  return (
    <div>
      <Label htmlFor={name}>{label}</Label>
      <Input id={name} name={name} type={type} required={required} className="mt-1.5" />
    </div>
  );
}
