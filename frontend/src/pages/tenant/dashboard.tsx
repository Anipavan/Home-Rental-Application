import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  CreditCard,
  Wrench,
  Home,
  Calendar,
  TrendingUp,
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
        <Card className="mb-6 overflow-hidden border-0 bg-gradient-to-br from-indigo-600 via-violet-600 to-fuchsia-600 text-white shadow-lift">
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

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="p-6 flex items-center justify-between">
            <h2 className="font-display font-semibold text-lg">Recent payments</h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/app/payments">
                See all <ArrowRight />
              </Link>
            </Button>
          </div>
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
        </Card>

        <Card>
          <div className="p-6 flex items-center justify-between">
            <h2 className="font-display font-semibold text-lg">Maintenance</h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/app/maintenance">
                See all <ArrowRight />
              </Link>
            </Button>
          </div>
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
                  <p className="text-xs text-muted-foreground">{r.category}</p>
                </div>
                <Badge variant={statusVariant(r.status)}>{r.status}</Badge>
              </div>
            ))}
            <Button asChild variant="outline" className="w-full mt-2">
              <Link to="/app/maintenance/new">+ Raise a request</Link>
            </Button>
          </div>
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
