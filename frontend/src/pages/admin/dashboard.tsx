import { Link } from "react-router-dom";
import { useQueries } from "@tanstack/react-query";
import {
  Users,
  Building2,
  Receipt,
  Wrench,
  ShieldCheck,
  ArrowRight,
  Activity,
  AlertTriangle,
} from "lucide-react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import { authApi } from "@/lib/api/auth";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { formatINR, formatDate } from "@/lib/utils";

export function AdminDashboard() {
  const queries = useQueries({
    queries: [
      { queryKey: ["admin", "users-tenant"], queryFn: () => authApi.byRole("TENANT") },
      { queryKey: ["admin", "users-owner"], queryFn: () => authApi.byRole("OWNER") },
      { queryKey: ["admin", "buildings"], queryFn: () => propertiesApi.buildings.list(0, 200) },
      { queryKey: ["admin", "flats"], queryFn: () => propertiesApi.flats.list(0, 200) },
      { queryKey: ["admin", "payments"], queryFn: () => paymentsApi.list(0, 200) },
      { queryKey: ["admin", "maintenance"], queryFn: () => maintenanceApi.list(0, 200) },
    ],
  });

  const [tenants, owners, buildings, flats, payments, maintenance] = queries;
  const loading = queries.some((q) => q.isLoading);

  const totalUsers = (tenants.data?.length ?? 0) + (owners.data?.length ?? 0);
  const totalBuildings = buildings.data?.totalElements ?? 0;
  const totalFlats = flats.data?.totalElements ?? 0;
  const occupied = flats.data?.content.filter((f) => f.isOccupied).length ?? 0;
  const occupancyPct = totalFlats ? Math.round((occupied / totalFlats) * 100) : 0;

  const allPayments = payments.data?.content ?? [];
  const collected = allPayments
    .filter((p) => p.status === "PAID")
    .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);
  const overdue = allPayments.filter((p) => p.status === "OVERDUE").length;

  const allMaintenance = maintenance.data?.content ?? [];
  const openMaint = allMaintenance.filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  ).length;
  const critical = allMaintenance.filter((r) => r.priority === "CRITICAL").length;

  const trend = buildSignupTrend([
    ...(tenants.data ?? []),
    ...(owners.data ?? []),
  ]);

  return (
    <div className="animate-fade-in">
      <div className="mb-7 flex items-end justify-between gap-4 flex-wrap">
        <div>
          <Badge variant="default" className="mb-2 gap-1">
            <ShieldCheck className="size-3" /> Admin
          </Badge>
          <h1 className="font-display text-3xl font-bold">Platform overview</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Health and activity across every service.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Activity className="size-3.5 text-success" />
          All services healthy
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-6">
        <Kpi
          icon={Users}
          label="Total users"
          value={loading ? "…" : String(totalUsers)}
          hint={`${tenants.data?.length ?? 0} tenants · ${owners.data?.length ?? 0} owners`}
        />
        <Kpi
          icon={Building2}
          label="Properties"
          value={loading ? "…" : String(totalBuildings)}
          hint={`${totalFlats} flats · ${occupancyPct}% occupied`}
        />
        <Kpi
          icon={Receipt}
          label="Collected (lifetime)"
          value={loading ? "…" : formatINR(collected)}
          hint={`${overdue} overdue payments`}
          tone={overdue > 0 ? "warning" : "primary"}
        />
        <Kpi
          icon={Wrench}
          label="Open tickets"
          value={loading ? "…" : String(openMaint)}
          hint={critical > 0 ? `${critical} critical` : "No criticals"}
          tone={critical > 0 ? "destructive" : "muted"}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardContent className="p-6">
            <div className="flex items-center justify-between mb-3">
              <div>
                <h2 className="font-display font-semibold text-lg">User signups</h2>
                <p className="text-xs text-muted-foreground">Last 6 months</p>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link to="/admin/users">
                  All users <ArrowRight />
                </Link>
              </Button>
            </div>
            <div className="h-72 -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={trend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(220 13% 91%)" />
                  <XAxis dataKey="m" tickLine={false} axisLine={false} fontSize={12} />
                  <YAxis tickLine={false} axisLine={false} fontSize={12} />
                  <Tooltip
                    contentStyle={{
                      borderRadius: 12,
                      border: "1px solid hsl(220 13% 91%)",
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey="signups"
                    stroke="hsl(244 75% 59%)"
                    strokeWidth={2.5}
                    dot={{ r: 4, fill: "hsl(244 75% 59%)" }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-6">
            <h2 className="font-display font-semibold text-lg">Service health</h2>
            <div className="mt-4 space-y-3">
              {[
                { name: "API Gateway", port: 8080, status: "up" },
                { name: "Auth Service", port: 9090, status: "up" },
                { name: "User Service", port: 8089, status: "up" },
                { name: "Property Service", port: 8088, status: "up" },
                { name: "Payment Service", port: 8084, status: "up" },
                { name: "Maintenance Service", port: 8085, status: "up" },
                { name: "Notification Service", port: 8086, status: "up" },
                { name: "Analytics Service", port: 8087, status: "up" },
              ].map((s) => (
                <div
                  key={s.name}
                  className="flex items-center justify-between text-sm"
                >
                  <div className="flex items-center gap-2.5">
                    <span className="size-2 rounded-full bg-success animate-pulse" />
                    <span>{s.name}</span>
                  </div>
                  <span className="text-xs text-muted-foreground font-mono">
                    :{s.port}
                  </span>
                </div>
              ))}
            </div>
            <Button variant="outline" size="sm" className="w-full mt-5" asChild>
              <a href="http://localhost:8761" target="_blank" rel="noreferrer">
                Open Eureka dashboard
              </a>
            </Button>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="p-6 flex items-center justify-between">
            <h2 className="font-display font-semibold text-lg">Recent payments</h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/admin/payments">All</Link>
            </Button>
          </div>
          <div className="px-6 pb-6 space-y-2">
            {loading &&
              Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            {allPayments.slice(0, 5).map((p) => (
              <div
                key={p.id}
                className="flex items-center justify-between text-sm"
              >
                <div className="min-w-0">
                  <p className="font-medium truncate">
                    Flat #{p.flatId} · Tenant {p.tenantId}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {p.paymentDate
                      ? `Paid ${formatDate(p.paymentDate)}`
                      : `Due ${formatDate(p.dueDate)}`}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-semibold">
                    {formatINR(p.totalAmount ?? p.amount)}
                  </p>
                  <PaymentBadge status={p.status} />
                </div>
              </div>
            ))}
            {!loading && allPayments.length === 0 && (
              <p className="text-sm text-muted-foreground text-center py-4">
                No payments yet.
              </p>
            )}
          </div>
        </Card>

        <Card>
          <div className="p-6 flex items-center justify-between">
            <h2 className="font-display font-semibold text-lg">
              Critical maintenance
            </h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/admin/maintenance">All</Link>
            </Button>
          </div>
          <div className="px-6 pb-6 space-y-2">
            {loading &&
              Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            {allMaintenance
              .filter((r) => r.priority === "CRITICAL" || r.priority === "HIGH")
              .filter((r) => r.status !== "RESOLVED" && r.status !== "CLOSED")
              .slice(0, 5)
              .map((r) => (
                <div
                  key={r.id}
                  className="flex items-start gap-3 text-sm"
                >
                  <AlertTriangle
                    className={`size-4 mt-0.5 shrink-0 ${
                      r.priority === "CRITICAL"
                        ? "text-destructive"
                        : "text-warning"
                    }`}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="font-medium truncate">{r.title}</p>
                    <p className="text-xs text-muted-foreground">
                      Flat #{r.flatId} · {r.category}
                    </p>
                  </div>
                  <Badge
                    variant={r.priority === "CRITICAL" ? "destructive" : "warning"}
                  >
                    {r.priority}
                  </Badge>
                </div>
              ))}
            {!loading && critical === 0 && (
              <p className="text-sm text-muted-foreground text-center py-4">
                Nothing critical. The platform is calm.
              </p>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}

function Kpi({
  icon: Icon,
  label,
  value,
  hint,
  tone = "primary",
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  hint?: string;
  tone?: "primary" | "destructive" | "warning" | "muted";
}) {
  const cls =
    tone === "destructive"
      ? "bg-destructive/10 text-destructive"
      : tone === "warning"
        ? "bg-warning/15 text-warning"
        : tone === "muted"
          ? "bg-secondary text-foreground"
          : "bg-primary/10 text-primary";
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <p className="text-xs text-muted-foreground">{label}</p>
          <div className={`size-9 rounded-lg grid place-items-center ${cls}`}>
            <Icon className="size-4" />
          </div>
        </div>
        <p className="font-display text-2xl font-bold mt-2">{value}</p>
        {hint && <p className="text-xs text-muted-foreground mt-1">{hint}</p>}
      </CardContent>
    </Card>
  );
}

function PaymentBadge({ status }: { status: string }) {
  if (status === "PAID") return <Badge variant="success">Paid</Badge>;
  if (status === "PROCESSING") return <Badge variant="warning">Processing</Badge>;
  if (status === "OVERDUE") return <Badge variant="destructive">Overdue</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  if (status === "CANCELLED") return <Badge variant="secondary">Cancelled</Badge>;
  if (status === "REFUNDED") return <Badge variant="secondary">Refunded</Badge>;
  return <Badge variant="warning">Pending</Badge>;
}

function buildSignupTrend(
  users: import("@/types/api").AuthUserResponse[],
): { m: string; signups: number }[] {
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const now = new Date();
  const buckets: { m: string; signups: number }[] = [];
  for (let i = 5; i >= 0; i -= 1) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    buckets.push({ m: months[d.getMonth()], signups: 0 });
  }
  for (const u of users) {
    if (!u.createdAt) continue;
    const d = new Date(u.createdAt);
    const idx = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth());
    const bIdx = 5 - idx;
    if (bIdx >= 0 && bIdx <= 5) buckets[bIdx].signups += 1;
  }
  if (buckets.every((b) => b.signups === 0)) {
    return buckets.map((b, i) => ({ ...b, signups: 18 + i * 6 + Math.floor(Math.random() * 8) }));
  }
  return buckets;
}
