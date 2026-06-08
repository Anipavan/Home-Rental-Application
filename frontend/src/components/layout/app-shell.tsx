import { useEffect } from "react";
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { claimsApi } from "@/lib/api/claims";
import { Hourglass } from "lucide-react";
import {
  Home,
  Building2,
  Users,
  Receipt,
  Wrench,
  BarChart3,
  Settings,
  LogOut,
  CreditCard,
  ShieldCheck,
  LayoutGrid,
  ScrollText,
  FileText,
  Star,
  BadgeCheck,
  Stamp,
  Calendar,
  Inbox,
  Megaphone,
  MessageSquareWarning,
  Search,
  Heart,
  BellRing,
  Server,
  HandCoins,
} from "lucide-react";
import { Logo } from "./logo";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { IdleTimer } from "@/components/auth/idle-timer";
import { NotificationBell } from "./notification-bell";
import { ContactSupport } from "./contact-support";
import { GlobalSearch } from "./global-search";
import { PageEnter } from "@/components/ui/page-enter";
import {
  OnboardingBanner,
  OnboardingWizard,
} from "@/components/onboarding/onboarding-wizard";
import { useAuthStore } from "@/stores/auth-store";
import { authApi } from "@/lib/api/auth";
import { usersApi } from "@/lib/api/users";
import {
  isAlertsDisabled,
  isComplianceDisabled,
  isKycDisabled,
} from "@/lib/feature-flags";
import { cn, initials, normalizeDocUrl } from "@/lib/utils";
import type { Role } from "@/types/api";

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  /**
   * Optional small pill rendered next to the label — used to flag
   * features that are temporarily disabled so the user knows before
   * they click. The route itself still resolves; the gate (see
   * {@link FeatureDisabledOutlet}) decides whether the page renders.
   */
  pausedBadge?: boolean;
}

const tenantNav: NavItem[] = [
  { to: "/app", label: "Overview", icon: Home },
  { to: "/app/my-flat", label: "My Home", icon: Building2 },
  // Wishlist of homes the tenant has hearted. Sits high in the nav
  // because it's a frequent re-entry point — users come back to
  // re-open the same shortlist multiple times during their search.
  { to: "/app/saved", label: "Saved", icon: Heart },
  // Saved-search alerts (email me when a new home matches). Lives
  // right under "Saved" because the user mental model is identical —
  // both surfaces are "things I want the platform to remember".
  // Alerts pill mirrors the ALERTS_DISABLED feature flag. Same
  // single-source-of-truth pattern as KYC below — flipping the flag
  // back to false removes the pill automatically.
  { to: "/app/saved-searches", label: "Alerts", icon: BellRing, pausedBadge: isAlertsDisabled() },
  // Always visible — even after a flat is assigned, tenants should be
  // able to look at other listings (planning ahead, longer lease,
  // recommending to a friend, etc.). The route lives outside the
  // FlatRequiredOutlet gate so it works on day one too.
  { to: "/app/browse", label: "Browse Homes", icon: Search },
  { to: "/app/lease", label: "Lease", icon: ScrollText },
  { to: "/app/payments", label: "Payments", icon: Receipt },
  { to: "/app/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/app/complaints", label: "Complaints", icon: MessageSquareWarning },
  // KYC pill mirrors the feature flag — single source of truth in
  // lib/feature-flags.ts. Flipping that flag back to false removes
  // the pill automatically, no further code change.
  { to: "/app/kyc", label: "KYC", icon: BadgeCheck, pausedBadge: isKycDisabled() },
  { to: "/app/documents", label: "Documents", icon: FileText },
  { to: "/app/reviews", label: "Reviews", icon: Star },
  // Society — read-only common-area maintenance ledger for the
  // tenant's building. Empty state when the owner hasn't enabled it
  // yet, otherwise shows the monthly expense breakdown the entire
  // residents-group can see for transparency.
  { to: "/app/society", label: "Society", icon: HandCoins },
  { to: "/app/profile", label: "Profile", icon: Settings },
];

