import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQueries, useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Building2,
  Calendar,
  CheckCircle2,
  FileText,
  Receipt,
  TrendingUp,
  Wallet,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { CollapsibleSection } from "@/components/ui/collapsible-section";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/components/layout/page-header";
import { AnnouncementsPanel } from "@/components/society/announcements-panel";
import { SocietyLedgerView } from "@/components/society/ledger-view";
import { formatINR } from "@/lib/utils";
import type {
  FlatChargeCategory,
  FlatMaintenanceRow,
  SocietyConfig,
} from "@/types/api";

const CHARGE_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

const STATUS_TONES: Record<string, string> = {
  DUE: "bg-warning/20 text-warning",
  OVERDUE: "bg-destructive/20 text-destructive",
  PAID: "bg-success/20 text-success",
  WAIVED: "bg-secondary text-secondary-foreground",
};

const currentMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

/**
 * Tenant-side view of their building's society.
 *
 * <p>Two tabs, one per resident-facing intent:
 * <ol>
 *   <li><b>Your maintenance</b> — the resident's own bills for the
 *       month, with a Pay-Now button per row that opens a UPI QR.
 *       Personal money view.</li>
 *   <li><b>Maintenance details</b> — the full transparency dashboard
 *       (KPIs, spend-by-category chart, expense entries table,
 *       per-flat bills). Same visualisation the shareable public link
 *       shows — surfacing it in-app removes the need for the
 *       maintainer to WhatsApp a link every month.</li>
 * </ol>
 *
 * <p>Announcements sit above the tabs so a resident sees a notice
 * whether they're on the money-tab or the transparency-tab.
 */
export function TenantSocietyPage() {
  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Income & Expenses"
        description="Your monthly bills + a transparent record of common-area income and expenses."
      />

      {configQ.isLoading ? (
        <Skeleton className="h-64 rounded-2xl" />
      ) : !configQ.data ? (
        <EmptyState
          variant="info"
          icon={Building2}
          title="No society set up yet"
          description="The owner hasn't enabled common-area maintenance tracking for your building. Ask them to set it up to see a transparent expense ledger here."
        />
      ) : (
        <ResidentSociety config={configQ.data} />
      )}
    </div>
  );
}

function ResidentSociety({ config }: { config: SocietyConfig }) {
  const [month, setMonth] = useState(currentMonth());

  return (
    <>
      <p className="text-sm text-muted-foreground mb-4">
        Viewing{" "}
        <span className="font-semibold text-foreground">
          {config.societyDisplayName ?? "your society"}
        </span>{" "}
        · dues ₹{config.defaultPerFlatAmount}/flat by day{" "}
        {config.monthlyDueDay} each month.
      </p>

      {/* Notice board — same for both tabs. */}
      <AnnouncementsPanel buildingId={config.buildingId} canPost={false} />

      {/* Distinct per-tab active colours so a resident sees at a
          glance which view they're on — green for "your money in",
          violet for "the shared transparency dashboard". The
          overrides are local (no changes to the shared Tabs
          primitive that other pages rely on). */}
      <Tabs defaultValue="mine" className="mt-2">
        <TabsList className="w-full sm:w-auto h-auto gap-2 p-1.5 rounded-2xl">
          <TabsTrigger
            value="mine"
            className={
              "gap-2.5 flex-1 sm:flex-none px-5 py-3 text-base rounded-xl " +
              "data-[state=active]:bg-gradient-to-br " +
              "data-[state=active]:from-emerald-500 " +
              "data-[state=active]:to-emerald-600 " +
              "data-[state=active]:text-white " +
              "data-[state=active]:shadow-lift"
            }
          >
            <Receipt className="size-5" />
            Your maintenance
          </TabsTrigger>
          <TabsTrigger
            value="details"
            className={
              "gap-2.5 flex-1 sm:flex-none px-5 py-3 text-base rounded-xl " +
              "data-[state=active]:bg-gradient-to-br " +
              "data-[state=active]:from-violet-500 " +
              "data-[state=active]:to-purple-600 " +
              "data-[state=active]:text-white " +
              "data-[state=active]:shadow-lift"
            }
          >
            <FileText className="size-5" />
            Maintenance details
          </TabsTrigger>
        </TabsList>

        {/* ── Tab 1: personal bills + pay ─────────────────────────── */}
        <TabsContent value="mine" className="mt-5">
          <YourMaintenanceTab
            config={config}
            month={month}
            setMonth={setMonth}
          />
        </TabsContent>

        {/* ── Tab 2: shared transparency dashboard ────────────────── */}
        <TabsContent value="details" className="mt-5">
          <SocietyLedgerView
            fetchLedger={(m) => societyApi.ledger(config.buildingId, m)}
            cacheKey={`tenant-ledger-${config.buildingId}`}
          />
        </TabsContent>
      </Tabs>
    </>
  );
}

