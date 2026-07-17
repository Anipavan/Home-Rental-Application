import { createBrowserRouter, Navigate } from "react-router-dom";
import { PublicLayout } from "@/components/layout/public-layout";
import { AppShell } from "@/components/layout/app-shell";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { FlatRequiredOutlet } from "@/components/auth/flat-required-outlet";
import { FeatureDisabledOutlet } from "@/components/auth/feature-disabled-outlet";
import {
  isAlertsDisabled,
  isComplianceDisabled,
  isKycDisabled,
} from "@/lib/feature-flags";
import { LandingPage } from "@/pages/public/landing";
import { BrowsePage } from "@/pages/public/browse";
import { PropertyDetailPage } from "@/pages/public/property-detail";
import { AboutPage } from "@/pages/public/about";
import { LoginPage } from "@/pages/public/login";
import { RegisterPage } from "@/pages/public/register";
import { RegistrationPaymentPage } from "@/pages/public/registration-payment";
import { ForgotPasswordPage } from "@/pages/public/forgot-password";
import { ResetPasswordPage } from "@/pages/public/reset-password";
import { VerifyEmailPage } from "@/pages/public/verify-email";
import { VerifyEmailSentPage } from "@/pages/public/verify-email-sent";
import { WelcomePage } from "@/pages/public/welcome";
import { SetupSocietyPage } from "@/pages/public/setup-society";
import { NotFoundPage } from "@/pages/public/not-found";
import { TenantDashboard } from "@/pages/tenant/dashboard";
import { MyFlatPage } from "@/pages/tenant/my-flat";
import { PendingClaimPage } from "@/pages/tenant/pending-claim";
import { PaymentsListPage } from "@/pages/tenant/payments";
import { PayPage } from "@/pages/tenant/pay";
import { PaymentReturnPage } from "@/pages/tenant/payment-return";
import { MaintenancePage } from "@/pages/tenant/maintenance";
import { MaintenanceNewPage } from "@/pages/tenant/maintenance-new";
import { ComplaintsPage } from "@/pages/tenant/complaints";
import { ComplaintsNewPage } from "@/pages/tenant/complaints-new";
import { ComplaintDetailPage } from "@/pages/tenant/complaint-detail";
import { ProfilePage } from "@/pages/tenant/profile";
import { TenantLeasePage } from "@/pages/tenant/lease";
import { KycPage } from "@/pages/tenant/kyc";
import { KycCallbackPage } from "@/pages/tenant/kyc-callback";
import { DocumentsPage } from "@/pages/tenant/documents";
import { OwnerDocumentsPage } from "@/pages/owner/documents";
import { TenantReviewsPage } from "@/pages/tenant/reviews";
import { OwnerDashboard } from "@/pages/owner/dashboard";
import { BuildingsPage } from "@/pages/owner/buildings";
import { BuildingNewPage } from "@/pages/owner/building-new";
import { BuildingDetailPage } from "@/pages/owner/building-detail";
import { FlatsPage } from "@/pages/owner/flats";
import { FlatNewPage } from "@/pages/owner/flat-new";
import { TenantsPage } from "@/pages/owner/tenants";
import { TenantDetailPage } from "@/pages/owner/tenant-detail";
import { OwnerPaymentsPage } from "@/pages/owner/payments";
import { OwnerMaintenancePage } from "@/pages/owner/maintenance";
import { OwnerComplaintsPage } from "@/pages/owner/complaints";
import { OwnerAnalyticsPage } from "@/pages/owner/analytics";
import { OwnerAgreementsPage } from "@/pages/owner/agreements";
import { OwnerLeasesPage } from "@/pages/owner/leases";
import { OwnerEnquiriesPage } from "@/pages/owner/enquiries";
import { OwnerCompliancePage } from "@/pages/owner/compliance";
import { AdminDashboard } from "@/pages/admin/dashboard";
import { AdminUsersPage } from "@/pages/admin/users";
import { AdminVendorUsagePage } from "@/pages/admin/vendor-usage";
import { AdminSettingsPage } from "@/pages/admin/settings";
import { MaintainerPaymentGate } from "@/components/maintainer/payment-gate";
import { AdminPropertiesPage } from "@/pages/admin/properties";
import { AdminPaymentsPage } from "@/pages/admin/payments";
import { AdminMaintenancePage } from "@/pages/admin/maintenance";
import { AdminComplaintsPage } from "@/pages/admin/complaints";
import { AdminReviewsPage } from "@/pages/admin/reviews";
import { AdminSupportPage } from "@/pages/admin/support";
import { AdminVisitRequestsPage } from "@/pages/admin/visit-requests";
import { AdminAnnouncementsPage } from "@/pages/admin/announcements";
import { NotificationsInboxPage } from "@/pages/notifications-inbox";
import { NotificationPreferencesPage } from "@/pages/notifications-preferences";
import { SavedListingsPage } from "@/pages/tenant/saved";
import { SavedSearchesPage } from "@/pages/tenant/saved-searches";
import { ComparePage } from "@/pages/tenant/compare";
import { OwnerSocietyPage } from "@/pages/owner/society";
import { OwnerSocietiesOverviewPage } from "@/pages/owner/societies-overview";
import { TenantSocietyPage } from "@/pages/tenant/society";
import { SocietyPayPage } from "@/pages/tenant/society-pay";
import { SocietyPayAllPage } from "@/pages/tenant/society-pay-all";
import { PublicSocietyLedgerPage } from "@/pages/public/society-ledger";
import {
  MaintainerFlatsPage,
  MaintainerHomePage,
} from "@/pages/maintainer/dashboard";
import { MaintainerExpensesPage } from "@/pages/maintainer/expenses";

