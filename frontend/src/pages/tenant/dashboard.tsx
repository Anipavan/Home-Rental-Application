import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  ChevronDown,
  ChevronUp,
  CreditCard,
  Wrench,
  Home,
  Calendar,
  TrendingUp,
  IndianRupee,
  MessageSquareWarning,
  Users,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { formatINR, formatDate } from "@/lib/utils";

export function TenantDashboard() {
  const { authUserId, userName } = useAuthStore();

  // Per-card collapse toggles — start expanded. Local component
  // state (not persisted) since these cards are on the landing page
  // and users generally want the same view every visit.
  const [paymentsOpen, setPaymentsOpen] = useState(true);
  const [maintenanceOpen, setMaintenanceOpen] = useState(true);

  const flatsQ = useQuery({
    queryKey: ["my-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId!),
    enabled: !!authUserId,
  });
  const paymentsQ = useQuery({
    queryKey: ["my-payments", authUserId],
    queryFn: () => paymentsApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });
  const requestsQ = useQuery({
    queryKey: ["my-maintenance", authUserId],
    queryFn: () => maintenanceApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  const flat = flatsQ.data?.[0];
  const payments = paymentsQ.data ?? [];
  const pending = payments.find((p) => p.status === "PENDING" || p.status === "OVERDUE");
  const openRequests = (requestsQ.data ?? []).filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  ).length;

  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 12) return "Good morning";
    if (h < 17) return "Good afternoon";
    return "Good evening";
  })();

  return (
    <div className="animate-fade-in">
      <div className="mb-7">
        <p className="text-muted-foreground text-sm">
          {greeting},
        </p>
        <h1 className="font-display text-3xl font-bold tracking-tight">
          {userName ?? "there"} 👋
        </h1>
      </div>

      {pending ? (
        <Card className="mb-6 overflow-hidden border-0 gradient-brand text-white shadow-lift">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.18),transparent_60%)] pointer-events-none" />
          <CardContent className="relative p-7 sm:p-8 grid gap-6 sm:grid-cols-[1fr_auto] items-center">
            <div>
              <p className="text-white/80 text-sm">
                {pending.status === "OVERDUE" ? "Rent overdue" : "Rent due"}
              </p>
              <p className="font-display text-3xl sm:text-4xl font-bold mt-1.5">
                {formatINR(pending.totalAmount ?? pending.amount)}
              </p>
              <p className="text-white/80 text-sm mt-1.5">
                {pending.status === "OVERDUE"
                  ? `Overdue since ${formatDate(pending.dueDate)} — late fee applied`
                  : `Due ${formatDate(pending.dueDate)}`}
              </p>
            </div>
            <Button asChild size="lg" className="bg-white text-foreground hover:bg-white/90">
              <Link to={`/app/payments/${pending.id}/pay`}>
                <CreditCard /> Pay now
              </Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card className="mb-6 bg-gradient-to-br from-success/10 via-success/5 to-transparent border-success/20">
          <CardContent className="p-6 flex items-center gap-4">
            <div className="size-12 rounded-full bg-success/20 text-success grid place-items-center">
              <TrendingUp />
            </div>
            <div>
              <p className="font-display font-semibold text-lg">All caught up!</p>
              <p className="text-sm text-muted-foreground">
                No rent due. Your next bill arrives soon.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-3 mb-7">
        <StatCard
          icon={Home}
          label="My home"
          value={flat ? `Flat ${flat.flatNumber}` : flatsQ.isLoading ? "…" : "Not assigned"}
          hint={flat ? `Floor ${flat.floor ?? "—"}` : undefined}
        />
        <StatCard
          icon={Calendar}
          label="Lease ends"
          value={flat ? formatDate(flat.leaseEndDate) : "—"}
        />
        <StatCard
          icon={Wrench}
          label="Open requests"
          value={requestsQ.isLoading ? "…" : String(openRequests)}
          hint={openRequests > 0 ? "Track progress →" : "Everything looks great"}
        />
      </div>

      {/* Colour-coded shortcut tiles — one tap into the section a
          tenant actually reaches for. Sits between the stat row and
          the detail cards because that's where the eye lands next
          after scanning the summary tiles. */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-7">
        <QuickTile
          to="/app/payments"
          icon={IndianRupee}
          label="Payments"
          sub="View & pay"
          gradient="from-emerald-500 to-emerald-600"
        />
        <QuickTile
          to="/app/maintenance"
          icon={Wrench}
          label="Maintenance"
          sub="Raise a request"
          gradient="from-amber-500 to-orange-600"
        />
        <QuickTile
          to="/app/complaints"
          icon={MessageSquareWarning}
          label="Complaints"
          sub="File an issue"
          gradient="from-rose-500 to-pink-600"
        />
        <QuickTile
          to="/app/society"
          icon={Users}
          label="Society"
          sub="Notices & bills"
          gradient="from-violet-500 to-purple-600"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="p-6 flex items-center justify-between gap-2">
            <h2 className="font-display font-semibold text-lg">Recent payments</h2>
            <div className="flex items-center gap-1">
              <Button asChild variant="ghost" size="sm">
                <Link to="/app/payments">
                  See all <ArrowRight />
                </Link>
              </Button>
              <button
                type="button"
                onClick={() => setPaymentsOpen((v) => !v)}
                className="size-8 shrink-0 grid place-items-center rounded-full bg-primary/10 text-primary border border-primary/30 hover:bg-primary hover:text-primary-foreground transition-colors"
                aria-label={paymentsOpen ? "Collapse recent payments" : "Expand recent payments"}
                aria-expanded={paymentsOpen}
                title={paymentsOpen ? "Collapse" : "Expand"}
              >
                {paymentsOpen ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
              </button>
            </div>
          </div>
          {paymentsOpen && (
            <div className="px-6 pb-6 space-y-2">
              {paymentsQ.isLoading &&
                Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-14 rounded-lg" />
                ))}
              {!paymentsQ.isLoading && payments.length === 0 && (
                <p className="text-sm text-muted-foreground py-6 text-center">
                  No payments yet.
                </p>
              )}
              {payments.slice(0, 5).map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between rounded-lg p-3 hover:bg-secondary/50 transition-colors"
                >
                  <div>
                    <p className="font-medium text-sm">
                      {formatINR(p.totalAmount ?? p.amount)}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Due {formatDate(p.dueDate)}
                    </p>
                  </div>
                  <PaymentStatusBadge status={p.status} />
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <div className="p-6 flex items-center justify-between gap-2">
            <h2 className="font-display font-semibold text-lg">Maintenance</h2>
            <div className="flex items-center gap-1">
              <Button asChild variant="ghost" size="sm">
                <Link to="/app/maintenance">
                  See all <ArrowRight />
                </Link>
              </Button>
              <button
                type="button"
                onClick={() => setMaintenanceOpen((v) => !v)}
                className="size-8 shrink-0 grid place-items-center rounded-full bg-primary/10 text-primary border border-primary/30 hover:bg-primary hover:text-primary-foreground transition-colors"
                aria-label={maintenanceOpen ? "Collapse maintenance" : "Expand maintenance"}
                aria-expanded={maintenanceOpen}
                title={maintenanceOpen ? "Collapse" : "Expand"}
              >
                {maintenanceOpen ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
              </button>
            </div>
          </div>
          {maintenanceOpen && (
            <div className="px-6 pb-6 space-y-2">
              {requestsQ.isLoading &&
                Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-14 rounded-lg" />
                ))}
              {!requestsQ.isLoading && (requestsQ.data?.length ?? 0) === 0 && (
                <p className="text-sm text-muted-foreground py-6 text-center">
                  No requests open.
                </p>
              )}
              {(requestsQ.data ?? []).slice(0, 5).map((r) => (
                <div
                  key={r.id}
                  className="flex items-center justify-between rounded-lg p-3 hover:bg-secondary/50 transition-colors"
                >
                  <div className="min-w-0">
                    <p className="font-medium text-sm truncate">{r.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {r.category ?? r.complaintCategory ?? "—"}
                    </p>
                  </div>
                  <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
                </div>
              ))}
              <Button asChild variant="outline" className="w-full mt-2">
                <Link to="/app/maintenance/new">+ Raise a request</Link>
              </Button>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}

function StatCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className="font-display font-semibold text-lg mt-1">{value}</p>
          {hint && <p className="text-xs text-muted-foreground mt-1">{hint}</p>}
        </div>
        <div className="size-9 rounded-lg bg-primary/10 text-primary grid place-items-center">
          <Icon className="size-4" />
        </div>
      </div>
    </Card>
  );
}

function PaymentStatusBadge({ status }: { status: string }) {
  if (status === "PAID") return <Badge variant="success">Paid</Badge>;
  if (status === "PROCESSING") return <Badge variant="warning">Processing</Badge>;
  if (status === "OVERDUE") return <Badge variant="destructive">Overdue</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  if (status === "CANCELLED") return <Badge variant="secondary">Cancelled</Badge>;
  if (status === "REFUNDED") return <Badge variant="secondary">Refunded</Badge>;
  return <Badge variant="warning">Pending</Badge>;
}

function statusVariant(s: string) {
  if (s === "RESOLVED" || s === "CLOSED") return "success" as const;
  if (s === "IN_PROGRESS") return "warning" as const;
  return "default" as const;
}

/**
 * Coloured shortcut tile — icon in a translucent chip on the left,
 * label + one-line sub on the right. Each tile picks its own gradient
 * so the row scans quickly ("green = money, amber = wrench, red =
 * shout, purple = building").
 */
function QuickTile({
  to,
  icon: Icon,
  label,
  sub,
  gradient,
}: {
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  sub: string;
  gradient: string;
}) {
  return (
    <Link
      to={to}
      className={`group relative overflow-hidden rounded-xl p-4 text-white shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-lg bg-gradient-to-br ${gradient}`}
    >
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.22),transparent_60%)] pointer-events-none" />
      <div className="relative flex items-center gap-3">
        <div className="size-10 shrink-0 rounded-lg bg-white/25 grid place-items-center backdrop-blur-sm">
          <Icon className="size-5" />
        </div>
        <div className="min-w-0">
          <p className="font-semibold text-sm leading-tight">{label}</p>
          <p className="text-[11px] text-white/85 leading-tight mt-0.5 truncate">
            {sub}
          </p>
        </div>
      </div>
    </Link>
  );
}