const ownerNav: NavItem[] = [
  { to: "/owner", label: "Overview", icon: Home },
  { to: "/owner/buildings", label: "Buildings", icon: Building2 },
  { to: "/owner/flats", label: "Flats", icon: LayoutGrid },
  { to: "/owner/tenants", label: "Tenants", icon: Users },
  { to: "/owner/payments", label: "Payments", icon: Receipt },
  { to: "/owner/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/owner/complaints", label: "Complaints", icon: MessageSquareWarning },
  { to: "/owner/agreements", label: "Agreements", icon: ScrollText },
  { to: "/owner/leases", label: "Leases", icon: FileText },
  { to: "/owner/documents", label: "Documents", icon: FileText },
  { to: "/owner/enquiries", label: "Enquiries", icon: Inbox },
  // KYC pill mirrors the same feature flag the tenant /app/kyc nav
  // uses — single source of truth in lib/feature-flags.ts. Owners
  // need PAN verification too (powers the 'Verified owner' badge
  // on their listings + unblocks compliance flows like RERA filings).
  { to: "/owner/kyc", label: "KYC", icon: BadgeCheck, pausedBadge: isKycDisabled() },
  // Compliance pill mirrors the COMPLIANCE_DISABLED feature flag.
  { to: "/owner/compliance", label: "Compliance", icon: Stamp, pausedBadge: isComplianceDisabled() },
  { to: "/owner/analytics", label: "Analytics", icon: BarChart3 },
  // Society — common-area maintenance ledger overview. Lists every
  // building the owner owns, with quick-setup or open-ledger CTA.
  { to: "/owner/society", label: "Society", icon: HandCoins },
  { to: "/owner/profile", label: "Profile", icon: Settings },
];

const adminNav: NavItem[] = [
  { to: "/admin", label: "Overview", icon: ShieldCheck },
  { to: "/admin/users", label: "Users", icon: Users },
  { to: "/admin/properties", label: "Properties", icon: Building2 },
  { to: "/admin/payments", label: "Payments", icon: Receipt },
  { to: "/admin/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/admin/complaints", label: "Complaints", icon: MessageSquareWarning },
  { to: "/admin/reviews", label: "Reviews", icon: Star },
  { to: "/admin/support", label: "Support", icon: FileText },
  { to: "/admin/visit-requests", label: "Visit requests", icon: Calendar },
  // Issue #9 — admin announcement broadcast composer.
  { to: "/admin/announcements", label: "Announcements", icon: Megaphone },
  // Per-vendor API usage + billing alerts. Admin-only — server enforces
  // the role, nav entry is admin-scoped so non-admins never see the link.
  { to: "/admin/vendor-usage", label: "Vendor usage", icon: Server },
  { to: "/admin/profile", label: "Profile", icon: Settings },
];

/** Slimmed sidebar for MAINTAINER users — they only need their per-
 *  flat dashboard + the expense ledger + Profile. No buildings / flats
 *  / tenants list (those are owner-scoped). Owners who self-assigned
 *  as the maintainer for one of their own buildings keep the full
 *  OWNER nav since they wear both hats. */
const maintainerNav: NavItem[] = [
  { to: "/maintainer", label: "Flats & dues", icon: HandCoins },
  { to: "/maintainer/profile", label: "Profile", icon: Settings },
];

function navFor(role: Role | null): NavItem[] {
  if (role === "OWNER") return ownerNav;
  if (role === "MAINTAINER") return maintainerNav;
  if (role === "ADMIN") return adminNav;
  return tenantNav;
}

