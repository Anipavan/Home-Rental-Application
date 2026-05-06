import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  Bed,
  Bath,
  Square,
  Calendar,
  Building2,
  MapPin,
  Phone,
  Wrench,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Separator } from "@/components/ui/separator";
import { formatINR, formatDate } from "@/lib/utils";
import { getPlaceholderImage } from "@/components/property/property-card";

export function MyFlatPage() {
  const { authUserId } = useAuthStore();
  const flatsQ = useQuery({
    queryKey: ["my-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId!),
    enabled: !!authUserId,
  });
  const flat = flatsQ.data?.[0];
  const buildingQ = useQuery({
    queryKey: ["building", flat?.buildingId],
    queryFn: () => propertiesApi.buildings.get(flat!.buildingId),
    enabled: !!flat?.buildingId,
  });

  if (flatsQ.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="aspect-video rounded-2xl" />
      </div>
    );
  }

  if (!flat) {
    return (
      <div className="animate-fade-in">
        <PageHeader title="My home" />
        <Card className="p-12 text-center">
          <Building2 className="size-12 mx-auto text-muted-foreground" />
          <p className="font-display text-lg mt-4">You're not in a home yet.</p>
          <p className="text-muted-foreground text-sm mt-1 max-w-sm mx-auto">
            Browse listings and reach out to owners — once they assign you to a
            flat, it'll appear here.
          </p>
          <Button asChild variant="gradient" className="mt-5">
            <Link to="/browse">Browse homes</Link>
          </Button>
        </Card>
      </div>
    );
  }

  const b = buildingQ.data;

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="My home"
        description="Everything about your current rental, in one place."
      />

      <div className="rounded-2xl overflow-hidden mb-6 aspect-[16/9] sm:aspect-[3/1] bg-muted">
        <img
          src={getPlaceholderImage(flat.id)}
          alt="Home"
          className="w-full h-full object-cover"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <Card>
            <CardContent className="p-6">
              <h2 className="font-display text-2xl font-bold">
                {b?.buildingName ? `${b.buildingName} · Flat ${flat.flatNumber}` : `Flat ${flat.flatNumber}`}
              </h2>
              <p className="text-muted-foreground flex items-center gap-1.5 mt-2">
                <MapPin className="size-4" />
                {b ? `${b.buildingAddress}, ${b.buildingCity}, ${b.buildingState}` : "—"}
              </p>
              <Separator className="my-5" />
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-5">
                <Stat icon={Bed} label="Bedrooms" value={String(flat.bedrooms ?? 2)} />
                <Stat icon={Bath} label="Bathrooms" value={String(flat.bathrooms ?? 2)} />
                <Stat icon={Square} label="Area" value={`${flat.areaSqft ?? "—"} sqft`} />
                <Stat icon={Calendar} label="Floor" value={String(flat.floor ?? "—")} />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-6">
              <h3 className="font-display font-semibold text-lg mb-4">
                Lease details
              </h3>
              <div className="grid sm:grid-cols-2 gap-4 text-sm">
                <Row label="Start date" value={formatDate(flat.leaseStartDate)} />
                <Row label="End date" value={formatDate(flat.leaseEndDate)} />
                <Row label="Monthly rent" value={formatINR(flat.rentAmount)} />
                <Row
                  label="Security deposit"
                  value={formatINR(Number(flat.rentAmount) * 2)}
                />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-6">
              <h3 className="font-display font-semibold text-lg mb-3">
                House rules
              </h3>
              <ul className="text-sm space-y-2 text-muted-foreground">
                <li>• No smoking inside the flat.</li>
                <li>• Pets allowed with owner consent.</li>
                <li>• Visitors welcome till 10pm.</li>
                <li>• Use designated parking only.</li>
              </ul>
            </CardContent>
          </Card>
        </div>

        <aside className="space-y-4">
          <Card className="p-6">
            <h3 className="font-display font-semibold mb-3">Quick actions</h3>
            <div className="space-y-2">
              <Button asChild className="w-full" variant="gradient">
                <Link to="/app/payments">Pay rent</Link>
              </Button>
              <Button asChild className="w-full" variant="outline">
                <Link to="/app/maintenance/new">
                  <Wrench /> Raise an issue
                </Link>
              </Button>
              <Button className="w-full" variant="outline">
                <Phone /> Contact owner
              </Button>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
}) {
  return (
    <div>
      <Icon className="size-4 text-muted-foreground" />
      <p className="text-xs text-muted-foreground mt-1.5">{label}</p>
      <p className="font-display font-semibold text-lg">{value}</p>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium mt-0.5">{value}</p>
    </div>
  );
}
