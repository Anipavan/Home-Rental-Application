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
  Waves,
  Snowflake,
  Lock,
  Zap,
  Building2,
  Bus,
  ChefHat,
  Droplets,
  Cpu,
  type LucideIcon,
} from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { ReviewList } from "@/components/reviews/review-list";
import { formatINR, formatDate, floorLabel } from "@/lib/utils";
import { PropertyEnquiryDialog } from "@/components/property/property-enquiry-dialog";
import { PropertyGallery } from "@/components/property/property-gallery";
import { FavoriteButton } from "@/components/property/favorite-button";

/**
 * Best-effort icon picker. Matches a lowercased substring of the
 * user-typed amenity / included-item against known keywords. Falls
 * back to CheckCircle2 for anything we don't recognise — every line
 * still gets *some* icon so the visual grid stays consistent.
 *
 * Add new entries here as owners type new categories. The list is
 * frontend-only on purpose — adding a keyword doesn't require a
 * backend deploy, and the worst case is the generic checkmark.
 */
const ICON_KEYWORDS: Array<{ kw: RegExp; icon: LucideIcon }> = [
  { kw: /wi[-\s]?fi|internet|broadband/i, icon: Wifi },
  { kw: /car park|parking|garage/i, icon: Car },
  { kw: /gym|fitness/i, icon: Dumbbell },
  { kw: /garden|lawn|park|outdoor/i, icon: Trees },
  { kw: /pool|swim/i, icon: Waves },
  { kw: /a[\.\s/-]?c\b|air[-\s]?cond/i, icon: Snowflake },
  { kw: /security|cctv|guard/i, icon: Lock },
  { kw: /power[-\s]?back|generator|inverter|backup/i, icon: Zap },
  { kw: /lift|elevator/i, icon: Building2 },
  { kw: /metro|bus|transport/i, icon: Bus },
  { kw: /kitchen|modular|chimney/i, icon: ChefHat },
  { kw: /water|ro\b|purifier|tank/i, icon: Droplets },
  { kw: /smart|home automation|alexa|iot/i, icon: Cpu },
];

function iconFor(label: string): LucideIcon {
  for (const { kw, icon } of ICON_KEYWORDS) {
    if (kw.test(label)) return icon;
  }
  return CheckCircle2;
}

/**
 * Parses the owner's free-text amenity/included-items field into
 * discrete items. Accepts commas, semicolons, or newlines as
 * separators so the owner can type whatever feels natural. Trims
 * whitespace + drops empty fragments so a trailing comma doesn't
 * render an empty chip.
 */