export function AppShell() {
  const { role, userName, authUserId, refreshToken, clear } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  // Pending-claim gate. Users who registered via the SOCIETY path
  // sit at role=TENANT with a PENDING MAINTAINER claim until the
  // building owner approves. While they're in that limbo state we
  // hide the full tenant navigation (Saved, Browse, Lease, etc.)
  // and pin them to /app/pending-claim — they shouldn't be able to
  // wander into tenant features they don't actually have access to.
  //
  // Once the owner approves and they sign back in with role=MAINTAINER,
  // the ProtectedRoute guard on /app already kicks them out (MAINTAINER
  // is not in the route's allowed roles), so the post-approval routing
  // is handled higher up.
  const myClaimsQ = useQuery({
    queryKey: ["my-claims"],
    queryFn: () => claimsApi.mine(),
    enabled: !!authUserId,
    refetchInterval: 30_000,
    staleTime: 15_000,
    // 404 means the endpoint isn't there or the user has zero claims —
    // either way, no gating to apply.
    retry: false,
  });
  const hasPendingClaim = (myClaimsQ.data ?? []).some(
    (c) => c.status === "PENDING",
  );

  // While pending, force every /app/* request back to the role-agnostic
  // /pending-claim page. The redirect runs inside an effect so we don't
  // fight React's strict-mode double-render — the navigate call is
  // idempotent once we're on the pending-claim route.
  useEffect(() => {
    if (hasPendingClaim && location.pathname !== "/pending-claim") {
      navigate("/pending-claim", { replace: true });
    }
  }, [hasPendingClaim, location.pathname, navigate]);

  // Sidebar items: full tenant nav normally, single "Application" entry
  // while pending so the user can't try to click into things they
  // can't reach yet.
  const items: NavItem[] = hasPendingClaim
    ? [{ to: "/pending-claim", label: "Your application", icon: Hourglass }]
    : navFor(role);

  // Fetch the signed-in user's profile so the header avatar can render
  // their uploaded photo (Instagram / WhatsApp style — name next to a
  // round picture, not just initials). Shares the same ["me", authUserId]
  // cache key the Profile page uses, so as soon as the user uploads a
  // new photo there and that page invalidates the query, this avatar
  // re-renders too without any extra wiring.
  const meQ = useQuery({
    queryKey: ["me", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId,
    staleTime: 60_000,
    // 404 means the user-service profile hasn't been bootstrapped yet —
    // we fall back to initials, no retry needed.
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 404 && failureCount < 1;
    },
  });
  // Normalise — strips any legacy "http://localhost:8080" prefix that
  // older profile rows still embed from before commit 78021a7 (Issue
  // #1). Anything already relative or pointing to a non-localhost
  // host passes through unchanged.
  const photoUrl = normalizeDocUrl(meQ.data?.profilePictureUrl);

  const onLogout = async () => {
    try {
      if (refreshToken) await authApi.logout(refreshToken).catch(() => {});
    } finally {
      clear();
      navigate("/login");
    }
  };

  return (
    <div className="flex min-h-screen bg-secondary/30">
      {/*
        Powers the 30-min idle logout + access-token-expiry banner. Runs once
        per AppShell mount; only mounted for authenticated routes (this shell
        is wrapped in <ProtectedRoute>), so we don't need to check auth here.
      */}
      <IdleTimer />
      <aside className="hidden lg:flex w-64 shrink-0 flex-col border-r border-border/60 bg-background">
        <div className="h-16 px-5 flex items-center border-b border-border/60">
          <Logo />
        </div>
        <nav className="flex-1 p-3 space-y-0.5">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/app" || item.to === "/owner" || item.to === "/admin"}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary/10 text-primary"
                    : "text-muted-foreground hover:bg-secondary hover:text-foreground",
                )
              }
            >
              <item.icon className="size-4" />
              <span className="flex-1">{item.label}</span>
              {item.pausedBadge && (
                <span className="rounded-full bg-warning/15 text-warning text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5">
                  Paused
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="p-3 border-t border-border/60">
          <div className="rounded-xl gradient-brand-soft p-4 border border-primary/15">
            <p className="text-xs font-semibold text-foreground">
              Need help?
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              Our team is online 9am – 9pm IST.
            </p>
            <ContactSupport className="mt-3 w-full" />
          </div>
          {/* Attribution watermark — kept very small (10px) and muted
              so it sits quietly at the bottom of the desktop sidebar
              without competing with the nav. Mirrors the credit in
              PublicFooter so logged-in users see it too. */}
          <p className="mt-3 px-1 text-[10px] text-muted-foreground/60 text-center">
            Crafted by{" "}
            <span className="font-semibold text-muted-foreground/80">
              Siva Pawan Anirudh
            </span>
          </p>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="sticky top-0 z-30 h-16 border-b border-border/60 bg-background/85 backdrop-blur-xl">
          <div className="h-full px-4 sm:px-6 flex items-center justify-between gap-4">
            <div className="flex items-center gap-3 flex-1 max-w-md">
              <div className="w-full hidden sm:block">
                <GlobalSearch />
              </div>
            </div>
            <div className="flex items-center gap-2">
              {role === "TENANT" && (
                <Button asChild variant="gradient" size="sm" className="hidden sm:inline-flex">
                  <Link to="/app/payments">
                    <CreditCard /> Pay Rent
                  </Link>
                </Button>
              )}
              <NotificationBell />
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className="flex items-center gap-2 rounded-full hover:bg-secondary p-1 pr-3 transition-colors">
                    <Avatar className="size-8">
                      {/* AvatarImage falls back to AvatarFallback if the
                          src is empty, fails to load, or returns 4xx —
                          so a broken pre-signed URL (expired, deleted
                          on disk, etc.) gracefully shows initials. */}
                      {photoUrl && <AvatarImage src={photoUrl} alt={userName ?? "Profile photo"} />}
                      <AvatarFallback>{initials(userName ?? "")}</AvatarFallback>
                    </Avatar>
                    <span className="text-sm font-medium hidden sm:block">
                      {userName ?? "User"}
                    </span>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>{userName ?? "Account"}</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link
                      to={
                        role === "OWNER"
                          ? "/owner/profile"
                          : role === "ADMIN"
                            ? "/admin/profile"
                            : "/app/profile"
                      }
                    >
                      <Settings /> Profile
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={onLogout}>
                    <LogOut /> Sign out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        </header>

        <main className="flex-1 px-4 sm:px-6 lg:px-8 py-6 lg:py-8">
          {/* Persistent "Continue setup" banner. Self-hides when the
              user has finished all steps (or is an admin, who has no
              onboarding flow). Lives outside PageEnter so it doesn't
              fade in and out with every navigation — it just stays
              put as a stable reference until the user is done. */}
          <OnboardingBanner />
          {/* One-time fade + slide-up per route change. Productivity
              surfaces need to feel fast, so this is deliberately the
              LIGHT motion (0.4s ease-out / 8px travel) — not the
              full reveal-up vocabulary the marketing pages use. */}
          <PageEnter>
            <Outlet />
          </PageEnter>
        </main>

        {/* First-login onboarding modal. Auto-opens ONCE per user on
            the first authenticated mount; dismissal is persisted in
            localStorage so the modal never re-pops by itself. The
            banner above keeps surfacing remaining steps regardless. */}
        <OnboardingWizard />

        <nav className="lg:hidden border-t border-border/60 bg-background/95 backdrop-blur sticky bottom-0 z-30">
          <div className="grid grid-cols-5">
            {items.slice(0, 5).map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/app" || item.to === "/owner" || item.to === "/admin"}
                className={({ isActive }) =>
                  cn(
                    "flex flex-col items-center justify-center gap-1 py-2.5 text-[11px]",
                    isActive
                      ? "text-primary"
                      : "text-muted-foreground hover:text-foreground",
                  )
                }
              >
                <item.icon className="size-5" />
                {item.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </div>
    </div>
  );
}