/**
 * The "Your maintenance" tab content — extracted so the Tabs render
 * cleanly. Same behaviour as the pre-tabs page: month picker at the
 * top, "no UPI configured" banner if the society hasn't hooked up a
 * collection VPA, then the tenant's own charges with a Pay button
 * per row.
 */
function YourMaintenanceTab({
  config,
  month,
  setMonth,
}: {
  config: SocietyConfig;
  month: string;
  setMonth: (m: string) => void;
}) {
  const myBillsQ = useQuery({
    queryKey: ["tenant-society-bills", config.buildingId, month],
    queryFn: () => societyApi.myBills(config.buildingId, month),
    staleTime: 15_000,
  });

  const totalDue = useMemo(() => {
    const rows = myBillsQ.data ?? [];
    return rows
      .filter((r) => r.status === "DUE" || r.status === "OVERDUE")
      .reduce((s, r) => s + r.monthAmount, 0);
  }, [myBillsQ.data]);

  return (
    <>
      <div className="flex items-center gap-3 mb-4">
        <Calendar className="size-4 text-muted-foreground" />
        <Input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value || currentMonth())}
          className="w-48"
        />
      </div>

      {!config.upiId && (
        <Card className="mb-4 border-warning/40 bg-warning/5">
          <CardContent className="p-4 flex items-start gap-3">
            <Wallet className="size-5 text-warning shrink-0 mt-0.5" />
            <div className="text-sm">
              <p className="font-semibold">Online payment not set up yet</p>
              <p className="text-muted-foreground mt-0.5">
                The maintainer hasn't added the society's UPI ID to the
                collection account. Once they do, your Pay button below
                will generate a UPI QR you can scan from any app. Until
                then, please pay them directly and ask them to mark the
                charge as paid.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Personal history strip — six-month bar chart of what the
          tenant has actually paid to the society, month-over-month.
          Sits BELOW the "UPI not set up" banner (so a resident sees
          the alert first) and ABOVE the bills table (so they can
          eyeball the trend before drilling into the line items).
          Auto-hides on brand-new residents (nothing paid yet) so
          we never render an empty axis. */}
      <MyMonthlySpendChart buildingId={config.buildingId} />

      <CollapsibleSection
        className="mb-4"
        title={`My charges — ${month}`}
        icon={Receipt}
        summary={
          myBillsQ.data?.length
            ? totalDue > 0
              ? `Total Dues ${formatINR(totalDue)}`
              : `${myBillsQ.data.length} charge${myBillsQ.data.length === 1 ? "" : "s"}`
            : "No bills"
        }
      >
        {myBillsQ.isLoading ? (
          <Skeleton className="h-24 rounded-xl" />
        ) : !myBillsQ.data?.length ? (
          <EmptyState
            variant="info"
            icon={Receipt}
            title="No bills posted for you this month yet"
            description="The maintainer hasn't entered any charges against your flat for this month. Check back later or message them if you think this is wrong."
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border/60">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="bg-secondary/40 border-b border-border/60">
                  <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    Category
                  </th>
                  <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground">
                    Description
                  </th>
                  <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    Status
                  </th>
                  <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    Amount
                  </th>
                  <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    {/* Pay-button column */}
                  </th>
                </tr>
              </thead>
              <tbody>
                {myBillsQ.data.map((row) => (
                  <ChargeRow
                    key={row.collectionId ?? row.category ?? "row"}
                    row={row}
                    config={config}
                  />
                ))}
              </tbody>
              {totalDue > 0 && (
                <tfoot>
                  <tr className="bg-primary/5 border-t-2 border-border/60 font-semibold">
                    <td
                      colSpan={3}
                      className="px-3 py-3 text-right text-sm uppercase tracking-wider text-muted-foreground"
                    >
                      Total Due
                    </td>
                    <td className="px-3 py-3 text-right whitespace-nowrap">
                      <span className="font-display text-base">
                        {formatINR(totalDue)}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-right whitespace-nowrap">
                      <Button asChild variant="gradient" size="sm">
                        <Link
                          to={`/app/society/pay-all/${config.buildingId}/${month}`}
                        >
                          Pay all · {formatINR(totalDue)}
                        </Link>
                      </Button>
                    </td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        )}
      </CollapsibleSection>
    </>
  );
}

/**
 * One charge row. Pay button on DUE/OVERDUE (destination page handles
 * the "no UPI configured" case); check mark + paid-on date on PAID.
 */
function ChargeRow({
  row,
  config,
}: {
  row: FlatMaintenanceRow;
  config: SocietyConfig;
}) {
  const label = row.category ? CHARGE_LABELS[row.category] : "Other";
  const tone = STATUS_TONES[row.status] ?? "bg-muted text-muted-foreground";
  const isPaid = row.status === "PAID";
  const canPay =
    (row.status === "DUE" || row.status === "OVERDUE") && row.collectionId;

  return (
    <tr className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20">
      <td className="px-3 py-2 align-top whitespace-nowrap">
        <Badge variant="secondary" className="text-[10px]">
          {label}
        </Badge>
      </td>
      <td className="px-3 py-2 align-top">
        {row.notes ? (
          <p className="text-xs text-muted-foreground italic line-clamp-2">
            {row.notes}
          </p>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
      <td className="px-3 py-2 align-top whitespace-nowrap">
        <span
          className={`rounded-full text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 ${tone}`}
        >
          {row.status}
        </span>
        {isPaid && row.paidOn && (
          <span className="block text-[10px] text-muted-foreground mt-0.5">
            on {row.paidOn}
          </span>
        )}
      </td>
      <td className="px-3 py-2 align-top text-right whitespace-nowrap">
        <span className="font-semibold font-display">
          {formatINR(row.monthAmount)}
        </span>
      </td>
      <td className="px-3 py-2 align-top text-right whitespace-nowrap">
        {canPay && (
          <Button asChild variant="gradient" size="sm">
            <Link
              to={`/app/society/pay/${config.buildingId}/${row.collectionId}`}
            >
              Pay {formatINR(row.monthAmount)}
            </Link>
          </Button>
        )}
        {isPaid && (
          <CheckCircle2
            className="size-5 text-success inline-block"
            aria-label="Paid"
          />
        )}
      </td>
    </tr>
  );
}

/**
 * Six-month bar chart of what THIS tenant has actually paid to
 * the society. Fetches per-month bills in parallel via
 * {@link useQueries} — cache keys match the ones the main table
 * uses so the current-month fetch dedupes for free. Sums PAID
 * rows only (ignoring DUE / WAIVED) so the chart answers "what
 * did I actually settle" not "what was billed".
 *
 * <p>Hides itself when the six-month window carries zero paid
 * amounts — a brand-new resident shouldn't see an empty axis.
 */
function MyMonthlySpendChart({ buildingId }: { buildingId: string }) {
  // Six months, newest first (matching lastNMonths' return).
  const months = useMemo(() => lastNMonths(6), []);
  const queries = useQueries({
    queries: months.map((m) => ({
      queryKey: ["tenant-society-bills", buildingId, m],
      queryFn: () => societyApi.myBills(buildingId, m),
      staleTime: 30_000,
    })),
  });

  const isLoading = queries.some((q) => q.isLoading);

  // Build chart data oldest → newest so the X-axis reads left-to-right.
  const chartData = useMemo(() => {
    return months
      .slice()
      .reverse()
      .map((m) => {
        const idx = months.indexOf(m);
        const rows = queries[idx]?.data ?? [];
        const paid = rows
          .filter((r) => r.status === "PAID")
          .reduce((s, r) => s + r.monthAmount, 0);
        return { month: fmtMonth(m), paid };
      });
  }, [months, queries]);

  const totalPaid = chartData.reduce((s, r) => s + r.paid, 0);

  // Nothing paid across the whole window — brand-new resident,
  // or one whose maintainer hasn't billed anything yet. Skip the
  // empty chart rather than render six empty bars.
  if (!isLoading && totalPaid === 0) return null;

  return (
    <CollapsibleSection
      className="mb-4"
      title="Your payment history"
      icon={TrendingUp}
      summary={
        isLoading
          ? "Last 6 months"
          : `${formatINR(totalPaid)} · last 6 months`
      }
      defaultOpen
    >
      {isLoading ? (
        <Skeleton className="h-40" />
      ) : (
        <div className="h-40">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
              maxBarSize={44}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="hsl(var(--border))"
              />
              <XAxis
                dataKey="month"
                tickLine={false}
                axisLine={false}
                fontSize={11}
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                fontSize={11}
                tickFormatter={(v) =>
                  Number(v) >= 1000
                    ? `₹${(Number(v) / 1000).toFixed(0)}K`
                    : `₹${v}`
                }
              />
              <Tooltip
                cursor={false}
                formatter={(v: number) => [formatINR(v), "Paid"]}
                contentStyle={{
                  borderRadius: 10,
                  border: "1px solid hsl(var(--border))",
                  fontSize: 12,
                }}
              />
              <Bar dataKey="paid" fill="#10b981" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </CollapsibleSection>
  );
}

/** Six most recent "YYYY-MM" strings, newest first. */
function lastNMonths(n: number): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 0; i < n; i += 1) {
    out.push(
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`,
    );
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}

/** "2026-07" → "Jul 2026". Mirrors the same helper in ledger-view. */
function fmtMonth(ym: string): string {
  const [y, m] = ym.split("-");
  if (!y || !m) return ym;
  const idx = Number(m) - 1;
  const names = [
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  ];
  return `${names[idx] ?? m} ${y}`;
}