function parseList(raw: string | undefined | null): string[] {
  if (!raw) return [];
  return raw
    .split(/[\n,;]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

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

  // Parse owner-supplied lists. Empty arrays cause their respective
  // sections to be hidden entirely rather than rendering a hardcoded
  // fallback — keeps the UX honest about what the owner has actually
  // declared.
  const amenityItems = parseList(b?.amenities);
  const includedItems = parseList(b?.includedItems);

  // Security deposit: explicit owner value wins. Falling back to
  // 3 × monthly rent (industry standard in India — was previously
  // computed as 2× which the user flagged).
  const deposit =
    flat.depositAmount && flat.depositAmount > 0
      ? flat.depositAmount
      : Number(flat.rentAmount) * 3;

  return (
    <div className="container py-6 lg:py-8">
      <Button asChild variant="ghost" size="sm" className="mb-4">
        <Link to="/browse">
          <ArrowLeft /> Back to listings
        </Link>
      </Button>

      {/* Hero gallery — only renders images the owner actually
          uploaded. Empty owner gallery → neutral "no photos yet"
          state, never stock placeholders. */}
      <div className="relative">
        <PropertyGallery
          buildingId={flat.buildingId}
          alt={b?.buildingName ?? flat.flatNumber}
        />
        <Badge
          variant={flat.isOccupied ? "secondary" : "success"}
          className="absolute top-4 left-4 bg-white/90 backdrop-blur"
        >
          {flat.isOccupied ? "Occupied" : "Available now"}
        </Badge>
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
              {/* Verified-owner badge — gated on the backend-computed
                  ownerVerified boolean, which itself comes from a
                  Feign call to user-service checking kyc_status.
                  Renders nothing when the owner hasn't completed KYC
                  (the default state until KYC is reactivated). */}
              {b?.ownerVerified && (
                <Badge variant="default" className="gap-1">
                  <ShieldCheck className="size-3" /> Verified owner
                </Badge>
              )}
            </div>
          </div>

          <Card className="p-6">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 sm:gap-6">
              <Stat icon={Bed} label="Bedrooms" value={`${flat.bedrooms ?? "—"}`} />
              <Stat icon={Bath} label="Bathrooms" value={`${flat.bathrooms ?? "—"}`} />
              <Stat icon={Square} label="Area" value={`${flat.areaSqft ?? "—"} sqft`} />
              {/* Floor: owner enters a number, display shows the
                  ordinal English word — 0 → "Ground", 1 → "First",
                  beyond 20 falls back to "21st", "22nd", etc. */}
              <Stat
                icon={Calendar}
                label="Floor"
                value={floorLabel(flat.floor)}
              />
            </div>
          </Card>

          {/* About section — owner-supplied description if present,
              else a short auto-generated blurb that uses ACTUAL flat
              numbers (no "5 floors and 20 flats" hardcoded fallback). */}
          {(flat.description || flat.bedrooms || b?.buildingCity) && (
            <section>
              <h2 className="font-display text-xl font-semibold mb-3">
                About this home
              </h2>
              {flat.description ? (
                <p className="text-muted-foreground leading-relaxed whitespace-pre-wrap">
                  {flat.description}
                </p>
              ) : (
                <p className="text-muted-foreground leading-relaxed">
                  A {flat.bedrooms ?? "—"}BHK
                  {b?.buildingCity ? ` in ${b.buildingCity}` : ""}
                  {b?.buildingTotalFloors
                    ? `. The building has ${b.buildingTotalFloors} floor${
                        b.buildingTotalFloors === 1 ? "" : "s"
                      }${
                        b.buildingTotalFlats
                          ? ` and ${b.buildingTotalFlats} flats`
                          : ""
                      }, with the unit on the ${floorLabel(flat.floor)} floor.`
                    : flat.floor !== undefined && flat.floor !== null
                      ? `. The unit is on the ${floorLabel(flat.floor)} floor.`
                      : "."}
                </p>
              )}
            </section>
          )}

          {/* Amenities — rendered only when the owner actually
              supplied some. Empty → section hidden. */}
          {amenityItems.length > 0 && (
            <section>
              <h2 className="font-display text-xl font-semibold mb-3">
                Amenities
              </h2>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {amenityItems.map((label) => {
                  const Icon = iconFor(label);
                  return (
                    <div
                      key={label}
                      className="flex items-center gap-2.5 p-3 rounded-xl bg-secondary/40 border border-border/40"
                    >
                      <Icon className="size-4 text-primary" />
                      <span className="text-sm font-medium">{label}</span>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {/* What's included — same dynamic treatment, two-column
              checkmark list instead of the icon grid. Empty → hidden. */}
          {includedItems.length > 0 && (
            <section>
              <h2 className="font-display text-xl font-semibold mb-3">
                What's included
              </h2>
              <ul className="grid gap-2 sm:grid-cols-2">
                {includedItems.map((label) => (
                  <li key={label} className="flex items-start gap-2 text-sm">
                    <CheckCircle2 className="size-4 text-success mt-0.5 shrink-0" />
                    {label}
                  </li>
                ))}
              </ul>
            </section>
          )}

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
              <Row
                label="Security deposit"
                value={formatINR(deposit)}
                hint="Equivalent to 3 months' rent"
              />
              <Row label="Maintenance" value="₹2,000 / mo" />
              <Row
                label="Available from"
                value={formatDate(flat.leaseStartDate) ?? "Immediate"}
              />
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
            <p className="font-display font-semibold">Why renters trust Anirudh Homes</p>
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

function Row({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <div className="flex justify-between gap-2">
      <span className="text-muted-foreground">
        {label}
        {hint && (
          <span className="block text-[10px] text-muted-foreground/70 mt-0.5">
            {hint}
          </span>
        )}
      </span>
      <span className="font-medium whitespace-nowrap">{value}</span>
    </div>
  );
}
