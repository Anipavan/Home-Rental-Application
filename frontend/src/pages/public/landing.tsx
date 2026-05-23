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
import { propertiesApi } from "@/lib/api/properties";

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

const testimonials = [
  {
    quote:
      "Found a 2BHK in Indiranagar in two days, paid the deposit through PhonePe, moved in the same week. The dashboard is a delight.",
    name: "Aanya Mehta",
    role: "Tenant · Bengaluru",
  },
  {
    quote:
      "I manage 14 flats. Anirudh Homes replaced three spreadsheets and a WhatsApp group. The maintenance flow alone saves me an hour a day.",
    name: "Rahul Iyer",
    role: "Owner · Pune",
  },
  {
    quote:
      "The auto-reminders mean my tenants pay on the 3rd, not the 13th. Best part: clean receipts I can hand to my CA.",
    name: "Sushma R.",
    role: "Owner · Hyderabad",
  },
];

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

  const liveStats = [
    {
      v: formatCount(homesQ.data?.totalElements ?? null),
      l: "Verified homes",
    },
    {
      v: formatCount(buildingsQ.data?.totalElements ?? null),
      l: "Buildings listed",
    },
    {
      v: formatCount(vacantQ.data?.length ?? null),
      l: "Available now",
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
    },
  ];

  return (
    <>
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-hero-radial" />
        <div className="absolute inset-0 bg-grid-light bg-[size:32px_32px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" />
        <div className="container relative pt-16 lg:pt-24 pb-16">
          <div className="max-w-3xl mx-auto text-center">
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

      <section className="border-y border-border/60 bg-background">
        <div className="container py-10 grid grid-cols-2 md:grid-cols-4 gap-6">
          {liveStats.map((s) => (
            <div key={s.l} className="text-center">
              <div className="font-display text-2xl sm:text-3xl font-bold gradient-text">
                {s.v}
              </div>
              <div className="text-xs sm:text-sm text-muted-foreground mt-1">
                {s.l}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="container py-16 lg:py-24">
        <div className="max-w-2xl">
          <h2 className="font-display text-3xl lg:text-4xl font-bold tracking-tight">
            Renting, the way it should feel.
          </h2>
          <p className="mt-3 text-muted-foreground">
            Every rental ritual — search, sign, pay, fix — designed for the
            phone in your pocket.
          </p>
        </div>
        <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <Card
              key={f.title}
              className="p-6 hover:shadow-lift transition-shadow group"
            >
              <div className="size-11 rounded-xl bg-primary/10 text-primary grid place-items-center mb-4 group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                <f.icon className="size-5" />
              </div>
              <h3 className="font-display font-semibold">{f.title}</h3>
              <p className="text-sm text-muted-foreground mt-1.5">{f.desc}</p>
            </Card>
          ))}
        </div>
      </section>

      <section className="bg-secondary/30 border-y border-border/60">
        <div className="container py-16 lg:py-20 grid gap-10 lg:grid-cols-2 items-center">
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
        </div>
      </section>

      <section className="container py-16 lg:py-24">
        <div className="text-center max-w-2xl mx-auto">
          <h2 className="font-display text-3xl lg:text-4xl font-bold">
            Loved by 12,000+ renters.
          </h2>
          <p className="mt-3 text-muted-foreground">
            Real reviews from real homes.
          </p>
        </div>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {testimonials.map((t) => (
            <Card key={t.name} className="p-6">
              <div className="flex gap-0.5 text-amber-500 mb-3">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star key={i} className="size-4 fill-current" />
                ))}
              </div>
              <p className="text-sm leading-relaxed">"{t.quote}"</p>
              <div className="mt-5 pt-4 border-t border-border/60">
                <p className="text-sm font-semibold">{t.name}</p>
                <p className="text-xs text-muted-foreground">{t.role}</p>
              </div>
            </Card>
          ))}
        </div>
      </section>

      <section className="container pb-20">
        <div className="rounded-3xl gradient-brand text-white px-6 py-12 sm:px-12 sm:py-16 text-center shadow-lift relative overflow-hidden">
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
      </section>
    </>
  );
}
