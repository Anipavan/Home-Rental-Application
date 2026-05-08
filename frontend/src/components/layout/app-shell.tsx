import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Home,
  Building2,
  Users,
  Receipt,
  Wrench,
  BarChart3,
  Settings,
  LogOut,
  Search,
  CreditCard,
  ShieldCheck,
  LayoutGrid,
  ScrollText,
  FileText,
  Star,
  BadgeCheck,
  Stamp,
} from "lucide-react";
import { Logo } from "./logo";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { useAuthStore } from "@/stores/auth-store";
import { authApi } from "@/lib/api/auth";
import { cn, initials } from "@/lib/utils";
import type { Role } from "@/types/api";

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

const tenantNav: NavItem[] = [
  { to: "/app", label: "Overview", icon: Home },
  { to: "/app/my-flat", label: "My Home", icon: Building2 },
  { to: "/app/lease", label: "Lease", icon: ScrollText },
  { to: "/app/payments", label: "Payments", icon: Receipt },
  { to: "/app/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/app/kyc", label: "KYC", icon: BadgeCheck },
  { to: "/app/documents", label: "Documents", icon: FileText },
  { to: "/app/reviews", label: "Reviews", icon: Star },
  { to: "/app/profile", label: "Profile", icon: Settings },
];

const ownerNav: NavItem[] = [
  { to: "/owner", label: "Overview", icon: Home },
  { to: "/owner/buildings", label: "Buildings", icon: Building2 },
  { to: "/owner/flats", label: "Flats", icon: LayoutGrid },
  { to: "/owner/tenants", label: "Tenants", icon: Users },
  { to: "/owner/payments", label: "Payments", icon: Receipt },
  { to: "/owner/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/owner/agreements", label: "Agreements", icon: ScrollText },
  { to: "/owner/leases", label: "Leases", icon: FileText },
  { to: "/owner/compliance", label: "Compliance", icon: Stamp },
  { to: "/owner/analytics", label: "Analytics", icon: BarChart3 },
];

const adminNav: NavItem[] = [
  { to: "/admin", label: "Overview", icon: ShieldCheck },
  { to: "/admin/users", label: "Users", icon: Users },
  { to: "/admin/properties", label: "Properties", icon: Building2 },
  { to: "/admin/payments", label: "Payments", icon: Receipt },
  { to: "/admin/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/admin/reviews", label: "Reviews", icon: Star },
  { to: "/admin/support", label: "Support", icon: FileText },
];

function navFor(role: Role | null): NavItem[] {
  if (role === "OWNER") return ownerNav;
  if (role === "ADMIN") return adminNav;
  return tenantNav;
}

export function AppShell() {
  const { role, userName, refreshToken, clear } = useAuthStore();
  const navigate = useNavigate();
  const items = navFor(role);

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
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="p-3 border-t border-border/60">
          <div className="rounded-xl bg-gradient-to-br from-primary/10 via-violet-500/10 to-fuchsia-500/10 p-4 border border-primary/10">
            <p className="text-xs font-semibold text-foreground">
              Need help?
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              Our team is online 9am – 9pm IST.
            </p>
            <ContactSupport className="mt-3 w-full" />
          </div>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="sticky top-0 z-30 h-16 border-b border-border/60 bg-background/85 backdrop-blur-xl">
          <div className="h-full px-4 sm:px-6 flex items-center justify-between gap-4">
            <div className="flex items-center gap-3 flex-1 max-w-md">
              <div className="relative w-full hidden sm:block">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                <Input
                  placeholder="Search homes, tenants, invoices…"
                  className="pl-10 bg-secondary/60 border-transparent"
                />
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
                          ? "/owner"
                          : role === "ADMIN"
                            ? "/admin"
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
          <Outlet />
        </main>

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
