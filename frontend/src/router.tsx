import { createBrowserRouter, Navigate } from "react-router-dom";
import { PublicLayout } from "@/components/layout/public-layout";
import { AppShell } from "@/components/layout/app-shell";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { FlatRequiredOutlet } from "@/components/auth/flat-required-outlet";
import { FeatureDisabledOutlet } from "@/components/auth/feature-disabled-outlet";
import { isKycDisabled } from "@/lib/feature-flags";
import { LandingPage } from "@/pages/public/landing";
import { BrowsePage } from "@/pages/public/browse";
import { PropertyDetailPage } from "@/pages/public/property-detail";
import { LoginPage } from "@/pages/public/login";
import { RegisterPage } from "@/pages/public/register";
import { ForgotPasswordPage } from "@/pages/public/forgot-password";
import { ResetPasswordPage } from "@/pages/public/reset-password";
import { NotFoundPage } from "@/pages/public/not-found";
import { TenantDashboard } from "@/pages/tenant/dashboard";
import { MyFlatPage } from "@/pages/tenant/my-flat";
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
import { DocumentsPage } from "@/pages/tenant/documents";
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
import { AdminPropertiesPage } from "@/pages/admin/properties";
import { AdminPaymentsPage } from "@/pages/admin/payments";
import { AdminMaintenancePage } from "@/pages/admin/maintenance";
import { AdminComplaintsPage } from "@/pages/admin/complaints";
import { AdminReviewsPage } from "@/pages/admin/reviews";
import { AdminSupportPage } from "@/pages/admin/support";
import { AdminVisitRequestsPage } from "@/pages/admin/visit-requests";
import { NotificationsInboxPage } from "@/pages/notifications-inbox";
import { NotificationPreferencesPage } from "@/pages/notifications-preferences";
import { SavedListingsPage } from "@/pages/tenant/saved";
import { SavedSearchesPage } from "@/pages/tenant/saved-searches";
import { ComparePage } from "@/pages/tenant/compare";

export const router = createBrowserRouter([
  {
    element: <PublicLayout />,
    children: [
      { path: "/", element: <LandingPage /> },
      { path: "/browse", element: <BrowsePage /> },
      { path: "/property/:id", element: <PropertyDetailPage /> },
      { path: "/about", element: <AboutStub /> },
      { path: "/list-property", element: <Navigate to="/register" replace /> },
    ],
  },
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  { path: "/forgot-password", element: <ForgotPasswordPage /> },
  { path: "/reset-password", element: <ResetPasswordPage /> },
  {
    path: "/app",
    element: (
      <ProtectedRoute roles={["TENANT"]}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      // Always available — even before a flat is assigned. The user's
      // first-day flow is: register → see Overview / My Home (empty
      // state with "browse listings" CTA) / Profile.
      { index: true, element: <TenantDashboard /> },
      { path: "my-flat", element: <MyFlatPage /> },
      { path: "profile", element: <ProfilePage /> },
      // Saved + notifications stay outside FlatRequiredOutlet so a
      // tenant browsing for their NEXT home (no flat assigned yet)
      // can still wishlist and receive registration emails.
      { path: "saved", element: <SavedListingsPage /> },
      // /app/saved-searches is the manage-alerts page. Kept outside
      // FlatRequiredOutlet — a tenant who hasn't been assigned a flat
      // yet is exactly the audience for saved-search alerts.
      { path: "saved-searches", element: <SavedSearchesPage /> },
      { path: "compare", element: <ComparePage /> },
      // Channel-toggle UI — lives outside FlatRequiredOutlet so any
      // user (even pre-flat-assignment) can dial in their delivery
      // preferences right after registration.
      { path: "notifications/preferences", element: <NotificationPreferencesPage /> },
      { path: "notifications", element: <NotificationsInboxPage /> },
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
        ],
      },
    ],
  },
  {
    path: "/owner",
    element: (
      <ProtectedRoute roles={["OWNER"]}>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <OwnerDashboard /> },
      { path: "buildings", element: <BuildingsPage /> },
      { path: "buildings/new", element: <BuildingNewPage /> },
      { path: "buildings/:id", element: <BuildingDetailPage /> },
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
      { path: "compliance", element: <OwnerCompliancePage /> },
      { path: "analytics", element: <OwnerAnalyticsPage /> },
      { path: "notifications", element: <NotificationsInboxPage /> },
      { path: "notifications/preferences", element: <NotificationPreferencesPage /> },
      // Owners get the same Profile page as tenants — the user-service
      // schema is role-agnostic (first/last name, email, phone, photo,
      // address). Same component, just a different route.
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

function AboutStub() {
  return (
    <div className="container py-16 max-w-2xl">
      <h1 className="font-display text-3xl font-bold">About Hearth</h1>
      <p className="text-muted-foreground mt-3">
        Hearth is a calm, modern rental platform — verified homes, instant
        payments, and real humans on support. Built in India, for India.
      </p>
    </div>
  );
}
