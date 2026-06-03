import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "@/stores/auth-store";
import type { Role } from "@/types/api";

export function ProtectedRoute({
  children,
  roles,
}: {
  children: React.ReactNode;
  roles?: Role[];
}) {
  const { isAuthenticated, role } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (roles && role && !roles.includes(role)) {
    // Maintainer lands on its slim dashboard. Owners (and owner-as-
    // maintainer dual-hat users) get the full owner shell.
    const home =
      role === "MAINTAINER"
        ? "/maintainer"
        : role === "OWNER"
          ? "/owner"
          : role === "ADMIN"
            ? "/admin"
            : "/app";
    return <Navigate to={home} replace />;
  }

  return <>{children}</>;
}
