import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2 } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

export function BuildingNewPage() {
  const { authUserId } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const m = useMutation({
    mutationFn: propertiesApi.buildings.create,
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["my-buildings"] });
      toast({ title: "Building added", description: `${data.buildingName} is live.` });
      navigate("/owner/buildings");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't add building",
        description: extractErrorMessage(e),
      }),
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!authUserId) return;
    const fd = new FormData(e.currentTarget);
    m.mutate({
      ownerId: authUserId,
      buildingName: String(fd.get("buildingName") ?? ""),
      buildingAddress: String(fd.get("buildingAddress") ?? ""),
      buildingCity: String(fd.get("buildingCity") ?? ""),
      buildingState: String(fd.get("buildingState") ?? ""),
      buildingTotalFloors: Number(fd.get("buildingTotalFloors") ?? 0),
      buildingTotalFlats: Number(fd.get("buildingTotalFlats") ?? 0),
      amenities: String(fd.get("amenities") ?? ""),
    });
  }

  return (
    <div className="animate-fade-in max-w-2xl">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/owner/buildings">
          <ArrowLeft /> Back
        </Link>
      </Button>
      <PageHeader
        title="Add a building"
        description="The big-picture details. You'll add flats next."
      />

      <Card>
        <CardContent className="p-6 sm:p-8">
          <form onSubmit={onSubmit} className="space-y-5">
            <div>
              <Label htmlFor="buildingName">Building name</Label>
              <Input id="buildingName" name="buildingName" required className="mt-1.5" placeholder="Sunshine Apartments" />
            </div>
            <div>
              <Label htmlFor="buildingAddress">Address</Label>
              <Input id="buildingAddress" name="buildingAddress" required className="mt-1.5" />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <Field label="City" name="buildingCity" required />
              <Field label="State" name="buildingState" required />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <Field label="Total floors" name="buildingTotalFloors" type="number" min={1} required />
              <Field label="Total flats (6–20)" name="buildingTotalFlats" type="number" min={6} max={20} required />
            </div>
            <div>
              <Label htmlFor="amenities">Amenities</Label>
              <Textarea
                id="amenities"
                name="amenities"
                rows={3}
                className="mt-1.5"
                placeholder="e.g. Lift, parking, gym, swimming pool"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button asChild variant="ghost">
                <Link to="/owner/buildings">Cancel</Link>
              </Button>
              <Button type="submit" variant="gradient" disabled={m.isPending}>
                {m.isPending && <Loader2 className="animate-spin" />}
                Add building
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
  min,
  max,
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  min?: number;
  max?: number;
}) {
  return (
    <div>
      <Label htmlFor={name}>{label}</Label>
      <Input
        id={name}
        name={name}
        type={type}
        required={required}
        min={min}
        max={max}
        className="mt-1.5"
      />
    </div>
  );
}
