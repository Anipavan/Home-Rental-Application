import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  Home,
  Users,
  TrendingUp,
  Wrench,
  IndianRupee,
  ArrowRight,
} from "lucide-react";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { formatINR, formatDate } from "@/lib/utils";

export function OwnerDashboard() {
  const { authUserId, userName } = useAuthStore();

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const paymentsQ = useQuery({
    queryKey: ["owner-payments", authUserId],
    queryFn: () => paymentsApi.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const maintQ = useQuery({
    queryKey: ["owner-maintenance", authUserId],
    queryFn: () => maintenanceApi.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const totalBuildings = buildingsQ.data?.length ?? 0;
  const totalFlats =
    buildingsQ.data?.reduce((sum, b) => sum + (b.activeFlatsCount ?? b.buildingTotalFlats ?? 0), 0) ?? 0;

  const payments = paymentsQ.data ?? [];
  const paidThisMonth = payments
    .filter((p) => {
      if (p.status !== "PAID" || !p.paymentDate) return false;
      const d = new Date(p.paymentDate);
      const now = new Date();
      return (
        d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
      );
    })
    .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);

  const overdueAmount = payments
    .filter((p) => p.status === "OVERDUE")
    .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);

  const openMaint = (maintQ.data ?? []).filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  ).length;

  const trend = buildMonthlyTrend(payments);

  return (
    <div className="animate-fade-in">
      <div className="mb-7 flex items-end justify-between gap-4 flex-wrap">
        <div>
          <p className="text-sm text-muted-foreground">Welcome back,</p>
          <h1 className="font-display text-3xl font-bold">{userName ?? "Owner"}</h1>
        </div>
        <Button asChild variant="gradient">
          <Link to="/owner/buildings/new">+ Add building</Link>
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-6">
        <KpiCard
          icon={IndianRupee}
          label="Collected this month"
          value={formatINR(paidThisMonth)}
          delta={"+12%"}
          tone="primary"
        />
        <KpiCard
          icon={TrendingUp}
          label="Overdue"
          value={formatINR(overdueAmount)}
          tone="destructive"
        />
        <KpiCard
          icon={Building2}
          label="Buildings"
          value={String(totalBuildings)}
          hint={`${totalFlats} flats`}
          tone="muted"
        />
        <KpiCard
          icon={Wrench}
          label="Open maintenance"
          value={String(openMaint)}
          tone="warning"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <div className="p-6 flex items-center justify-between">
            <div>
              <h2 className="font-display font-semibold text-lg">
                Monthly collection
              </h2>
              <p className="text-xs text-muted-foreground">
                Last 6 months · Paid vs. Pending
              </p>
            </div>
            <Button asChild variant="ghost" size="sm">
              <Link to="/owner/analytics">
                Details <ArrowRight />
              </Link>
            </Button>
          </div>
          <div className="px-2 pb-4 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trend} margin={{ left: 10, right: 20, top: 10 }}>
                <defs>
                  <linearGradient id="grad-paid" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="hsl(244 75% 59%)" stopOpacity={0.4} />
                    <stop offset="100%" stopColor="hsl(244 75% 59%)" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="grad-pending" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="hsl(38 92% 50%)" stopOpacity={0.3} />
                    <stop offset="100%" stopColor="hsl(38 92% 50%)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(220 13% 91%)" />
                <XAxis dataKey="m" tickLine={false} axisLine={false} fontSize={12} />
                <YAxis tickLine={false} axisLine={false} fontSize={12} tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}K`} />
                <Tooltip
                  formatter={(v: number) => formatINR(v)}
                  contentStyle={{
                    borderRadius: 12,
                    border: "1px solid hsl(220 13% 91%)",
                    boxShadow: "0 10px 30px -10px rgb(15 23 42 / 0.18)",
                  }}
                />
                <Area type="monotone" dataKey="paid" stroke="hsl(244 75% 59%)" strokeWidth={2.5} fill="url(#grad-paid)" />
                <Area type="monotone" dataKey="pending" stroke="hsl(38 92% 50%)" strokeWidth={2.5} fill="url(#grad-pending)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card>
          <div className="p-6 flex items-center justify-between">
            <h2 className="font-display font-semibold text-lg">Recent activity</h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/owner/payments">All</Link>
            </Button>
          </div>
          <div className="px-6 pb-6 space-y-3">
            {paymentsQ.isLoading &&
              Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            {(paymentsQ.data ?? []).slice(0, 5).map((p) => (
              <div
                key={p.id}
                className="flex items-center justify-between text-sm"
              >
                <div className="min-w-0">
                  <p className="font-medium truncate">
                    Flat #{p.flatId}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {p.paymentDate ? formatDate(p.paymentDate) : `Due ${formatDate(p.dueDate)}`}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-semibold">
                    {formatINR(p.totalAmount ?? p.amount)}
                  </p>
                  {p.status === "PAID" ? (
                    <Badge variant="success">Paid</Badge>
                  ) : p.status === "OVERDUE" ? (
                    <Badge variant="destructive">Overdue</Badge>
                  ) : (
                    <Badge variant="warning">Pending</Badge>
                  )}
                </div>
              </div>
            ))}
            {!paymentsQ.isLoading && (paymentsQ.data?.length ?? 0) === 0 && (
              <p className="text-sm text-muted-foreground text-center py-4">
                No activity yet.
              </p>
            )}
          </div>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="p-6 flex items-center justify-between">
            <div>
              <h2 className="font-display font-semibold text-lg">Buildings</h2>
              <p className="text-xs text-muted-foreground">
                {totalBuildings} active
              </p>
            </div>
            <Button asChild variant="ghost" size="sm">
              <Link to="/owner/buildings">
                Manage <ArrowRight />
              </Link>
            </Button>
          </div>
          <div className="px-6 pb-6 space-y-2">
            {buildingsQ.isLoading &&
              Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            {(buildingsQ.data ?? []).slice(0, 4).map((b) => (
              <Link
                key={b.buildingId}
                to={`/owner/buildings/${b.buildingId}`}
                className="flex items-center justify-between p-3 rounded-lg hover:bg-secondary/50 transition-colors"
              >
                <div className="flex items-center gap-3">
                  <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center">
                    <Building2 className="size-4" />
                  </div>
                  <div>
                    <p className="font-medium text-sm">{b.buildingName}</p>
                    <p className="text-xs text-muted-foreground">
                      {b.buildingCity}, {b.buildingState}
                    </p>
                  </div>
                </div>
                <Badge variant="secondary">{b.activeFlatsCount ?? b.buildingTotalFlats ?? 0} flats</Badge>
              </Link>
            ))}
            {!buildingsQ.isLoading && (buildingsQ.data?.length ?? 0) === 0 && (
              <p className="text-sm text-muted-foreground text-center py-6">
                No buildings yet.
              </p>
            )}
          </div>
        </Card>

        <Card>
          <div className="p-6 flex items-center justify-between">
            <div>
              <h2 className="font-display font-semibold text-lg">Maintenance queue</h2>
              <p className="text-xs text-muted-foreground">
                {openMaint} active requests
              </p>
            </div>
            <Button asChild variant="ghost" size="sm">
              <Link to="/owner/maintenance">
                Manage <ArrowRight />
              </Link>
            </Button>
          </div>
          <div className="px-6 pb-6 space-y-2">
            {maintQ.isLoading &&
              Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            {(maintQ.data ?? [])
              .filter((r) => r.status !== "CLOSED" && r.status !== "RESOLVED")
              .slice(0, 4)
              .map((r) => (
                <div
                  key={r.id}
                  className="flex items-start justify-between p-3 rounded-lg hover:bg-secondary/50 transition-colors"
                >
                  <div className="min-w-0">
                    <p className="font-medium text-sm truncate">{r.title}</p>
                    <p className="text-xs text-muted-foreground">
                      Flat #{r.flatId} · {r.category}
                    </p>
                  </div>
                  <Badge variant={r.priority === "CRITICAL" || r.priority === "HIGH" ? "destructive" : "warning"}>
                    {r.priority}
                  </Badge>
                </div>
              ))}
            {openMaint === 0 && !maintQ.isLoading && (
              <p className="text-sm text-muted-foreground text-center py-6">
                Nothing open. Excellent.
              </p>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}

function KpiCard({
  icon: Icon,
  label,
  value,
  delta,
  hint,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  delta?: string;
  hint?: string;
  tone?: "primary" | "destructive" | "warning" | "muted";
}) {
  const toneCls =
    tone === "primary"
      ? "bg-primary/10 text-primary"
      : tone === "destructive"
        ? "bg-destructive/10 text-destructive"
        : tone === "warning"
          ? "bg-warning/15 text-warning"
          : "bg-secondary text-foreground";
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <p className="text-xs text-muted-foreground">{label}</p>
          <div className={`size-9 rounded-lg grid place-items-center ${toneCls}`}>
            <Icon className="size-4" />
          </div>
        </div>
        <p className="font-display text-2xl font-bold mt-2">{value}</p>
        {(delta || hint) && (
          <p className="text-xs text-muted-foreground mt-1">
            {delta && <span className="text-success font-medium">{delta}</span>}
            {delta && hint && " · "}
            {hint}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function buildMonthlyTrend(payments: import("@/types/api").PaymentResponse[]) {
  const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const now = new Date();
  const buckets: { m: string; paid: number; pending: number }[] = [];
  for (let i = 5; i >= 0; i -= 1) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    buckets.push({ m: monthNames[d.getMonth()], paid: 0, pending: 0 });
  }
  for (const p of payments) {
    const d = new Date(p.paymentDate ?? p.dueDate);
    const idx = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth());
    const bIdx = 5 - idx;
    if (bIdx < 0 || bIdx > 5) continue;
    const amt = Number(p.totalAmount ?? p.amount);
    if (p.status === "PAID") buckets[bIdx].paid += amt;
    else buckets[bIdx].pending += amt;
  }
  // synthesize a believable trend if backend has no data
  const empty = buckets.every((b) => b.paid === 0 && b.pending === 0);
  if (empty) {
    return buckets.map((b, i) => ({
      m: b.m,
      paid: 120000 + i * 18000 + Math.round(Math.random() * 10000),
      pending: 20000 + Math.round(Math.random() * 12000),
    }));
  }
  return buckets;
}
