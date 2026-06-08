import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import {
  Building2,
  Home,
  TrendingUp,
  Wrench,
  IndianRupee,
  ArrowRight,
  AlertCircle,
  Trophy,
  PieChart as PieChartIcon,
} from "lucide-react";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
} from "recharts";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { maintenanceApi } from "@/lib/api/maintenance";
import { authApi } from "@/lib/api/auth";
import { claimsApi } from "@/lib/api/claims";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse } from "@/types/api";

export function OwnerDashboard() {
  const { authUserId, userName } = useAuthStore();

  // refetchOnWindowFocus + short staleTime so the KPIs stay live —
  // an owner who flips between Tenants / Payments / Overview tabs
  // sees the same number on each, instead of a 5-min-stale cache
  // on whichever page they hit first. Trade-off: a few extra
  // fetches; payments.byOwner is sub-100ms so it's invisible.
  //
  // refetchInterval of 30s also picks up tenant-side activity (a rent
  // payment just landed, a maintenance request was raised) without the
  // owner needing to focus the tab. Without this the dashboard tile
  // shows ₹0 for up to 5 minutes after a tenant successfully pays.
  // 30s is the sweet spot — fast enough to feel live, slow enough to
  // keep three small queries off the critical path.
  const FRESH = {
    refetchOnWindowFocus: true,
    staleTime: 15_000,
    refetchInterval: 30_000,
  };

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
    ...FRESH,
  });

  const paymentsQ = useQuery({
    queryKey: ["owner-payments", authUserId],
    queryFn: () => paymentsApi.byOwner(authUserId!),
    enabled: !!authUserId,
    ...FRESH,
  });

  const maintQ = useQuery({
    queryKey: ["owner-maintenance", authUserId],
    queryFn: () => maintenanceApi.byOwner(authUserId!),
    enabled: !!authUserId,
    ...FRESH,
  });

  // Tenant names for the "Top tenants" widget — without this we'd
  // be showing raw UUIDs which read as opaque data to an owner who
  // thinks in "Aanya, Rahul, Sushma". Cached 5 min — tenant names
  // don't change that often, and the same query powers admin /users
  // so a navigation there reuses the cache for free.
  const tenantsQ = useQuery({
    queryKey: ["admin", "users-tenant"],
    queryFn: () => authApi.byRole("TENANT"),
    staleTime: 5 * 60_000,
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

  // "Outstanding" = everything still owed, regardless of whether
  // the due-date has passed. Matches what the Tenants list and
  // /owner/payments compute, so the same ₹15,000 appears as the same
  // number on every owner-side page. Splitting into overdue (past-
  // due) vs upcoming (not yet due) so the KPI tile can call out the
  // urgent slice without hiding the rest.
  const overdueAmount = payments
    .filter((p) => p.status === "OVERDUE")
    .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);
  const upcomingAmount = payments
    .filter((p) => p.status === "PENDING")
    .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);
  const outstandingAmount = overdueAmount + upcomingAmount;

  const openMaint = (maintQ.data ?? []).filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  ).length;

  /* ── Occupancy across all of this owner's buildings ──
   * Aggregate occupiedFlatsCount / activeFlatsCount across every
   * building. Renders as a donut + center % so the owner can see
   * portfolio-level occupancy without clicking into Analytics. */
  const occupancy = useMemo(() => {
    const buildings = buildingsQ.data ?? [];
    let occupied = 0;
    let total = 0;
    for (const b of buildings) {
      const flats = b.activeFlatsCount ?? b.buildingTotalFlats ?? 0;
      total += flats;
      occupied += b.occupiedFlatsCount ?? 0;
    }
    const vacant = Math.max(0, total - occupied);
    const pct = total > 0 ? Math.round((occupied / total) * 100) : 0;
    return { occupied, vacant, total, pct };
  }, [buildingsQ.data]);

  /* ── Pending-payment aging buckets ──
   * For every PENDING / OVERDUE row, compute "days past due" from
   * the dueDate. Bucket into 0-30 / 31-60 / 61-90 / 90+ so the owner
   * can see how stale the unpaid pile is at a glance. The standard
   * landlord chase-cadence: anything >30 days needs a personal call;
   * anything >90 days probably needs a lawyer. */
  const agingBuckets = useMemo(() => buildAgingBuckets(payments), [payments]);

  /* ── Top-paying tenants (lifetime PAID) ──
   * Group settled payments by tenantId, sum totalAmount, sort desc,
   * take top 5. Resolves tenant names via the tenants query. */
  const topTenants = useMemo(
    () => buildTopTenants(payments, tenantsQ.data ?? []),
    [payments, tenantsQ.data],
  );

  const trend = buildMonthlyTrend(payments);

  // Resolve flatId UUIDs to readable flat numbers for the Recent activity
  // strip + the maintenance queue. 60 s cache — single batched fetch.
  const recentPayments = (paymentsQ.data ?? []).slice(0, 5);
  const openMaintTickets = (maintQ.data ?? [])
    .filter((r) => r.status !== "CLOSED" && r.status !== "RESOLVED")
    .slice(0, 4);
  const flatLookup = useFlatLookup([
    ...recentPayments.map((p) => p.flatId),
    ...openMaintTickets.map((r) => r.flatId),
  ]);

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

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5 mb-6">
        {/* "Collected this month" — sum of PAID payments whose
            paymentDate falls inside the current calendar month. No
            month-over-month delta (the old hard-coded "+12%" was a
            placeholder that became misleading once the amount was
            ₹0 — fake growth on no revenue). */}
        <KpiCard
          icon={IndianRupee}
          label="Collected this month"
          value={formatINR(paidThisMonth)}
          tone="primary"
        />
        {/* Outstanding = PENDING + OVERDUE — matches the Tenants list
            and /owner/payments. The hint line surfaces the
            already-past-due slice in red so the owner can see at a
            glance whether to chase now or just wait for the due date. */}
        <KpiCard
          icon={TrendingUp}
          label="Total Dues"
          value={formatINR(outstandingAmount)}
          tone={overdueAmount > 0 ? "destructive" : "warning"}
          hint={
            outstandingAmount === 0
              ? "All clear"
              : overdueAmount > 0
                ? `${formatINR(overdueAmount)} overdue · ${formatINR(upcomingAmount)} upcoming`
                : `${formatINR(upcomingAmount)} upcoming · 0 overdue`
          }
        />
        {/* Occupancy % — moved out of /analytics so it sits next to
            the financials. Hint shows the absolute count so 67% reads
            as "8 of 12 flats" not just a hollow percentage. */}
        <KpiCard
          icon={Home}
          label="Occupancy"
          value={occupancy.total > 0 ? `${occupancy.pct}%` : "—"}
          hint={
            occupancy.total === 0
              ? "Add flats to track"
              : `${occupancy.occupied} of ${occupancy.total} flats`
          }
          tone={
            occupancy.pct >= 80
              ? "primary"
              : occupancy.pct >= 50
                ? "warning"
                : "destructive"
          }
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

      {/* Self-service membership claims awaiting this owner's decision.
          The widget hides itself when there's nothing to show, so it
          doesn't take up real estate on a clean day. Inserted between
          KPIs and the charts because owners typically scan the KPIs
          first, then act on alerts; the claims card sits in their
          natural eye-path. */}
      <PendingClaimsWidget />

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
            {recentPayments.map((p) => (
              <div
                key={p.id}
                className="flex items-center justify-between text-sm"
              >
                <div className="min-w-0">
                  <p className="font-medium truncate">
                    Flat {flatLookup.nameOf(p.flatId)}
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

      {/* Financial dashboard widgets — aging buckets + top tenants +
          occupancy donut. Sits BETWEEN the trend chart and the
          buildings/maintenance lists so the analytical content gets
          higher visual priority. Three-column grid on lg so each card
          stays readable on a 1280px laptop. */}
      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        {/* Aging buckets — how stale is the unpaid pile? */}
        <Card>
          <div className="p-6 flex items-center justify-between">
            <div>
              <h2 className="font-display font-semibold text-lg">
                Overdue aging
              </h2>
              <p className="text-xs text-muted-foreground">
                Pending + overdue, by days past due
              </p>
            </div>
            <div className="size-9 rounded-lg bg-warning/15 text-warning grid place-items-center">
              <AlertCircle className="size-4" />
            </div>
          </div>
          <div className="px-6 pb-6 space-y-3">
            {agingBuckets.totalAmount === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-6">
                Nothing overdue. Owners' dream.
              </p>
            ) : (
              agingBuckets.rows.map((b) => (
                <div key={b.label}>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">{b.label}</span>
                    <span className="font-medium">
                      {formatINR(b.amount)}
                      {b.count > 0 && (
                        <span className="text-xs text-muted-foreground ml-1.5">
                          ({b.count})
                        </span>
                      )}
                    </span>
                  </div>
                  {/* Stacked horizontal bar — width proportional to the
                      bucket's share of the total overdue, so the
                      "90+ days" red strip visibly dominates if the
                      owner has a real chase problem. */}
                  <div className="mt-1.5 h-2 rounded-full bg-secondary overflow-hidden">
                    <div
                      className={
                        b.severity === "destructive"
                          ? "h-full bg-destructive"
                          : b.severity === "warning"
                            ? "h-full bg-warning"
                            : "h-full bg-primary"
                      }
                      style={{
                        width: `${
                          agingBuckets.totalAmount > 0
                            ? Math.max(2, (b.amount / agingBuckets.totalAmount) * 100)
                            : 0
                        }%`,
                      }}
                    />
                  </div>
                </div>
              ))
            )}
          </div>
        </Card>

        {/* Top-paying tenants — leaderboard style, lifetime PAID */}
        <Card>
          <div className="p-6 flex items-center justify-between">
            <div>
              <h2 className="font-display font-semibold text-lg">
                Top tenants
              </h2>
              <p className="text-xs text-muted-foreground">
                Lifetime PAID rent
              </p>
            </div>
            <div className="size-9 rounded-lg bg-primary/10 text-primary grid place-items-center">
              <Trophy className="size-4" />
            </div>
          </div>
          <div className="px-6 pb-6 space-y-3">
            {topTenants.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-6">
                No paid rent yet.
              </p>
            ) : (
              topTenants.map((t, i) => (
                <div
                  key={t.tenantId}
                  className="flex items-center gap-3"
                >
                  {/* Rank chip — gold/silver/bronze hint for top 3,
                      neutral for 4-5. Subtle but adds a tiny dopamine
                      hit for "who paid the most this period". */}
                  <div
                    className={
                      "size-7 rounded-full grid place-items-center text-xs font-semibold shrink-0 " +
                      (i === 0
                        ? "bg-amber-400/20 text-amber-700"
                        : i === 1
                          ? "bg-slate-400/20 text-slate-700"
                          : i === 2
                            ? "bg-orange-400/20 text-orange-700"
                            : "bg-secondary text-muted-foreground")
                    }
                  >
                    {i + 1}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">
                      {t.name}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {t.paymentCount} payment{t.paymentCount === 1 ? "" : "s"}
                    </p>
                  </div>
                  <p className="text-sm font-semibold">
                    {formatINR(t.total)}
                  </p>
                </div>
              ))
            )}
          </div>
        </Card>

        {/* Occupancy donut — a single visual that says "% of flats
            paying rent". Reuses the per-building counts in
            buildingsQ.data so it doesn't fire an extra request. */}
        <Card>
          <div className="p-6 flex items-center justify-between">
            <div>
              <h2 className="font-display font-semibold text-lg">
                Occupancy
              </h2>
              <p className="text-xs text-muted-foreground">
                Across all your buildings
              </p>
            </div>
            <div className="size-9 rounded-lg bg-primary/10 text-primary grid place-items-center">
              <PieChartIcon className="size-4" />
            </div>
          </div>
          <div className="px-6 pb-6">
            {occupancy.total === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-12">
                Add a flat to track occupancy.
              </p>
            ) : (
              <div className="relative h-44">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={[
                        { name: "Occupied", value: occupancy.occupied },
                        { name: "Vacant", value: occupancy.vacant },
                      ]}
                      dataKey="value"
                      innerRadius={50}
                      outerRadius={70}
                      paddingAngle={2}
                      strokeWidth={0}
                    >
                      <Cell fill="hsl(244 75% 59%)" />
                      <Cell fill="hsl(220 13% 91%)" />
                    </Pie>
                    <Tooltip
                      formatter={(v: number) => `${v} flats`}
                      contentStyle={{
                        borderRadius: 12,
                        border: "1px solid hsl(220 13% 91%)",
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
                {/* Centre overlay — the headline % over the ring. */}
                <div className="absolute inset-0 grid place-items-center pointer-events-none">
                  <div className="text-center">
                    <p className="font-display text-3xl font-bold">
                      {occupancy.pct}%
                    </p>
                    <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                      occupied
                    </p>
                  </div>
                </div>
              </div>
            )}
            <div className="mt-3 flex items-center justify-center gap-4 text-xs">
              <span className="flex items-center gap-1.5">
                <span className="size-2 rounded-full bg-primary" />
                Occupied · {occupancy.occupied}
              </span>
              <span className="flex items-center gap-1.5">
                <span className="size-2 rounded-full bg-border" />
                Vacant · {occupancy.vacant}
              </span>
            </div>
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
            {openMaintTickets.map((r) => (
                <div
                  key={r.id}
                  className="flex items-start justify-between p-3 rounded-lg hover:bg-secondary/50 transition-colors"
                >
                  <div className="min-w-0">
                    <p className="font-medium text-sm truncate">{r.title}</p>
                    <p className="text-xs text-muted-foreground">
                      Flat {flatLookup.nameOf(r.flatId)} ·{" "}
                      {r.category ?? r.complaintCategory ?? "—"}
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

function buildMonthlyTrend(payments: PaymentResponse[]) {
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
  // Returns the real (potentially-empty) trend. An earlier version
  // synthesized fake "believable" numbers when the backend had no
  // data — same dishonesty as fake testimonials and just as quickly
  // erodes user trust. If a new owner sees the chart at zero, that's
  // the truth; they'll see it grow as they assign tenants + collect
  // first rents.
  return buckets;
}

/**
 * Build aging buckets from PENDING + OVERDUE payments. Buckets in
 * the standard landlord chase-cadence — anything past 90 days is a
 * red-flag bucket that probably needs legal action, not another
 * reminder text. Severity drives the bar colour: 0-30 stays primary
 * (just due), 31-60 warns amber, 60+ goes destructive red.
 */
function buildAgingBuckets(payments: PaymentResponse[]) {
  const now = Date.now();
  const oneDayMs = 24 * 60 * 60 * 1000;
  type Bucket = {
    label: string;
    minDays: number;
    maxDays: number;
    amount: number;
    count: number;
    severity: "primary" | "warning" | "destructive";
  };
  const rows: Bucket[] = [
    { label: "Due now (0-30 days)", minDays: -Infinity, maxDays: 30, amount: 0, count: 0, severity: "primary" },
    { label: "31-60 days overdue", minDays: 31, maxDays: 60, amount: 0, count: 0, severity: "warning" },
    { label: "61-90 days overdue", minDays: 61, maxDays: 90, amount: 0, count: 0, severity: "warning" },
    { label: "90+ days overdue", minDays: 91, maxDays: Infinity, amount: 0, count: 0, severity: "destructive" },
  ];

  for (const p of payments) {
    if (p.status !== "PENDING" && p.status !== "OVERDUE") continue;
    if (!p.dueDate) continue;
    const dueMs = new Date(p.dueDate).getTime();
    const daysPastDue = Math.floor((now - dueMs) / oneDayMs);
    const amt = Number(p.totalAmount ?? p.amount);
    const target = rows.find(
      (r) => daysPastDue >= r.minDays && daysPastDue <= r.maxDays,
    );
    if (target) {
      target.amount += amt;
      target.count += 1;
    }
  }
  const totalAmount = rows.reduce((s, r) => s + r.amount, 0);
  return { rows, totalAmount };
}

/**
 * Group PAID payments by tenantId, sum totalAmount, take the top 5.
 * Resolves tenant names via the authApi.byRole("TENANT") response
 * (the same list admin /users uses). Falls back to a short raw id
 * fragment when a tenant row isn't on the lookup yet (legacy data,
 * deleted user, etc).
 */
function buildTopTenants(
  payments: PaymentResponse[],
  tenants: Array<{ id: string | number; userName: string }>,
) {
  const tenantNameById = new Map<string, string>();
  for (const t of tenants) {
    tenantNameById.set(String(t.id), t.userName);
  }
  const totals = new Map<string, { total: number; count: number }>();
  for (const p of payments) {
    if (p.status !== "PAID") continue;
    if (!p.tenantId) continue;
    const key = String(p.tenantId);
    const prev = totals.get(key) ?? { total: 0, count: 0 };
    prev.total += Number(p.totalAmount ?? p.amount);
    prev.count += 1;
    totals.set(key, prev);
  }
  return [...totals.entries()]
    .map(([tenantId, agg]) => ({
      tenantId,
      name: tenantNameById.get(tenantId) ?? `Tenant ${tenantId.slice(0, 6)}`,
      total: agg.total,
      paymentCount: agg.count,
    }))
    .sort((a, b) => b.total - a.total)
    .slice(0, 5);
}

/**
 * Self-service membership claims widget. Polls every 30s while the
 * dashboard is open so a freshly-submitted claim shows up without a
 * manual refresh. Renders nothing when there are no pending claims —
 * the owner's dashboard stays clean on quiet days.
 *
 * <p>Each row offers Approve / Reject. Approve flips the row's status
 * server-side and (for MAINTAINER claims) triggers an auth-service
 * role bump + a society-config swap; the owner sees a toast confirming
 * the change. The widget refetches after the mutation so the row drops
 * out of the pending list immediately.
 */
function PendingClaimsWidget() {
  const qc = useQueryClient();
  const { toast: tst } = useToast();
  const pendingQ = useQuery({
    queryKey: ["my-pending-claims"],
    queryFn: () => claimsApi.pendingForOwner(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  const approveMut = useMutation({
    mutationFn: (claimId: string) => claimsApi.approve(claimId),
    onSuccess: (claim) => {
      qc.invalidateQueries({ queryKey: ["my-pending-claims"] });
      const titleByRole = {
        MAINTAINER: "Maintainer approved.",
        RESIDENT: "Resident approved.",
        FLAT_OWNER: "Flat owner approved.",
      } as const;
      const descByRole = {
        MAINTAINER: `${claim.applicantName ?? "The applicant"} can now manage the society. They'll need to sign out and back in to see the maintainer dashboard.`,
        RESIDENT: `${claim.applicantName ?? "The applicant"} is now attached to flat ${claim.claimedFlatNumber ?? "their flat"}.`,
        FLAT_OWNER: `${claim.applicantName ?? "The applicant"} is now the owner of flat ${claim.claimedFlatNumber ?? "their flat"}. Rent for that flat now goes to them.`,
      } as const;
      tst({
        title: titleByRole[claim.requestedRole] ?? "Approved.",
        description: descByRole[claim.requestedRole] ?? "",
      });
    },
    onError: (e) => {
      tst({
        title: "Couldn't approve",
        description: extractErrorMessage(e),
        variant: "destructive",
      });
    },
  });

  const rejectMut = useMutation({
    mutationFn: (claimId: string) => claimsApi.reject(claimId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-pending-claims"] });
      tst({ title: "Request rejected." });
    },
    onError: (e) => {
      tst({
        title: "Couldn't reject",
        description: extractErrorMessage(e),
        variant: "destructive",
      });
    },
  });

  const pending = pendingQ.data ?? [];
  if (!pendingQ.isLoading && pending.length === 0) return null;

  return (
    <Card className="mb-6 border-warning/40">
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3 mb-3">
          <div>
            <h3 className="font-display font-semibold text-base">
              Pending requests
            </h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              People who registered themselves as residents or maintainers
              for your buildings — approve to grant access.
            </p>
          </div>
          {pending.length > 0 && (
            <Badge variant="secondary">{pending.length}</Badge>
          )}
        </div>

        {pendingQ.isLoading ? (
          <Skeleton className="h-20 rounded-md" />
        ) : (
          <div className="space-y-2">
            {pending.map((c) => (
              <div
                key={c.id}
                className="flex flex-wrap items-start gap-3 p-3 rounded-lg border border-border/60 bg-secondary/30"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <Badge
                      variant={
                        c.requestedRole === "MAINTAINER"
                          ? "default"
                          : "secondary"
                      }
                      className="text-[10px]"
                    >
                      {c.requestedRole === "MAINTAINER"
                        ? "Maintainer"
                        : c.requestedRole === "FLAT_OWNER"
                          ? "Flat owner"
                          : "Resident"}
                    </Badge>
                    <span className="font-semibold text-sm truncate">
                      {c.applicantName ?? c.applicantEmail ?? "Applicant"}
                    </span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    <span className="font-medium">
                      {c.buildingName ?? "Building"}
                    </span>
                    {c.claimedFlatNumber && ` · flat ${c.claimedFlatNumber}`}
                    {c.applicantEmail && (
                      <>
                        {" · "}
                        <span className="font-mono">{c.applicantEmail}</span>
                      </>
                    )}
                  </p>
                  {c.applicantNote && (
                    <p className="text-xs italic text-muted-foreground mt-1.5">
                      &ldquo;{c.applicantNote}&rdquo;
                    </p>
                  )}
                </div>
                <div className="flex gap-2 shrink-0">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={
                      rejectMut.isPending && rejectMut.variables === c.id
                    }
                    onClick={() => {
                      if (
                        confirm(
                          `Reject ${c.applicantName ?? "this request"}? They can submit again later.`,
                        )
                      ) {
                        rejectMut.mutate(c.id);
                      }
                    }}
                  >
                    Reject
                  </Button>
                  <Button
                    variant="gradient"
                    size="sm"
                    disabled={
                      approveMut.isPending && approveMut.variables === c.id
                    }
                    onClick={() => {
                      const who = c.applicantName ?? "this person";
                      const building = c.buildingName ?? "the building";
                      const flat = c.claimedFlatNumber ?? "?";
                      const msgByRole = {
                        MAINTAINER: `Approve ${who} as maintainer of ${building}?\n\nThis will REPLACE any existing maintainer for this building.`,
                        RESIDENT: `Approve ${who} as the resident of flat ${flat} in ${building}?`,
                        FLAT_OWNER: `Mark ${who} as the OWNER of flat ${flat} in ${building}?\n\nRent for this flat will route to them from now on, and they'll be on the lease (Party A) instead of you.`,
                      } as const;
                      const msg =
                        msgByRole[c.requestedRole] ??
                        `Approve ${who}'s request?`;
                      if (confirm(msg)) {
                        approveMut.mutate(c.id);
                      }
                    }}
                  >
                    Approve
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
