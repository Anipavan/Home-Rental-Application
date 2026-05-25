import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  ShieldCheck,
  Wallet,
  Heart,
  Sparkles,
  Building2,
  Globe2,
  Users,
  Zap,
  CheckCircle2,
  Lock,
  IndianRupee,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Reveal } from "@/components/ui/reveal";
import { propertiesApi } from "@/lib/api/properties";

/* ────────────────────────────────────────────────────────────────────
 * About — the brand page.
 *
 * Design intent (Apple-style, taken in small doses):
 *   • One idea per scroll-section, generous whitespace, big typography.
 *   • Motion is reserved — a single fade-up reveal triggered by an
 *     IntersectionObserver, plus two slow-drifting ambient orbs in the
 *     hero. Nothing spins. Nothing parallaxes. Nothing demands attention.
 *   • Brand colours are anchored on the existing gradient-brand
 *     (emerald → teal → sky) so this page reads as a continuation of
 *     the landing, not a different product.
 *   • Live stats pull from the same endpoints the landing uses so the
 *     numbers are honest, not aspirational.
 *
 * Sections (top → bottom):
 *   1. Hero            — brand statement + ambient drift
 *   2. Story           — three short paragraphs about why we built this
 *   3. Live numbers    — connected to real data
 *   4. Differentiators — how we differ from NoBroker / 99acres / etc.
 *   5. Pillars         — verified, transparent, India-first
 *   6. Closing CTA     — get started / list property
 * ──────────────────────────────────────────────────────────────────── */

export function AboutPage() {
  return (
    <div className="overflow-x-clip">
      <Hero />
      <Story />
      <LiveNumbers />
      <Differentiators />
      <Pillars />
      <ClosingCTA />
    </div>
  );
}

/* ─────────────────────────── Hero ─────────────────────────── */

function Hero() {
  return (
    <section className="relative overflow-hidden">
      {/* Two ambient gradient orbs — slow drift, generous blur. They sit
          behind everything via z-index and never touch the content. The
          `pointer-events-none` keeps them from intercepting clicks even
          on the very edges of the hero. */}
      <div
        aria-hidden
        className="pointer-events-none absolute -top-32 -left-24 size-[480px] rounded-full bg-gradient-to-br from-emerald-400/30 via-teal-400/20 to-transparent blur-3xl animate-ambient-drift-slow"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -top-10 right-[-10%] size-[560px] rounded-full bg-gradient-to-br from-sky-400/25 via-teal-300/15 to-transparent blur-3xl animate-ambient-drift-slower"
      />
      {/* Subtle dot grid mask — same texture the landing hero uses, so
          the two pages feel like one brand surface. */}
      <div className="absolute inset-0 bg-grid-light bg-[size:32px_32px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_75%)]" />

      <div className="container relative pt-24 lg:pt-32 pb-20 lg:pb-28">
        <div className="max-w-3xl mx-auto text-center animate-fade-in">
          <Badge className="mb-6 px-3 py-1 text-xs">
            <Sparkles className="size-3" /> Built in India · for India
          </Badge>
          <h1 className="font-display font-bold tracking-tight text-4xl sm:text-5xl lg:text-6xl leading-[1.05]">
            Renting a home should feel{" "}
            <span className="gradient-text">simple, safe, and human.</span>
          </h1>
          <p className="mt-6 text-lg sm:text-xl text-muted-foreground leading-relaxed">
            Anirudh Homes is a calm, modern rental platform — verified owners,
            instant UPI payments, GST receipts, real humans on support. No
            brokers. No surprise fees. No paperwork chaos.
          </p>
          <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
            <Button asChild variant="gradient" size="lg">
              <Link to="/browse">
                Browse homes <ArrowRight />
              </Link>
            </Button>
            <Button asChild variant="outline" size="lg">
              <Link to="/register">List your property</Link>
            </Button>
          </div>
          <p className="mt-7 text-xs text-muted-foreground flex items-center justify-center gap-2">
            <Lock className="size-3" /> 256-bit TLS · PCI-DSS gateways ·
            DigiLocker-grade KYC
          </p>
        </div>
      </div>
    </section>
  );
}

