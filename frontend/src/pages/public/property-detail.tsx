import { useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Bed,
  Bath,
  Square,
  MapPin,
  Calendar,
  Phone,
  ShieldCheck,
  ArrowLeft,
  Wifi,
  Car,
  Dumbbell,
  Trees,
  CheckCircle2,
} from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { ReviewList } from "@/components/reviews/review-list";
import { formatINR, formatDate } from "@/lib/utils";
import { getPlaceholderImage } from "@/components/property/property-card";
import { PropertyEnquiryDialog } from "@/components/property/property-enquiry-dialog";
import { FavoriteButton } from "@/components/property/favorite-button";

const amenities = [
  { icon: Wifi, label: "High-speed Wi-Fi" },
  { icon: Car, label: "Car parking" },
  { icon: Dumbbell, label: "Gym" },
  { icon: Trees, label: "Garden" },
];

export function PropertyDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  // Flat.id is a String UUID on the backend. Coercing to Number here was the
  // bug behind public property-detail "Not found" pages.
  const flatId = id ?? "";

  // Enquiry-dialog state. Two modes share one dialog component — see
  // PropertyEnquiryDialog. Buttons used to hard-link to /login, which
  // broke the visitor flow entirely; now they open a contextual dialog
  // that either surfaces the owner contact (auth + lookup OK) or
  // captures a lead via support tickets.
  const [enquiryMode, setEnquiryMode] =
    useState<"contact" | "visit" | null>(null);

  const flatQ = useQuery({
    queryKey: ["flat", flatId],
    queryFn: () => propertiesApi.flats.get(flatId),
    enabled: !!flatId,
  });

  const buildingQ = useQuery({
    queryKey: ["building", flatQ.data?.buildingId],
    queryFn: () => propertiesApi.buildings.get(flatQ.data!.buildingId),
    enabled: !!flatQ.data?.buildingId,
  });

  if (flatQ.isLoading) {
    return (
      <div className="container py-8 space-y-6">
        <Skeleton className="aspect-[16/9] rounded-2xl" />
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-4 w-1/3" />
      </div>
    );
  }

  if (flatQ.isError || !flatQ.data) {
    return (
      <div className="container py-16 text-center">
        <p className="text-muted-foreground">Listing not found.</p>
        <Button asChild variant="link" className="mt-3">
          <Link to="/browse">← Back to browse</Link>
        </Button>
      </div>
    );
  }

  const flat = flatQ.data;
  const b = buildingQ.data;
  const heroImg = getPlaceholderImage(flat.id);
  const galleryImgs = [1, 2, 3, 4].map((i) => getPlaceholderImage(`${flat.id}-${i}`));

  return (
    <div className="container py-6 lg:py-8">
      <Button asChild variant="ghost" size="sm" className="mb-4">
        <Link to="/browse">
          <ArrowLeft /> Back to listings
        </Link>
      </Button>

      <div className="grid gap-3 lg:grid-cols-[2fr_1fr] aspect-[16/9] lg:aspect-auto lg:h-[480px]">
        <div className="rounded-2xl overflow-hidden bg-muted relative">
          <img src={heroImg} alt="Hero" className="w-full h-full object-cover" />
          <Badge
            variant={flat.isOccupied ? "secondary" : "success"}
            className="absolute top-4 left-4 bg-white/90 backdrop-blur"
          >
            {flat.isOccupied ? "Occupied" : "Available now"}
          </Badge>
        </div>
        <div className="hidden lg:grid grid-cols-2 gap-3">
          {galleryImgs.map((img, i) => (
            <div
              key={i}
              className="rounded-2xl overflow-hidden bg-muted aspect-square"
            >
              <img src={img} alt={`Gallery ${i}`} className="w-full h-full object-cover" />
            </div>
          ))}
        </div>
      </div>

      <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_360px]">
        <div className="space-y-8">
          <div>
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <div>
                <h1 className="font-display text-3xl font-bold tracking-tight">
                  {b?.buildingName ? `${b.buildingName} · ${flat.flatNumber}` : `Flat ${flat.flatNumber}`}
                </h1>
                <p className="text-muted-foreground flex items-center gap-1.5 mt-2">
                  <MapPin className="size-4" />
                  {b ? `${b.buildingAddress}, ${b.buildingCity}, ${b.buildingState}` : "—"}
                </p>
              </div>
              <Badge variant="default" className="gap-1">
                <ShieldCheck className="size-3" /> Verified owner
              </Badge>
            </div>
          </div>

          <Card className="p-6">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 sm:gap-6">
              <Stat icon={Bed} label="Bedrooms" value={`${flat.bedrooms ?? 2}`} />
              <Stat icon={Bath} label="Bathrooms" value={`${flat.bathrooms ?? 2}`} />
              <Stat icon={Square} label="Area" value={`${flat.areaSqft ?? "—"} sqft`} />
              <Stat icon={Calendar} label="Floor" value={`${flat.floor ?? "—"}`} />
            </div>
          </Card>

          <section>
            <h2 className="font-display text-xl font-semibold mb-3">About this home</h2>
            <p className="text-muted-foreground leading-relaxed">
              A bright {flat.bedrooms ?? 2}BHK in a quiet, well-connected pocket
              of {b?.buildingCity ?? "the city"}. The building has{" "}
              {b?.buildingTotalFloors ?? 5} floors and {b?.buildingTotalFlats ?? 20} flats, with
              the unit on the {flat.floor ?? 3}rd floor. Cross-ventilated, with
              ample natural light through the day.
            </p>
          </section>

          <section>
            <h2 className="font-display text-xl font-semibold mb-3">Amenities</h2>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {amenities.map((a) => (
                <div
                  key={a.label}
                  className="flex items-center gap-2.5 p-3 rounded-xl bg-secondary/40 border border-border/40"
                >
                  <a.icon className="size-4 text-primary" />
                  <span className="text-sm font-medium">{a.label}</span>
                </div>
              ))}
            </div>
          </section>

          <section>
            <h2 className="font-display text-xl font-semibold mb-3">What's included</h2>
            <ul className="grid gap-2 sm:grid-cols-2">
              {[
                "Modular kitchen with chimney",
                "Wardrobes in all bedrooms",
                "RO water purifier",
                "Power back-up",
                "Lift access",
                "24/7 security",
              ].map((t) => (
                <li key={t} className="flex items-start gap-2 text-sm">
                  <CheckCircle2 className="size-4 text-success mt-0.5 shrink-0" />
                  {t}
                </li>
              ))}
            </ul>
          </section>

          <section>
            <h2 className="font-display text-xl font-semibold mb-3">Reviews</h2>
            <ReviewList targetType="PROPERTY" targetId={String(flat.buildingId)} />
          </section>
        </div>

        <aside className="lg:sticky lg:top-20 self-start space-y-4">
          <Card className="p-6">
            <p className="text-sm text-muted-foreground">Monthly rent</p>
            <p className="font-display text-3xl font-bold mt-1">
              {formatINR(flat.rentAmount)}
            </p>
            <Separator className="my-5" />
            <div className="space-y-2 text-sm">
              <Row label="Security deposit" value={formatINR(Number(flat.rentAmount) * 2)} />
              <Row label="Maintenance" value="₹2,000 / mo" />
              <Row label="Available from" value={formatDate(flat.leaseStartDate) ?? "Immediate"} />
            </div>
            <Button
              size="lg"
              variant="gradient"
              className="w-full mt-6"
              onClick={() => setEnquiryMode("contact")}
            >
              <Phone /> Contact owner
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="w-full mt-2"
              onClick={() => setEnquiryMode("visit")}
            >
              Schedule a visit
            </Button>
            <div className="mt-3">
              <FavoriteButton flatId={flat.id} variant="detail" className="w-full justify-center" />
            </div>
            <p className="text-xs text-muted-foreground text-center mt-4">
              You won't be charged anything to enquire.
            </p>
          </Card>

          <PropertyEnquiryDialog
            open={enquiryMode !== null}
            onOpenChange={(v) => {
              if (!v) setEnquiryMode(null);
            }}
            mode={enquiryMode ?? "contact"}
            ownerId={b?.ownerId}
            flatId={flat.id}
            buildingId={flat.buildingId}
            propertyLabel={
              b?.buildingName
                ? `${b.buildingName} · ${flat.flatNumber}`
                : `Flat ${flat.flatNumber}`
            }
            contextUrl={location.pathname}
          />

          <Card className="p-6">
            <p className="font-display font-semibold">Why renters trust Hearth</p>
            <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
              <li className="flex gap-2">
                <ShieldCheck className="size-4 text-success shrink-0 mt-0.5" />
                Verified ownership documents
              </li>
              <li className="flex gap-2">
                <ShieldCheck className="size-4 text-success shrink-0 mt-0.5" />
                Digitally signed agreements
              </li>
              <li className="flex gap-2">
                <ShieldCheck className="size-4 text-success shrink-0 mt-0.5" />
                Deposit refund tracking
              </li>
            </ul>
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
      <p className="font-display font-semibold text-lg mt-0.5">{value}</p>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}
