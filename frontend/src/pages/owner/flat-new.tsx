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