export const router = createBrowserRouter([
  {
    element: <PublicLayout />,
    children: [
      { path: "/", element: <LandingPage /> },
      { path: "/browse", element: <BrowsePage /> },
      { path: "/property/:id", element: <PropertyDetailPage /> },
      { path: "/about", element: <AboutPage /> },
      { path: "/list-property", element: <Navigate to="/register" replace /> },
    ],
  },
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  { path: "/registration-payment", element: <RegistrationPaymentPage /> },
  { path: "/forgot-password", element: <ForgotPasswordPage /> },
  { path: "/reset-password", element: <ResetPasswordPage /> },
  { path: "/verify-email-sent", element: <VerifyEmailSentPage /> },
  { path: "/verify-email/:token", element: <VerifyEmailPage /> },
  { path: "/welcome", element: <WelcomePage /> },
  { path: "/setup-society", element: <SetupSocietyPage /> },
  // ─── Public society ledger ───
  // Standalone page (no AppShell). The {token} in the URL is the only
  // credential — gateway whitelisted under
  // GET /rentals/v1/society/public/**. Anyone with the link can view.
  { path: "/society/view/:token", element: <PublicSocietyLedgerPage /> },
  // ─── Membership-claim status (role-agnostic) ───
  // Surfaced for self-registered MAINTAINER, RESIDENT, AND FLAT_OWNER
  // claimants while they wait for approval. Lives outside any role
  // shell so an OWNER-role flat-owner-claimant can land here without
  // tripping the role-gated /app or /owner trees. Page is the same
  // PendingClaimPage that was previously at /app/pending-claim — the
  // /app alias stays for backward compat with bookmarks.
  {
    path: "/pending-claim",
    element: (
      <ProtectedRoute>
        <PendingClaimPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/app",
    element: (
      // MAINTAINEE reuses the tenant AppShell — the pages inside are
      // role-agnostic (Overview, Society, Payments, Profile all key
      // off authUserId, not role). The app-shell renders a slim
      // sidebar for MAINTAINEE via navFor(); nav hiding is enough
      // because a maintainee has no data on the tenant-only surfaces
      // (Lease, Maintenance-request, etc.) even if they navigate there.
      <ProtectedRoute roles={["TENANT", "MAINTAINEE"]}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      // Always available — even before a flat is assigned. The user's
      // first-day flow is: register → see Overview / My Home (empty
      // state with "browse listings" CTA) / Profile.
      { index: true, element: <TenantDashboard /> },
      { path: "my-flat", element: <MyFlatPage /> },
      // Status page for users who registered as a society
      // maintainer/maintainee — shows their claim's status and
      // an explicit "sign in again" CTA once approved.
      { path: "pending-claim", element: <PendingClaimPage /> },
      { path: "profile", element: <ProfilePage /> },
      // Saved + notifications stay outside FlatRequiredOutlet so a
      // tenant browsing for their NEXT home (no flat assigned yet)
      // can still wishlist and receive registration emails.
      { path: "saved", element: <SavedListingsPage /> },
      // /app/saved-searches is the manage-alerts page. Kept outside
      // FlatRequiredOutlet — a tenant who hasn't been assigned a flat
      // yet is exactly the audience for saved-search alerts. Same
      // disable pattern as KYC below — the route still renders so
      // the URL / nav highlight stay consistent, but it's wrapped in
      // FeatureDisabledOutlet when the flag is on.
      ...(isAlertsDisabled()
        ? [
            {
              element: (
                <FeatureDisabledOutlet
                  feature="Alerts"
                  reason="We've paused saved-search alerts while we polish the email digest. Your saved searches are kept — you'll start getting matches again as soon as we re-enable this."
                />
              ),
              children: [{ path: "saved-searches", element: <SavedSearchesPage /> }],
            },
          ]
        : [{ path: "saved-searches", element: <SavedSearchesPage /> }]),
      { path: "compare", element: <ComparePage /> },
      // Channel-toggle UI — lives outside FlatRequiredOutlet so any
      // user (even pre-flat-assignment) can dial in their delivery
      // preferences right after registration.
      { path: "notifications/preferences", element: <NotificationPreferencesPage /> },
      { path: "notifications", element: <NotificationsInboxPage /> },
      // DigiLocker OAuth callback. Lives outside FlatRequiredOutlet so a
      // tenant who just registered (no flat yet) can still complete
      // identity verification. Lives outside the FeatureDisabledOutlet
      // wrapper below for the same reason — when KYC is paused there's
      // no flow to come back to, so we don't gate this URL on the flag.
      // The page itself short-circuits with a friendly error if the
      // backend rejects the code/state.
      { path: "kyc/callback", element: <KycCallbackPage /> },
      // Browse listings is always available — a tenant with a flat
      // assigned should still be able to look at other homes (longer
      // lease, bigger place, different neighbourhood, etc.). Reuses
      // the public BrowsePage component, which doesn't depend on the
      // PublicLayout shell — it's a self-contained page that drops
      // cleanly inside AppShell's <main>.
      { path: "browse", element: <BrowsePage /> },
      // Gated by FlatRequiredOutlet. Direct-URL access without a flat
      // gets toasted + redirected back to /app. Nav-link clicks are
      // also intercepted in AppShell for the same UX without a route
      // round-trip. Set membership in TENANT_FLAT_REQUIRED_PATHS
      // (use-tenant-has-flat.ts) must stay in sync with the children
      // of this nested layout-route.
      {
        element: <FlatRequiredOutlet />,
        children: [
          { path: "payments", element: <PaymentsListPage /> },
          { path: "payments/:id/pay", element: <PayPage /> },
          { path: "payments/:id/return", element: <PaymentReturnPage /> },
          { path: "maintenance", element: <MaintenancePage /> },
          { path: "maintenance/new", element: <MaintenanceNewPage /> },
          { path: "complaints", element: <ComplaintsPage /> },
          { path: "complaints/new", element: <ComplaintsNewPage /> },
          { path: "complaints/:id", element: <ComplaintDetailPage /> },
          { path: "lease", element: <TenantLeasePage /> },
          // KYC is paused platform-wide while we tune the provider
          // integration. The page still renders behind the overlay so
          // the URL/nav highlight stays consistent. The on/off switch
          // lives in lib/feature-flags.ts (KYC_DISABLED) and is read
          // by every KYC surface (this route, the tenant kyc.tsx
          // queries, the owner tenant-detail badge, the sidebar pill).
          // Flip the flag to re-enable — nothing else needs to change.
          isKycDisabled()
            ? {
                element: (
                  <FeatureDisabledOutlet
                    feature="KYC"
                    reason="We've paused identity verification while we upgrade the provider integration. You can continue using the rest of the app — paying rent, raising tickets, and signing leases — without it for now."
                  />
                ),
                children: [{ path: "kyc", element: <KycPage /> }],
              }
            : { path: "kyc", element: <KycPage /> },
          { path: "documents", element: <DocumentsPage /> },
          { path: "reviews", element: <TenantReviewsPage /> },
          // Society read-only ledger for the tenant's building.
          { path: "society", element: <TenantSocietyPage /> },
          // Dedicated UPI Pay page for one society charge — replaces
          // the earlier in-page modal so the navigation pattern
          // matches the rent-pay flow (/app/payments/:id/pay).
          {
            path: "society/pay/:buildingId/:collectionId",
            element: <SocietyPayPage />,
          },
          // Bulk-pay landing — lists every DUE charge for a month
          // and (once the Razorpay bridge for society charges ships)
          // launches a single Razorpay order covering all of them.
          // Today the page lists rows with individual Pay buttons +
          // a disabled "Pay all via Razorpay" CTA so users see what's
          // coming without being misled into clicking a no-op.
          {
            path: "society/pay-all/:buildingId/:month",
            element: <SocietyPayAllPage />,
          },
        ],
      },
    ],
  },
  {
    path: "/owner",
    element: (
      <ProtectedRoute roles={["OWNER", "MAINTAINER"]}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <OwnerDashboard /> },
      { path: "buildings", element: <BuildingsPage /> },
      { path: "buildings/new", element: <BuildingNewPage /> },
      { path: "buildings/:id", element: <BuildingDetailPage /> },
      // Society / common-area maintenance — overview list + per-building
      // ledger view (with first-time setup wizard).
      { path: "society", element: <OwnerSocietiesOverviewPage /> },
      { path: "buildings/:id/society", element: <OwnerSocietyPage /> },
      { path: "flats", element: <FlatsPage /> },
      { path: "flats/new", element: <FlatNewPage /> },
      { path: "tenants", element: <TenantsPage /> },
      { path: "tenants/:tenantId", element: <TenantDetailPage /> },
      { path: "payments", element: <OwnerPaymentsPage /> },
      { path: "maintenance", element: <OwnerMaintenancePage /> },
      { path: "complaints", element: <OwnerComplaintsPage /> },
      { path: "agreements", element: <OwnerAgreementsPage /> },
      { path: "leases", element: <OwnerLeasesPage /> },
      { path: "enquiries", element: <OwnerEnquiriesPage /> },
      // Compliance paused while the compliance-service swaps from
      // MOCK to live RERA provider integration. Same wrapped-outlet
      // pattern as KYC above so the page URL still resolves but renders
      // behind a "temporarily unavailable" overlay.
      ...(isComplianceDisabled()
        ? [
            {
              element: (
                <FeatureDisabledOutlet
                  feature="Compliance"
                  reason="We've paused RERA + GST compliance tools while we switch from our mock provider to live integrations. Your existing filings stay intact — this page returns when the new provider is live."
                />
              ),
              children: [{ path: "compliance", element: <OwnerCompliancePage /> }],
            },
          ]
        : [{ path: "compliance", element: <OwnerCompliancePage /> }]),
      { path: "analytics", element: <OwnerAnalyticsPage /> },
      { path: "documents", element: <OwnerDocumentsPage /> },
      // Owners need PAN verification too — the same KycPage component
      // works for any role (it keys on authUserId, no tenant-specific
      // assumptions). Wrapped in the same FeatureDisabledOutlet as
      // the tenant /app/kyc route so a single flag flip toggles both
      // surfaces. Onboarding wizard's owner "Verify identity" step
      // links here.
      ...(isKycDisabled()
        ? [
            {
              element: (
                <FeatureDisabledOutlet
                  feature="KYC"
                  reason="We've paused identity verification while we upgrade the provider integration. You can continue listing properties, collecting rent, and managing leases without it for now."
                />
              ),
              children: [{ path: "kyc", element: <KycPage /> }],
            },
          ]
        : [{ path: "kyc", element: <KycPage /> }]),
      { path: "notifications", element: <NotificationsInboxPage /> },
      { path: "notifications/preferences", element: <NotificationPreferencesPage /> },
      // Owners get the same Profile page as tenants — the user-service
      // schema is role-agnostic (first/last name, email, phone, photo,
      // address). Same component, just a different route.
      { path: "profile", element: <ProfilePage /> },
    ],
  },
  // ─── Maintainer (society-only role) ───
  // Slim sidebar; only the per-flat dashboard + expense ledger + profile.
  // OWNER role is whitelisted too so an owner who self-assigned can use
  // the same screens without bouncing back through the owner shell.
  {
    path: "/maintainer",
    element: (
      <ProtectedRoute roles={["MAINTAINER", "OWNER"]}>
        <MaintainerPaymentGate>
          <AppShell />
        </MaintainerPaymentGate>
      </ProtectedRoute>
    ),
    children: [
      // Society list — auto-redirects to the single building's flat
      // dashboard when the maintainer manages exactly one (the common
      // case). Otherwise renders a picker.
      { index: true, element: <MaintainerHomePage /> },
      { path: ":buildingId/flats", element: <MaintainerFlatsPage /> },
      { path: ":buildingId/expenses", element: <MaintainerExpensesPage /> },
      { path: "notifications", element: <NotificationsInboxPage /> },
      { path: "notifications/preferences", element: <NotificationPreferencesPage /> },
      // Reuse the tenant ProfilePage component (role-agnostic — keys
      // off authUserId only). Same UI, same user-service backend.
      { path: "profile", element: <ProfilePage /> },
    ],
  },
  {
    path: "/admin",
    element: (
      <ProtectedRoute roles={["ADMIN"]}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <AdminDashboard /> },
      { path: "users", element: <AdminUsersPage /> },
      { path: "properties", element: <AdminPropertiesPage /> },
      { path: "payments", element: <AdminPaymentsPage /> },
      { path: "maintenance", element: <AdminMaintenancePage /> },
      { path: "complaints", element: <AdminComplaintsPage /> },
      { path: "reviews", element: <AdminReviewsPage /> },
      { path: "support", element: <AdminSupportPage /> },
      { path: "visit-requests", element: <AdminVisitRequestsPage /> },
      { path: "announcements", element: <AdminAnnouncementsPage /> },
      { path: "vendor-usage", element: <AdminVendorUsagePage /> },
      { path: "settings", element: <AdminSettingsPage /> },
      { path: "notifications", element: <NotificationsInboxPage /> },
      { path: "notifications/preferences", element: <NotificationPreferencesPage /> },
      // Admin profile mirrors the owner/tenant Profile page — same UI,
      // same User Service backend. Lets admins set their own photo +
      // contact info instead of being stuck with auth-tier initials.
      { path: "profile", element: <ProfilePage /> },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);

