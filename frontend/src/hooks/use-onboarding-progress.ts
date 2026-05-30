import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BadgeCheck,
  Building2,
  Home,
  Sparkles,
  Users,
  Wallet,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { kycApi } from "@/lib/api/kyc";
import { bankAccountsApi } from "@/lib/api/bank-accounts";
import { isKycDisabled } from "@/lib/feature-flags";
import type { Role } from "@/types/api";

/**
 * A single step in the onboarding wizard. Each step is auto-derived
 * from real data (e.g. "have you uploaded a building yet?") — never
 * a manual checkbox. The wizard / banner shows the next incomplete
 * step's CTA as the primary action.
 */
export interface OnboardingStep {
  /** Stable key for React + localStorage dedup. */
  id: string;
  /** Short imperative label — "List your first property". */
  label: string;
  /** One-line explanation under the label. */
  description: string;
  /** True when the user has completed this step. */
  complete: boolean;
  /** SPA route to take the user when they click this step. */
  href: string;
  /** Lucide icon component for the step chip. */
  icon: React.ComponentType<{ className?: string }>;
}

export interface OnboardingProgress {
  role: Role | null;
  steps: OnboardingStep[];
  completeCount: number;
  totalCount: number;
  allComplete: boolean;
  /** True while any underlying query is still loading. */
  isLoading: boolean;
  /** True once the user has dismissed the full-screen modal. The
   *  banner inside the app shell keeps showing until allComplete. */
  modalDismissed: boolean;
  /** Persist a "user has seen the modal" flag in localStorage and
   *  suppress further modal renders for this account. */
  dismissModal: () => void;
}

/**
 * localStorage key — scoped per auth user so two different accounts
 * sharing a browser each get their own first-login modal. The key
 * survives sign-out (we never clear it on logout) so re-logging in
 * doesn't re-trigger the modal a second time on the same browser.
 */
function modalSeenKey(authUserId: string | null | undefined): string {
  return `anirudhhomes.onboarding.modal-seen.${authUserId ?? "anon"}`;
}

/**
 * Returns the user's onboarding progress, auto-detected from the
 * data layer. Renders one of two flows depending on role:
 *
 *  • TENANT: Join a home → Verify identity → Pay first rent → Done
 *  • OWNER:  Verify identity → List first property → Bank details → Assign tenant
 *
 * ADMIN users get an empty wizard (no onboarding flow); the modal
 * + banner self-hide.
 */
