import { createBrowserRouter, Navigate } from "react-router-dom";
import { PublicLayout } from "@/components/layout/public-layout";
import { AppShell } from "@/components/layout/app-shell";
import { ProtectedRoute } from "@/components/auth/protected-route";
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
import { ProfilePage } from "@/pages/tenant/profile";
import { TenantLeasePage } from "@/pages/tenant/lease";
import { OwnerDashboard } from "@/pages/owner/dashboard";
import { BuildingsPage } from "@/pages/owner/buildings";
import { BuildingNewPage } from "@/pages/owner/building-new";
import { BuildingDetailPage } from "@/pages/owner/building-detail";
import { FlatsPage } from "@/pages/owner/flats";
import { FlatNewPage } from "@/pages/owner/flat-new";
import { TenantsPage } from "@/pages/owner/tenants";
import { OwnerPaymentsPage } from "@/pages/owner/payments";
import { OwnerMaintenancePage } from "@/pages/owner/maintenance";
import { OwnerAnalyticsPage } from "@/pages/owner/analytics";
import { OwnerAgreementsPage } from "@/pages/owner/agreements";
import { AdminDashboard } from "@/pages/admin/dashboard";
import { AdminUsersPage } from "@/pages/admin/users";
import { AdminPropertiesPage } from "@/pages/admin/properties";
import { AdminPaymentsPage } from "@/pages/admin/payments";
import { AdminMaintenancePage } from "@/pages/admin/maintenance";

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
      { index: true, element: <TenantDashboard /> },
      { path: "my-flat", element: <MyFlatPage /> },
      { path: "payments", element: <PaymentsListPage /> },
      { path: "payments/:id/pay", element: <PayPage /> },
      { path: "payments/:id/return", element: <PaymentReturnPage /> },
      { path: "maintenance", element: <MaintenancePage /> },
      { path: "maintenance/new", element: <MaintenanceNewPage /> },
      { path: "lease", element: <TenantLeasePage /> },
      { path: "profile", element: <ProfilePage /> },
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
      { path: "payments", element: <OwnerPaymentsPage /> },
      { path: "maintenance", element: <OwnerMaintenancePage /> },
      { path: "agreements", element: <OwnerAgreementsPage /> },
      { path: "analytics", element: <OwnerAnalyticsPage /> },
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