/* ─────────────────────────── Story ─────────────────────────── */

function Story() {
  return (
    <section className="container py-20 lg:py-28">
      <div className="max-w-3xl mx-auto space-y-12">
        <Reveal>
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-primary font-semibold mb-3">
              Our story
            </p>
            <h2 className="font-display text-3xl sm:text-4xl font-bold leading-tight">
              We grew up renting. We knew it could be calmer than this.
            </h2>
          </div>
        </Reveal>

        <Reveal delay={120}>
          <p className="text-lg text-muted-foreground leading-relaxed">
            Every Indian renter knows the routine. Six brokers. Twelve
            visits. A month's rent gone before the first rent's even paid.
            Lease agreements printed at the photocopier on the corner.
            Maintenance calls that bounce between WhatsApp groups for weeks.
          </p>
        </Reveal>

        <Reveal delay={200}>
          <p className="text-lg text-muted-foreground leading-relaxed">
            We started Anirudh Homes because the basic plumbing of renting
            in India — finding a place, signing a lease, paying rent on
            time, getting a leaky tap fixed — deserves the same care that
            ride-hailing and food delivery got a decade ago. Less friction,
            more trust, and a paper trail you can actually find when you
            need it.
          </p>
        </Reveal>

        <Reveal delay={280}>
          <div className="rounded-2xl border bg-gradient-brand-soft p-6 sm:p-8">
            <Heart className="size-6 text-primary mb-3" />
            <p className="text-lg sm:text-xl font-display font-semibold leading-snug">
              "Built so a tenant in Pune, an owner in Bengaluru, and a
              maintenance technician in Hyderabad can finish one rent
              cycle without ever needing to phone each other."
            </p>
            <p className="mt-3 text-sm text-muted-foreground">
              — The principle we hold every release to.
            </p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

/* ─────────────────────────── Live numbers ─────────────────────────── */

function LiveNumbers() {
  // Same shape the landing uses. Wrap each in retry:false so a single
  // service hiccup just shows "—" for that stat instead of throwing.
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
  const citySampleQ = useQuery({
    queryKey: ["marketing-stats", "city-sample"],
    queryFn: () => propertiesApi.buildings.list(0, 200),
    staleTime: 5 * 60_000,
    retry: false,
  });

  const stats = [
    {
      icon: Building2,
      value: formatCount(homesQ.data?.totalElements),
      label: "Verified homes",
      sub: "Listed directly by owners",
    },
    {
      icon: Globe2,
      value: formatCount(
        citySampleQ.data
          ? new Set(
              citySampleQ.data.content
                .map((b) => b.buildingCity?.trim().toLowerCase())
                .filter(Boolean),
            ).size
          : null,
      ),
      label: "Cities covered",
      sub: "And growing every week",
    },
    {
      icon: Users,
      value: formatCount(buildingsQ.data?.totalElements),
      label: "Buildings on platform",
      sub: "From owners who trust us",
    },
    {
      icon: Sparkles,
      value: formatCount(vacantQ.data?.length),
      label: "Available right now",
      sub: "Move-in ready listings",
    },
  ];

  return (
    <section className="relative">
      <div className="absolute inset-0 bg-gradient-brand-soft" />
      <div className="container relative py-20 lg:py-24">
        <Reveal>
          <div className="text-center max-w-2xl mx-auto mb-14">
            <p className="text-xs uppercase tracking-[0.2em] text-primary font-semibold mb-3">
              By the numbers
            </p>
            <h2 className="font-display text-3xl sm:text-4xl font-bold">
              Honest counters. No vanity metrics.
            </h2>
            <p className="mt-3 text-muted-foreground">
              Every number on this page is queried live from the platform,
              right now. If it's small, it's because we're young.
            </p>
          </div>
        </Reveal>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
          {stats.map((s, i) => (
            <Reveal key={s.label} delay={i * 80}>
              <Card className="p-6 h-full bg-card/80 backdrop-blur-sm border-border/60 hover:border-primary/40 hover:shadow-lift transition-all duration-300">
                <s.icon className="size-6 text-primary mb-3" />
                <p className="font-display font-bold text-3xl sm:text-4xl">
                  {s.value}
                </p>
                <p className="text-sm font-medium mt-1.5">{s.label}</p>
                <p className="text-xs text-muted-foreground mt-1">{s.sub}</p>
              </Card>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────────────── Differentiators ─────────────────────────── */

const DIFFERENTIATORS: Array<{
  icon: typeof ShieldCheck;
  title: string;
  desc: string;
  vs: string;
}> = [
  {
    icon: ShieldCheck,
    title: "Zero brokerage. Always.",
    desc: "You talk to the owner directly. We don't sell leads, we don't gate listings behind paywalls, we don't take a month's rent to introduce you.",
    vs: "vs ₹15,000-50,000 in broker fees on most platforms",
  },
  {
    icon: Wallet,
    title: "Rent in one tap.",
    desc: "Pay via PhonePe, Google Pay, Paytm, any UPI app, or card — straight from the rent due page. GST invoice arrives in your inbox in seconds.",
    vs: "vs cash, NEFT, and chasing receipts every month",
  },
  {
    icon: Zap,
    title: "Maintenance, built in.",
    desc: "Raise a ticket. Owner sees it instantly. Technician gets dispatched. Photos, status updates, and resolution all in one thread.",
    vs: "vs WhatsApp groups that go silent on Sunday",
  },
  {
    icon: CheckCircle2,
    title: "Lease that holds up in court.",
    desc: "RERA-stamped digital lease, e-signed by both sides, archived forever. Renewals and vacate notices baked into the flow — not a PDF you'll lose.",
    vs: "vs ₹500 photocopier-printed agreements",
  },
];

function Differentiators() {
  return (
    <section className="container py-20 lg:py-28">
      <Reveal>
        <div className="text-center max-w-2xl mx-auto mb-12">
          <p className="text-xs uppercase tracking-[0.2em] text-primary font-semibold mb-3">
            What makes us different
          </p>
          <h2 className="font-display text-3xl sm:text-4xl font-bold leading-tight">
            We're not another listing site.
          </h2>
          <p className="mt-3 text-muted-foreground">
            Owners and tenants need a tool that handles the whole rental
            relationship — not just the first hello.
          </p>
        </div>
      </Reveal>
      <div className="grid md:grid-cols-2 gap-5">
        {DIFFERENTIATORS.map((d, i) => (
          <Reveal key={d.title} delay={i * 90}>
            <Card className="p-7 h-full hover:shadow-lift hover:-translate-y-0.5 hover:border-primary/40 transition-all duration-300">
              <div className="size-11 rounded-xl gradient-brand grid place-items-center mb-4 shadow-soft">
                <d.icon className="size-5 text-white" />
              </div>
              <h3 className="font-display font-semibold text-xl mb-2">
                {d.title}
              </h3>
              <p className="text-muted-foreground leading-relaxed">
                {d.desc}
              </p>
              <p className="mt-4 text-xs uppercase tracking-wider text-primary font-semibold">
                {d.vs}
              </p>
            </Card>
          </Reveal>
        ))}
      </div>
    </section>
  );
}

/* ─────────────────────────── Pillars ─────────────────────────── */

const PILLARS = [
  {
    icon: ShieldCheck,
    title: "Verified",
    body: "Every owner KYC'd via PAN + DigiLocker before their first listing goes live. Every flat has owner-uploaded photos — not stock images.",
  },
  {
    icon: Globe2,
    title: "Transparent",
    body: "What you see is what you pay. Rent, deposit, late fee, GST — itemised on every invoice. No platform fee bolted on at checkout.",
  },
  {
    icon: IndianRupee,
    title: "India-first",
    body: "₹ formatting that respects the lakh system. Built for Indian banks (HDFC, ICICI, SBI), Indian payment rails (UPI, NEFT, IMPS), and Indian leases (RERA, GST).",
  },
];

function Pillars() {
  return (
    <section className="bg-secondary/30 border-y">
      <div className="container py-20 lg:py-24">
        <Reveal>
          <div className="max-w-2xl mb-12">
            <p className="text-xs uppercase tracking-[0.2em] text-primary font-semibold mb-3">
              Our pillars
            </p>
            <h2 className="font-display text-3xl sm:text-4xl font-bold leading-tight">
              Three things we won't compromise on.
            </h2>
          </div>
        </Reveal>
        <div className="grid md:grid-cols-3 gap-5">
          {PILLARS.map((p, i) => (
            <Reveal key={p.title} delay={i * 100}>
              <div className="relative p-7 rounded-2xl bg-card border h-full">
                <div className="absolute top-0 left-7 -translate-y-1/2 size-12 rounded-2xl gradient-brand grid place-items-center shadow-lift">
                  <p.icon className="size-5 text-white" />
                </div>
                <h3 className="font-display font-semibold text-2xl mt-6 mb-3">
                  {p.title}
                </h3>
                <p className="text-muted-foreground leading-relaxed">
                  {p.body}
                </p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────────────── Closing CTA ─────────────────────────── */

function ClosingCTA() {
  return (
    <section className="container py-24 lg:py-32">
      <Reveal>
        <div className="relative overflow-hidden rounded-3xl gradient-brand text-white p-10 sm:p-14 text-center">
          {/* One more ambient blob in the CTA. Keeps the motion vocabulary
              consistent with the hero — book-ending the page. */}
          <div
            aria-hidden
            className="pointer-events-none absolute -top-20 -right-20 size-80 rounded-full bg-white/15 blur-3xl animate-ambient-drift-slower"
          />
          <div className="relative max-w-2xl mx-auto">
            <h2 className="font-display font-bold text-3xl sm:text-4xl lg:text-5xl leading-tight">
              Move in. Get paid. Or both.
            </h2>
            <p className="mt-4 text-base sm:text-lg text-white/85">
              Anirudh Homes is free for tenants. Free for owners until you
              collect your first month's rent. Try it for a cycle — if it
              feels heavier than what you're doing today, walk away. No
              lock-in, no contract.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <Button asChild variant="secondary" size="lg">
                <Link to="/browse">
                  Find a home <ArrowRight />
                </Link>
              </Button>
              <Button
                asChild
                size="lg"
                className="bg-white/10 hover:bg-white/20 border border-white/30 text-white"
              >
                <Link to="/register">List a property</Link>
              </Button>
            </div>
            <p className="mt-8 text-xs text-white/70">
              Questions? Reach us at{" "}
              <a
                href="mailto:hello@anirudhhomes.in"
                className="underline hover:text-white"
              >
                hello@anirudhhomes.in
              </a>{" "}
              or call{" "}
              <a href="tel:+919108201223" className="underline hover:text-white">
                +91&nbsp;91082&nbsp;01223
              </a>
              .
            </p>
          </div>
        </div>
      </Reveal>
    </section>
  );
}

/* ─────────────────────────── helpers ─────────────────────────── */

/**
 * Same count formatter the landing page uses — kept inline so the
 * About page doesn't import private helpers from another route. Renders
 * 1234 → "1,234", 12345 → "12.3K", null/zero → "—".
 */
function formatCount(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n) || n <= 0) return "—";
  if (n < 1000) return String(n);
  if (n < 1_000_000) {
    const k = n / 1000;
    return `${k % 1 === 0 ? k.toFixed(0) : k.toFixed(1)}K`;
  }
  const m = n / 1_000_000;
  return `${m % 1 === 0 ? m.toFixed(0) : m.toFixed(1)}M`;
}
