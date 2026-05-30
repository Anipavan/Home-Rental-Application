import { Link } from "react-router-dom";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Search,
  ShieldCheck,
  Wallet,
  Wrench,
  Sparkles,
  Building2,
  ArrowRight,
  Star,
  MapPin,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Reveal } from "@/components/ui/reveal";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { reviewsApi } from "@/lib/api/reviews";
import { authApi } from "@/lib/api/auth";
import { formatINR, initials as nameInitials } from "@/lib/utils";

const cities = ["Bengaluru", "Mumbai", "Delhi NCR", "Hyderabad", "Chennai", "Pune"];

const features = [
  {
    icon: ShieldCheck,
    title: "Zero brokerage",
    desc: "Talk directly to verified owners. No middlemen. No surprise fees.",
  },
  {
    icon: Wallet,
    title: "One-tap rent",
    desc: "Pay via UPI, PhonePe, GPay, or card. Auto-receipts, every time.",
  },
  {
    icon: Wrench,
    title: "Maintenance, sorted",
    desc: "Raise a request, track it live, chat with technicians.",
  },
  {
    icon: Sparkles,
    title: "Smart matches",
    desc: "Tell us what you need — we surface homes that fit your life.",
  },
];

/**
 * Pretty-print a count: 1234 → "1,234", 12345 → "12.3K", 1.2M → "1.2M".
 * Used so the landing-page counters look right at any scale: a fresh
 * platform shows raw numbers ("3 homes listed"); at scale we collapse
 * to friendly units. Negative / zero / NaN coerce to "—" so a fetch
 * failure doesn't blow up the rendered card.
 */
function formatCount(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n) || n < 0) return "—";
  if (n === 0) return "0";
  if (n < 1_000) return n.toLocaleString("en-IN");
  if (n < 100_000) return (n / 1_000).toFixed(1).replace(/\.0$/, "") + "K";
  if (n < 10_000_000) return (n / 100_000).toFixed(1).replace(/\.0$/, "") + "L";
  return (n / 10_000_000).toFixed(1).replace(/\.0$/, "") + "Cr";
}

/* Note: hard-coded testimonials previously lived here as placeholders.
 * Removed 2026-05-28 in favour of pulling REAL approved reviews via
 * reviewsApi.featured() — fabricated "Aanya Mehta · Bengaluru" quotes
 * read as obvious marketing copy and undermined the rest of the
 * page's honest live counters. Now the testimonials section either
 * shows real reviews or hides itself when none exist. */