export function useOnboardingProgress(): OnboardingProgress {
  const { authUserId, role } = useAuthStore();
  const kycPaused = isKycDisabled();

  /* ── shared queries ──────────────────────────────────────────── */

  // KYC status — used by both roles. Returns null on 404 (the user
  // has never started KYC, which we count as "not verified").
  const kycQ = useQuery({
    queryKey: ["onboarding", "kyc", authUserId],
    queryFn: () => kycApi.status(authUserId!),
    enabled: !!authUserId && !kycPaused,
    staleTime: 60_000,
    retry: false,
  });

  /* ── tenant-only queries ─────────────────────────────────────── */

  const tenantFlatsQ = useQuery({
    queryKey: ["onboarding", "tenant-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId!),
    enabled: !!authUserId && role === "TENANT",
    staleTime: 60_000,
    retry: false,
  });

  const tenantPaymentsQ = useQuery({
    queryKey: ["onboarding", "tenant-payments", authUserId],
    queryFn: () => paymentsApi.byTenant(authUserId!),
    enabled: !!authUserId && role === "TENANT",
    staleTime: 60_000,
    retry: false,
  });

  /* ── owner-only queries ──────────────────────────────────────── */

  // Buildings carry occupiedFlatsCount + vacantFlatsCount on each row,
  // so a single query covers both "do they have a property?" and
  // "do they have any occupied flats?" without a second round-trip
  // to /flats/owner/{id} (an endpoint that doesn't exist on the
  // frontend client today).
  const ownerBuildingsQ = useQuery({
    queryKey: ["onboarding", "owner-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId && role === "OWNER",
    staleTime: 60_000,
    retry: false,
  });

  const bankAccountQ = useQuery({
    queryKey: ["onboarding", "bank-account", authUserId],
    queryFn: () => bankAccountsApi.getByUserId(authUserId!),
    enabled: !!authUserId && role === "OWNER",
    staleTime: 60_000,
    retry: false,
  });

  /* ── build the step list ─────────────────────────────────────── */

  const steps: OnboardingStep[] = useMemo(() => {
    if (!authUserId) return [];

    const kycVerified = kycQ.data?.verificationStatus === "VERIFIED";

    if (role === "TENANT") {
      const hasFlat = (tenantFlatsQ.data?.length ?? 0) > 0;
      const hasPaidRent = (tenantPaymentsQ.data ?? []).some(
        (p) => p.status === "PAID",
      );
      // Tenant flow — three real steps + a celebratory "explore"
      // tile so the wizard finishes on a positive note (you're done!)
      // rather than fading out silently.
      return [
        {
          id: "tenant-join-home",
          label: "Find your home",
          description: hasFlat
            ? "You've been assigned to a flat. Welcome."
            : "Browse verified listings and request a visit when one fits.",
          complete: hasFlat,
          href: hasFlat ? "/app/my-flat" : "/app/browse",
          icon: Home,
        },
        // KYC is optional in the broader app (feature-flag-gated). If
        // it's paused, drop the step entirely so the wizard doesn't
        // perpetually show 2/3.
        ...(kycPaused
          ? []
          : [
              {
                id: "tenant-kyc",
                label: "Verify your identity",
                description: kycVerified
                  ? "Identity confirmed via NSDL."
                  : "Quick PAN check — takes about five seconds.",
                complete: kycVerified,
                href: "/app/kyc",
                icon: BadgeCheck,
              },
            ]),
        {
          id: "tenant-first-rent",
          label: "Pay your first rent",
          description: hasPaidRent
            ? "First rent settled. Receipts live in Payments."
            : "Use PhonePe, Google Pay, any UPI app, or card.",
          complete: hasPaidRent,
          href: "/app/payments",
          icon: Wallet,
        },
      ];
    }

    if (role === "OWNER") {
      const hasBuilding = (ownerBuildingsQ.data?.length ?? 0) > 0;
      // "Has a tenant assigned to any flat" derives from the buildings
      // list's per-building occupiedFlatsCount aggregate. Saves us a
      // /flats/owner/{id} round-trip — that endpoint exists server-
      // side but isn't wired into the frontend api client today.
      const hasAssignedTenant = (ownerBuildingsQ.data ?? []).some(
        (b) => (b.occupiedFlatsCount ?? 0) > 0,
      );
      const hasBank = bankAccountQ.data != null;
      // Owner flow — four steps in the order they're most likely to
      // tackle them. KYC last only if not paused; otherwise drop.
      return [
        {
          id: "owner-list-property",
          label: "List your first property",
          description: hasBuilding
            ? "Your property is live on the platform."
            : "Add a building and at least one flat. Takes about three minutes.",
          complete: hasBuilding,
          href: hasBuilding ? "/owner/buildings" : "/owner/buildings/new",
          icon: Building2,
        },
        {
          id: "owner-bank",
          label: "Add your bank details",
          description: hasBank
            ? "Rent payouts route to your saved account."
            : "Required to receive rent. Stored encrypted, visible only to you.",
          complete: hasBank,
          href: "/owner/profile",
          icon: Wallet,
        },
        {
          id: "owner-assign-tenant",
          label: "Assign your tenant",
          description: hasAssignedTenant
            ? "Your tenant is on board. First invoice was auto-generated."
            : "Pick from tenants who've visited your flat — or invite by email.",
          complete: hasAssignedTenant,
          href: "/owner/tenants",
          icon: Users,
        },
        ...(kycPaused
          ? []
          : [
              {
                id: "owner-kyc",
                label: "Verify your identity",
                description: kycVerified
                  ? "Identity confirmed — your listings show the 'Verified owner' badge."
                  : "Renters trust verified owners. Quick PAN check.",
                complete: kycVerified,
                href: "/owner/profile",
                icon: BadgeCheck,
              },
            ]),
      ];
    }

    // Admin / unknown role — no wizard.
    return [];
  }, [
    authUserId,
    role,
    kycPaused,
    kycQ.data,
    tenantFlatsQ.data,
    tenantPaymentsQ.data,
    ownerBuildingsQ.data,
    bankAccountQ.data,
  ]);

  const completeCount = steps.filter((s) => s.complete).length;
  const totalCount = steps.length;
  const allComplete = totalCount > 0 && completeCount === totalCount;

  // Loading is "true while ANY relevant query hasn't returned data
  // yet" — we don't want the wizard to show a half-state where a
  // step is wrongly marked incomplete because its query hasn't
  // resolved. Per-role to avoid waiting on owner queries while a
  // tenant logs in (and vice-versa).
  const isLoading =
    !authUserId ||
    (role === "TENANT" &&
      (tenantFlatsQ.isLoading || tenantPaymentsQ.isLoading)) ||
    (role === "OWNER" &&
      (ownerBuildingsQ.isLoading || bankAccountQ.isLoading)) ||
    (!kycPaused && kycQ.isLoading);

  /* ── modal-dismissed state (localStorage) ────────────────────── */

  const [modalDismissed, setModalDismissed] = useState<boolean>(() => {
    if (typeof window === "undefined") return true;
    return !!window.localStorage.getItem(modalSeenKey(authUserId));
  });

  // Re-read when the user changes (sign-out then sign-in with a
  // different account in the same browser tab).
  useEffect(() => {
    if (typeof window === "undefined") return;
    setModalDismissed(!!window.localStorage.getItem(modalSeenKey(authUserId)));
  }, [authUserId]);

  const dismissModal = useCallback(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(modalSeenKey(authUserId), "1");
    setModalDismissed(true);
  }, [authUserId]);

  return {
    role: role as Role | null,
    steps,
    completeCount,
    totalCount,
    allComplete,
    isLoading,
    modalDismissed,
    dismissModal,
  };
}

/**
 * Sparkles icon re-exported for the "you're all done" celebratory
 * state in callers that don't already import lucide-react.
 */
export { Sparkles as OnboardingDoneIcon };