export function LandingPage() {
  const [city, setCity] = useState("Bengaluru");
  const [budget, setBudget] = useState("any");

  /**
   * Live marketing stats — pulled from existing public endpoints so a
   * fresh-launch platform shows real (low) numbers instead of the old
   * hardcoded "12K+ / ₹4.8 Cr / 98% / 4.8★" placeholders. Each query
   * is independent so a single service blip falls back to "—" without
   * tanking the whole stats strip.
   *
   * What each stat means today (post-wipe, all start at 0):
   *  - homesQ        → count of flats listed (any status)
   *  - buildingsQ    → count of buildings (owners' properties)
   *  - vacantQ       → count of available-now flats (browse target)
   *  - citiesQ       → distinct cities the platform has any building in
   */
  const homesQ = useQuery({
    queryKey: ["marketing-stats", "homes"],
    queryFn: () => propertiesApi.flats.list(0, 1),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const buildingsQ = useQuery({
    queryKey: ["marketing-stats", "buildings"],
    queryFn: () => propertiesApi.buildings.list(0, 1),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const vacantQ = useQuery({
    queryKey: ["marketing-stats", "vacant"],
    queryFn: () => propertiesApi.flats.vacant(),
    staleTime: 5 * 60_000,
    retry: false,
  });
  // Buildings page also gives us a city set on the response. We do
  // need to fetch a wider sample to count distinct cities reliably,
  // so we fetch page 0 size 200 (cheap, deduped by React Query).
  const citySampleQ = useQuery({
    queryKey: ["marketing-stats", "city-sample"],
    queryFn: () => propertiesApi.buildings.list(0, 200),
    staleTime: 5 * 60_000,
    retry: false,
  });
  // Tenant count — public endpoint, returns the AuthUserResponse list
  // for the TENANT role. We only need the length; React Query keeps a
  // single cached copy that the admin /users page reuses.
  const tenantsQ = useQuery({
    queryKey: ["marketing-stats", "tenants"],
    queryFn: () => authApi.byRole("TENANT"),
    staleTime: 5 * 60_000,
    retry: false,
  });
  // Lifetime ₹ processed — the only number that grows with platform
  // activity rather than supply. Whitelisted at the gateway so this
  // works without auth.
  const lifetimeQ = useQuery({
    queryKey: ["marketing-stats", "lifetime-collected"],
    queryFn: () => paymentsApi.publicLifetimeStats(),
    staleTime: 5 * 60_000,
    retry: false,
  });

  // Six tiles total. Each tile carries a `show` flag so we can hide
  // those that resolve to 0 — better to surface "9 verified homes"
  // proudly than "0 verified homes · 0 ₹ processed" embarrassingly.
  const liveStats: Array<{ v: string; l: string; show: boolean }> = [
    {
      v: formatCount(homesQ.data?.totalElements ?? null),
      l: "Verified homes",
      show: (homesQ.data?.totalElements ?? 0) > 0,
    },
    {
      v: formatCount(buildingsQ.data?.totalElements ?? null),
      l: "Buildings listed",
      show: (buildingsQ.data?.totalElements ?? 0) > 0,
    },
    {
      v: formatCount(vacantQ.data?.length ?? null),
      l: "Available now",
      show: (vacantQ.data?.length ?? 0) > 0,
    },
    {
      v: formatCount(
        citySampleQ.data
          ? new Set(
              citySampleQ.data.content
                .map((b) => b.buildingCity?.trim().toLowerCase())
                .filter(Boolean),
            ).size
          : null,
      ),
      l: "Cities covered",
      show: (citySampleQ.data?.content?.length ?? 0) > 0,
    },
    // NEW — tenants on platform.
    {
      v: formatCount(tenantsQ.data?.length ?? null),
      l: "Tenants on platform",
      show: (tenantsQ.data?.length ?? 0) > 0,
    },
    // NEW — lifetime ₹ processed. Uses formatINR for the rupee glyph
    // and the Indian lakh/crore grouping (1,23,45,678 not 12,345,678).
    {
      v:
        lifetimeQ.data?.totalCollectedRupees != null &&
        lifetimeQ.data.totalCollectedRupees > 0
          ? formatINR(lifetimeQ.data.totalCollectedRupees)
          : "—",
      l: "Processed in rent",
      show: (lifetimeQ.data?.totalCollectedRupees ?? 0) > 0,
    },
  ];
  // Only render tiles with usable data. Falls back to "the four
  // supply-side counters" before any rent has been collected.
  const visibleLiveStats = liveStats.filter((s) => s.show);

  // Featured testimonials — APPROVED reviews sorted by rating + recency.
  // Hides the entire section when empty (post-wipe / brand-new day).
  const featuredReviewsQ = useQuery({
    queryKey: ["marketing-featured-reviews"],
    queryFn: () => reviewsApi.featured(3),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const featuredReviews = featuredReviewsQ.data?.content ?? [];

  return (
    <>
      <section className="relative overflow-hidden">
        {/* Two slow-drifting ambient gradient orbs, same vocabulary as
            the About hero so the brand surfaces feel unified. They sit
            behind every other layer via `pointer-events-none` so they
            never intercept the search-card or chip clicks below. */}
        <div
          aria-hidden
          className="pointer-events-none absolute -top-32 -left-24 size-[480px] rounded-full bg-gradient-to-br from-emerald-400/30 via-teal-400/20 to-transparent blur-3xl animate-ambient-drift-slow"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -top-10 right-[-10%] size-[560px] rounded-full bg-gradient-to-br from-sky-400/25 via-teal-300/15 to-transparent blur-3xl animate-ambient-drift-slower"
        />
        <div className="absolute inset-0 bg-hero-radial" />
        <div className="absolute inset-0 bg-grid-light bg-[size:32px_32px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" />
        <div className="container relative pt-16 lg:pt-24 pb-16">
          <div className="max-w-3xl mx-auto text-center animate-fade-in">
            <Badge className="mb-5">
              <Sparkles className="size-3" /> New · Pay rent with PhonePe in one tap
            </Badge>
            <h1 className="font-display text-4xl sm:text-5xl lg:text-6xl font-extrabold leading-[1.05] tracking-tight">
              A home you'll love.
              <br />
              A landlord you'll <span className="gradient-text">trust.</span>
            </h1>
            <p className="mt-5 text-base sm:text-lg text-muted-foreground max-w-xl mx-auto">
              India's calmest rental platform. Verified listings, instant
              payments, real humans on support.
            </p>
          </div>

          <Card className="mt-10 max-w-3xl mx-auto p-3 shadow-lift border-border/40">
            <div className="grid grid-cols-1 sm:grid-cols-[1.2fr_1fr_1fr_auto] gap-2">
              <div className="relative">
                <MapPin className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                <Select value={city} onValueChange={setCity}>
                  <SelectTrigger className="pl-10 h-12 border-transparent bg-secondary/40">
                    <SelectValue placeholder="City" />
                  </SelectTrigger>
                  <SelectContent>
                    {cities.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Select defaultValue="any-bhk">
                <SelectTrigger className="h-12 border-transparent bg-secondary/40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any-bhk">Any BHK</SelectItem>
                  <SelectItem value="1">1 BHK</SelectItem>
                  <SelectItem value="2">2 BHK</SelectItem>
                  <SelectItem value="3">3 BHK</SelectItem>
                  <SelectItem value="4">4+ BHK</SelectItem>
                </SelectContent>
              </Select>
              <Select value={budget} onValueChange={setBudget}>
                <SelectTrigger className="h-12 border-transparent bg-secondary/40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any">Any budget</SelectItem>
                  <SelectItem value="0-15000">Under ₹15K</SelectItem>
                  <SelectItem value="15000-30000">₹15K – 30K</SelectItem>
                  <SelectItem value="30000-60000">₹30K – 60K</SelectItem>
                  <SelectItem value="60000-">₹60K +</SelectItem>
                </SelectContent>
              </Select>
              <Button asChild size="lg" variant="gradient" className="h-12">
                <Link to={`/browse?city=${encodeURIComponent(city)}`}>
                  <Search /> Search
                </Link>
              </Button>
            </div>
          </Card>

          <div className="mt-10 flex flex-wrap items-center justify-center gap-x-2 gap-y-2 text-sm text-muted-foreground">
            <span>Popular:</span>
            {["Indiranagar", "HSR Layout", "Powai", "Koramangala", "Andheri"].map(
              (n) => (
                <Link
                  key={n}
                  to={`/browse?q=${n}`}
                  className="rounded-full bg-secondary/60 hover:bg-secondary px-3 py-1 text-xs font-medium text-foreground/70 hover:text-foreground transition-colors"
                >
                  {n}
                </Link>
              ),
            )}
          </div>
        </div>
      </section>

      {visibleLiveStats.length > 0 && (
        <section className="border-y border-border/60 bg-background">
          {/* grid-cols-3 at small + lg:grid-cols-6 so the strip handles
              between 1 and 6 tiles gracefully. Tiles arrange themselves
              based on how many have real data — no awkward "0" cards. */}
          <div className="container py-10 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-6">
            {visibleLiveStats.map((s, i) => (
              <Reveal key={s.l} delay={i * 70}>
                <div className="text-center">
                  <div className="font-display text-2xl sm:text-3xl font-bold gradient-text">
                    {s.v}
                  </div>
                  <div className="text-xs sm:text-sm text-muted-foreground mt-1">
                    {s.l}
                  </div>
                </div>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      <section className="container py-16 lg:py-24">
        <Reveal>
          <div className="max-w-2xl">
            <h2 className="font-display text-3xl lg:text-4xl font-bold tracking-tight">
              Renting, the way it should feel.
            </h2>
            <p className="mt-3 text-muted-foreground">
              Every rental ritual — search, sign, pay, fix — designed for the
              phone in your pocket.
            </p>
          </div>
        </Reveal>
        <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
          {features.map((f, i) => (
            <Reveal key={f.title} delay={i * 90}>
              <Card className="p-6 hover:shadow-lift hover:-translate-y-0.5 transition-all duration-300 group h-full">
                <div className="size-11 rounded-xl bg-primary/10 text-primary grid place-items-center mb-4 group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                  <f.icon className="size-5" />
                </div>
                <h3 className="font-display font-semibold">{f.title}</h3>
                <p className="text-sm text-muted-foreground mt-1.5">{f.desc}</p>
              </Card>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="bg-secondary/30 border-y border-border/60">
        <div className="container py-16 lg:py-20 grid gap-10 lg:grid-cols-2 items-center">
          <Reveal>
            <div>
              <Badge variant="secondary">For Owners</Badge>
            <h2 className="mt-4 font-display text-3xl lg:text-4xl font-bold">
              Less paperwork. More peace.
            </h2>
            <p className="mt-3 text-muted-foreground max-w-md">
              List in three minutes. Auto-reminders to tenants. Deposit refunds
              tracked end-to-end. A single dashboard for every flat you own.
            </p>
            <ul className="mt-6 space-y-2.5 text-sm">
              {[
                "Tenant verification + digital agreements",
                "Rent collection across UPI / cards / wallets",
                "Real-time occupancy & revenue analytics",
                "Maintenance ticketing with SLA tracking",
              ].map((t) => (
                <li key={t} className="flex items-start gap-2.5">
                  <span className="mt-0.5 size-5 rounded-full bg-success/15 text-success grid place-items-center">
                    <svg viewBox="0 0 20 20" fill="currentColor" className="size-3">
                      <path
                        fillRule="evenodd"
                        d="M16.7 5.3a1 1 0 010 1.4l-7 7a1 1 0 01-1.4 0l-3-3a1 1 0 011.4-1.4L9 11.6l6.3-6.3a1 1 0 011.4 0z"
                        clipRule="evenodd"
                      />
                    </svg>
                  </span>
                  {t}
                </li>
              ))}
            </ul>
            <div className="mt-7 flex gap-3">
              <Button asChild size="lg" variant="gradient">
                <Link to="/register">
                  <Building2 /> List your property
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline">
                <Link to="/about">
                  See pricing <ArrowRight />
                </Link>
              </Button>
            </div>
            </div>
          </Reveal>
          <Reveal delay={150}>
            <div className="relative">
              <div className="absolute -inset-4 bg-gradient-to-br from-primary/30 to-rose-400/20 blur-3xl rounded-3xl -z-10" />
              <div className="rounded-3xl overflow-hidden shadow-lift border border-border/60">
                <img
                  src="https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80"
                  alt="Owner dashboard"
                  className="w-full h-full object-cover"
                />
              </div>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Real testimonials block — pulled live from APPROVED reviews
          sorted by rating + recency. Hidden entirely when no reviews
          exist yet (post-wipe / pre-launch). Hiding > faking. */}
      {featuredReviews.length > 0 && (
        <section className="container py-16 lg:py-24">
          <Reveal>
            <div className="text-center max-w-2xl mx-auto">
              <h2 className="font-display text-3xl lg:text-4xl font-bold">
                What renters are saying.
              </h2>
              <p className="mt-3 text-muted-foreground">
                Real reviews from real tenants on Anirudh Homes.
              </p>
            </div>
          </Reveal>
          <div className="mt-10 grid gap-5 md:grid-cols-3">
            {featuredReviews.map((r, i) => {
              // The reviewer's name isn't on the review row — we only
              // have reviewerId. To keep the card honest without a
              // joining round-trip per review, render initials from
              // the title (a fallback most reviews carry) or "AH" as
              // the brand-mark, then attribute as "Verified renter".
              const initialsSrc =
                r.title?.trim() || (r.reviewerType === "TENANT" ? "Tenant" : "Renter");
              return (
                <Reveal key={r.id} delay={i * 100}>
                  <Card className="p-6 hover:shadow-lift hover:-translate-y-0.5 transition-all duration-300 h-full">
                    <div className="flex gap-0.5 text-amber-500 mb-3">
                      {Array.from({ length: 5 }).map((_, idx) => (
                        <Star
                          key={idx}
                          className={
                            idx < r.rating
                              ? "size-4 fill-current"
                              : "size-4 text-muted-foreground/30"
                          }
                        />
                      ))}
                    </div>
                    {r.title && (
                      <p className="font-semibold text-sm mb-1.5">{r.title}</p>
                    )}
                    <p className="text-sm leading-relaxed text-muted-foreground">
                      "{r.body || "—"}"
                    </p>
                    <div className="mt-5 pt-4 border-t border-border/60 flex items-center gap-3">
                      <div className="size-8 rounded-full bg-primary/15 text-primary grid place-items-center text-xs font-semibold">
                        {nameInitials(initialsSrc)}
                      </div>
                      <div>
                        <p className="text-sm font-semibold">Verified renter</p>
                        <p className="text-xs text-muted-foreground">
                          {r.targetType === "OWNER"
                            ? "Reviewing their landlord"
                            : "Reviewing their stay"}
                        </p>
                      </div>
                    </div>
                  </Card>
                </Reveal>
              );
            })}
          </div>
        </section>
      )}

      <section className="container pb-20">
        <Reveal>
          <div className="rounded-3xl gradient-brand text-white px-6 py-12 sm:px-12 sm:py-16 text-center shadow-lift relative overflow-hidden">
            {/* Match the About CTA's "book-ending" — one more slow
                ambient blob inside the gradient panel, keeping the
                motion vocabulary consistent across landing & about. */}
            <div
              aria-hidden
              className="pointer-events-none absolute -top-20 -right-20 size-80 rounded-full bg-white/15 blur-3xl animate-ambient-drift-slower"
            />
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.18),transparent_50%)]" />
            <div className="relative">
              <h2 className="font-display text-3xl lg:text-4xl font-bold">
                Move in, in 3 days.
              </h2>
              <p className="mt-3 text-white/85 max-w-xl mx-auto">
                Sign up, browse verified homes, sign your agreement, pay rent —
                all in one place.
              </p>
              <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
                <Button
                  asChild
                  size="lg"
                  className="bg-white text-foreground hover:bg-white/90"
                >
                  <Link to="/register">Create your account</Link>
                </Button>
                <Button
                  asChild
                  size="lg"
                  variant="ghost"
                  className="text-white hover:bg-white/15"
                >
                  <Link to="/browse">
                    Browse homes <ArrowRight />
                  </Link>
                </Button>
              </div>
            </div>
          </div>
        </Reveal>
      </section>
    </>
  );
}
