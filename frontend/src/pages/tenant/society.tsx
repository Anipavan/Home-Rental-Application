import { useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQueries, useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Building2,
  Calendar,
  Check,
  CheckCircle2,
  ChevronDown,
  FileText,
  Loader2,
  Receipt,
  TrendingUp,
  Wallet,
  X,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { toast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { CollapsibleSection } from "@/components/ui/collapsible-section";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { PageHeader } from "@/components/layout/page-header";
import { AnnouncementsPanel } from "@/components/society/announcements-panel";
import { SocietyLedgerView } from "@/components/society/ledger-view";
import { cn, formatINR } from "@/lib/utils";
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
          <YourMaintenanceTab config={config} />
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
 * The "Your maintenance" tab content — multi-month view that
 * mirrors the Maintenance details tab's picker semantics. Owns
 * its own selectedMonths state (parent no longer threads month
 * through props). Bills fetch one query per selected month via
 * useQueries, so React Query caches month-by-month and toggling
 * a month off in the picker doesn't refetch the survivors.
 */
function YourMaintenanceTab({ config }: { config: SocietyConfig }) {
  const navigate = useNavigate();
  // Default = just the current month so first-paint matches the
  // pre-multi-select behaviour. Empty array is guarded against in
  // the MonthFilter component (it enforces min 1 selection).
  const [selectedMonths, setSelectedMonths] = useState<string[]>([
    currentMonth(),
  ]);

  // Stable idempotency key for this page-render. Two clicks of the
  // Pay-all button send the SAME key, so payment-service returns
  // the existing paymentId instead of minting a second order. Reset
  // between mounts (a fresh visit gets a fresh key).
  const idempotencyKeyRef = useRef<string>(
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  );

  // Newest first — keeps table rows in a stable descending order.
  const orderedMonths = useMemo(
    () => [...selectedMonths].sort((a, b) => b.localeCompare(a)),
    [selectedMonths],
  );

  const billQueries = useQueries({
    queries: orderedMonths.map((m) => ({
      queryKey: ["tenant-society-bills", config.buildingId, m] as const,
      queryFn: () => societyApi.myBills(config.buildingId, m),
      staleTime: 15_000,
    })),
  });

  const anyBillsLoading = billQueries.some((q) => q.isLoading);

  // Flatten every selected month's bills, tagging each row with
  // its source month so the table can render a "For month" column
  // when the user has more than one month picked.
  type BillRow = FlatMaintenanceRow & { _month: string };
  const allBills: BillRow[] = useMemo(() => {
    return orderedMonths.flatMap((m, i) => {
      const rows = billQueries[i]?.data ?? [];
      return rows.map((r) => ({ ...r, _month: m }));
    });
  }, [orderedMonths, billQueries]);

  const totalDue = useMemo(
    () =>
      allBills
        .filter((r) => r.status === "DUE" || r.status === "OVERDUE")
        .reduce((s, r) => s + r.monthAmount, 0),
    [allBills],
  );

  const showMonthColumn = orderedMonths.length > 1;

  // Collect every DUE / OVERDUE collectionId across the selected
  // months. Backend bridge accepts a flat list; the ordering doesn't
  // matter because payment-service treats them as an atomic set.
  const dueCollectionIds = useMemo(
    () =>
      allBills
        .filter((r) => r.status === "DUE" || r.status === "OVERDUE")
        .map((r) => r.collectionId)
        .filter((id): id is string => !!id),
    [allBills],
  );

  // Bulk-pay mutation. Works for single-month AND multi-month
  // selections — the bridge already accepts an arbitrary list of
  // collectionIds, and the tenant's own bills are always on the
  // same flat so the backend's "single flat per order" constraint
  // is satisfied automatically. Skipping the intermediate
  // /app/society/pay-all/{buildingId}/{month} URL entirely means
  // the multi-month case now works — that URL only carried one
  // month at a time, which was the reason the button used to
  // disappear on multi-select.
  const payAllMut = useMutation({
    mutationFn: () =>
      societyApi.initiateSocietyChargePayment(
        config.buildingId,
        dueCollectionIds,
        idempotencyKeyRef.current,
      ),
    onSuccess: (res) => {
      navigate(`/app/payments/${res.paymentId}/pay`, { replace: true });
    },
    onError: (err) =>
      toast({
        variant: "destructive",
        title: "Couldn't start the payment",
        description: extractErrorMessage(err),
      }),
  });

  return (
    <>
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <MonthFilter
          selected={selectedMonths}
          onChange={setSelectedMonths}
          options={lastNMonths(12)}
        />
        {selectedMonths.length > 1 && (
          <Button
            variant="ghost"
            size="sm"
            className="text-xs"
            onClick={() => setSelectedMonths([currentMonth()])}
          >
            <X className="size-3.5" /> Reset to this month
          </Button>
        )}
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

      {/* Personal history strip — bar chart of what the tenant has
          actually paid, one bar per selected month. Renders an
          empty-state card (instead of vanishing) when nothing has
          been paid in the window, matching the Maintenance details
          tab's "No data" affordance. */}
      <MyMonthlySpendChart
        buildingId={config.buildingId}
        selectedMonths={orderedMonths}
      />

      <CollapsibleSection
        className="mb-4"
        title={
          orderedMonths.length === 1
            ? `My charges — ${fmtMonth(orderedMonths[0]!)}`
            : `My charges — ${orderedMonths.length} months`
        }
        icon={Receipt}
        summary={
          allBills.length
            ? totalDue > 0
              ? `Total Dues ${formatINR(totalDue)}`
              : `${allBills.length} charge${allBills.length === 1 ? "" : "s"}`
            : "No bills"
        }
      >
        {anyBillsLoading ? (
          <Skeleton className="h-24 rounded-xl" />
        ) : !allBills.length ? (
          <EmptyState
            variant="info"
            icon={Receipt}
            title="No bills posted for the selected month(s) yet"
            description="The maintainer hasn't entered any charges against your flat for this period. Check back later or message them if you think this is wrong."
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border/60">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="bg-secondary/40 border-b border-border/60">
                  <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    Category
                  </th>
                  {showMonthColumn && (
                    <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                      For month
                    </th>
                  )}
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
                {allBills.map((row) => (
                  <ChargeRow
                    key={`${row._month}-${row.collectionId ?? row.category ?? "row"}`}
                    row={row}
                    config={config}
                    showMonth={showMonthColumn}
                  />
                ))}
              </tbody>
              {totalDue > 0 && dueCollectionIds.length > 0 && (
                <tfoot>
                  <tr className="bg-primary/5 border-t-2 border-border/60 font-semibold">
                    <td
                      colSpan={showMonthColumn ? 4 : 3}
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
                      {/* Direct mutation instead of a Link — the
                        * old /pay-all/{buildingId}/{month} URL only
                        * carried a single month. Calling the bridge
                        * directly lets multi-month selections work
                        * too; the backend accepts any number of
                        * collectionIds as long as they're on one
                        * flat (a tenant's own bills always are). */}
                      <Button
                        variant="gradient"
                        size="sm"
                        onClick={() => payAllMut.mutate()}
                        disabled={
                          payAllMut.isPending || dueCollectionIds.length === 0
                        }
                      >
                        {payAllMut.isPending ? (
                          <>
                            <Loader2 className="size-4 animate-spin" /> Starting…
                          </>
                        ) : (
                          <>Pay all · {formatINR(totalDue)}</>
                        )}
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
 * Two-level month picker: pick a year first (top tabs), then pick
 * one or more months from that year's 12-cell grid. Multi-select is
 * always on; enforces at least one selection so the page never
 * renders a blank state.
 *
 * <p>Why the redesign: the previous "flat list of last N months"
 * ordering mixed years together (Aug 2026 next to Dec 2025 next to
 * Sep 2025) which was confusing, AND it iterated backwards from
 * today only — future months of the current year (e.g. Sep 2026
 * when the maintainer already has expenses for that month) never
 * appeared. Year → months groups the picker semantically and
 * naturally surfaces every month of the selected year, past or
 * future. The {@code options} prop is no longer consumed — kept
 * only for backward compatibility with existing call sites.
 */
function MonthFilter({
  selected,
  onChange,
}: {
  selected: string[];
  onChange: (next: string[]) => void;
  /** Ignored — retained so old call sites keep compiling. The picker
   *  now builds its own year × month grid from {@code selected}. */
  options?: string[];
}) {
  const now = new Date();
  const nowYear = now.getFullYear();
  const nowMonth = now.getMonth() + 1;
  // Year tabs shown at the top of the dropdown. Always include the
  // current + previous + next year. Also fold in any years that
  // appear in the current selection so the picker never orphans a
  // selected month (e.g. resident jumped to Jan 2024 in a bookmark).
  const availableYears = new Set<number>([
    nowYear - 1,
    nowYear,
    nowYear + 1,
  ]);
  for (const m of selected) {
    const y = Number(m.split("-")[0]);
    if (Number.isFinite(y)) availableYears.add(y);
  }
  const yearTabs = Array.from(availableYears).sort((a, b) => b - a); // newest first

  // Start the picker on whichever year the first selected month
  // lives in — matches user expectation ("open where I left off").
  const initialYear = (() => {
    const s = [...selected].sort((a, b) => b.localeCompare(a))[0];
    const parsed = s ? Number(s.split("-")[0]) : NaN;
    return Number.isFinite(parsed) ? parsed : nowYear;
  })();
  const [viewYear, setViewYear] = useState<number>(initialYear);

  // 12 months of the currently-viewed year. Includes future months
  // — a maintainer may pre-load Sep 2026 charges in August, and the
  // tenant needs to see them ahead of time.
  const monthsInYear: string[] = Array.from({ length: 12 }, (_, i) =>
    `${viewYear}-${String(i + 1).padStart(2, "0")}`,
  );

  function toggle(m: string) {
    if (selected.includes(m)) {
      if (selected.length === 1) return; // keep at least one
      onChange(selected.filter((s) => s !== m));
    } else {
      onChange([...selected, m]);
    }
  }

  const sorted = [...selected].sort((a, b) => b.localeCompare(a));
  const label =
    selected.length === 1
      ? fmtMonth(sorted[0]!)
      : `${selected.length} months`;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <Calendar className="size-3.5" />
          {label}
          <ChevronDown className="size-3.5 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-72 p-1">
        {/* Year tabs — one row of small buttons across the top.
            Clicking a year just switches which months are shown
            below; it does NOT clear the current selection so a
            user can multi-select across years by tab-hopping. */}
        <div className="flex gap-1 px-1 pb-1 border-b border-border/60 mb-1 overflow-x-auto">
          {yearTabs.map((y) => (
            <Button
              key={y}
              type="button"
              variant={viewYear === y ? "default" : "ghost"}
              size="sm"
              className="text-xs h-7 px-2 shrink-0"
              onClick={() => setViewYear(y)}
            >
              {y}
            </Button>
          ))}
        </div>
        {/* Quick actions scoped to the currently-viewed year. */}
        <div className="flex gap-1 px-1 pb-1 border-b border-border/60 mb-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="flex-1 text-xs h-7"
            onClick={() => onChange(monthsInYear)}
            disabled={monthsInYear.every((m) => selected.includes(m))}
          >
            All {viewYear}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="flex-1 text-xs h-7"
            onClick={() => onChange([currentMonth()])}
            disabled={selected.length === 1 && selected[0] === currentMonth()}
          >
            This month
          </Button>
        </div>
        {/* 3×4 month grid — feels more like a calendar and takes half
            the vertical space of a scrollable list. */}
        <div className="grid grid-cols-3 gap-1 p-1">
          {monthsInYear.map((m) => {
            const isSelected = selected.includes(m);
            const isFuture =
              viewYear > nowYear ||
              (viewYear === nowYear && Number(m.split("-")[1]) > nowMonth);
            return (
              <button
                key={m}
                type="button"
                onClick={() => toggle(m)}
                className={cn(
                  "flex items-center justify-center gap-1 px-2 py-1.5 text-xs rounded-md border transition-colors",
                  isSelected
                    ? "border-primary bg-primary/10 font-semibold text-primary"
                    : "border-transparent hover:bg-secondary/60",
                  isFuture && !isSelected && "text-muted-foreground",
                )}
              >
                <span>{fmtMonth(m).split(" ")[0]}</span>
                {isSelected && <Check className="size-3" />}
              </button>
            );
          })}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * One charge row. Pay button on DUE/OVERDUE (destination page handles
 * the "no UPI configured" case); check mark + paid-on date on PAID.
 * The optional For-month cell is rendered when the caller has more
 * than one month selected — otherwise the column is hidden entirely.
 */
function ChargeRow({
  row,
  config,
  showMonth = false,
}: {
  row: FlatMaintenanceRow & { _month?: string };
  config: SocietyConfig;
  showMonth?: boolean;
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
      {showMonth && (
        <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap font-medium">
          {row._month ? fmtMonth(row._month) : "—"}
        </td>
      )}
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
/**
 * Category palette for the personal payment-history chart. Matches
 * the FLAT_CATEGORY_COLOR map in ledger-view.tsx so the tenant sees
 * the same colour for "Water bill" on both the Your maintenance
 * chart and the Details tab chart — no re-learning required.
 */
const CATEGORY_COLORS: Record<FlatChargeCategory, string> = {
  MAINTENANCE: "#10b981",
  WATER_BILL: "#0891b2",
  GAS_BILL: "#facc15",
  ELECTRICITY: "#f97316",
  COMMON_AREA_SHARE: "#84cc16",
  OTHER: "#a3a3a3",
};

function MyMonthlySpendChart({
  buildingId,
  selectedMonths,
}: {
  buildingId: string;
  /** Months the picker has selected. Chart data is re-ordered
   *  oldest → newest internally so the X-axis always reads
   *  left-to-right regardless of picker order. */
  selectedMonths: string[];
}) {
  const queries = useQueries({
    queries: selectedMonths.map((m) => ({
      queryKey: ["tenant-society-bills", buildingId, m],
      queryFn: () => societyApi.myBills(buildingId, m),
      staleTime: 30_000,
    })),
  });

  const isLoading = queries.some((q) => q.isLoading);

  // Chart data — one row per month, one numeric column per category.
  // Recharts renders adjacent <Bar> components as clustered groups
  // when they share no stackId, so each category becomes its own
  // side-by-side bar within the month tick.
  //
  // Includes PAID + DUE + OVERDUE (skips WAIVED). The chart is a
  // "what have I been charged" view, not a settlement ledger — a
  // month with only DUE bills should still show its bars so the
  // tenant sees the full pattern instead of a blank tick.
  const chartData = useMemo(() => {
    const orderedOldFirst = [...selectedMonths].sort((a, b) =>
      a.localeCompare(b),
    );
    return orderedOldFirst.map((m) => {
      const idx = selectedMonths.indexOf(m);
      const rows = queries[idx]?.data ?? [];
      const row: Record<string, number | string> = { month: fmtMonth(m) };
      for (const cat of Object.keys(CATEGORY_COLORS) as FlatChargeCategory[]) {
        row[cat] = 0;
      }
      for (const r of rows) {
        if (r.status === "WAIVED") continue;
        const cat = r.category ?? "OTHER";
        row[cat] = (Number(row[cat]) || 0) + r.monthAmount;
      }
      return row;
    });
  }, [selectedMonths, queries]);

  // Only render categories with a positive value somewhere in the
  // window — keeps the legend tight (no "Gas bill" entry for a
  // tenant who never gets a gas bill).
  const liveCategories = useMemo(
    () =>
      (Object.keys(CATEGORY_COLORS) as FlatChargeCategory[]).filter((c) =>
        chartData.some((d) => Number(d[c] ?? 0) > 0),
      ),
    [chartData],
  );

  const totalPaid = chartData.reduce(
    (s, r) =>
      s +
      (Object.keys(CATEGORY_COLORS) as FlatChargeCategory[]).reduce(
        (ss, c) => ss + (Number(r[c]) || 0),
        0,
      ),
    0,
  );
  const hasData = totalPaid > 0;

  const windowLabel =
    selectedMonths.length === 1
      ? fmtMonth(selectedMonths[0]!)
      : `${selectedMonths.length} months`;

  return (
    <CollapsibleSection
      className="mb-4"
      title="Your payment history"
      icon={TrendingUp}
      summary={
        isLoading
          ? windowLabel
          : hasData
            ? `${formatINR(totalPaid)} · ${windowLabel}`
            : `No data · ${windowLabel}`
      }
      defaultOpen
    >
      {isLoading ? (
        <Skeleton className="h-52" />
      ) : !hasData ? (
        /* Matches the Maintenance details tab's empty-state copy so
         * the two charts feel like one system. The card stays
         * visible (unlike the earlier auto-hide) so the layout
         * doesn't jump around when a resident toggles months. */
        <p className="text-sm text-muted-foreground py-10 text-center">
          No payments settled for the selected month(s) yet.
        </p>
      ) : (
        <div className="h-56">
          <ResponsiveContainer width="100%" height="100%">
            {/* No maxBarSize — Recharts auto-fills the category
                slot. Combined with barGap=0 that makes sibling
                bars sit truly flush (no whitespace between the
                green Maintenance bar and the blue Water-bill bar
                within a month). barCategoryGap="35%" keeps each
                month's cluster narrow enough that a multi-month
                view still reads as distinct groups. */}
            <BarChart
              data={chartData}
              margin={{ top: 8, right: 8, left: 0, bottom: 0 }}
              barGap={0}
              barCategoryGap="35%"
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
                formatter={(v: number, name: string) => [
                  formatINR(v),
                  CHARGE_LABELS[name as FlatChargeCategory] ?? name,
                ]}
                contentStyle={{
                  borderRadius: 10,
                  border: "1px solid hsl(var(--border))",
                  fontSize: 12,
                }}
              />
              <Legend
                verticalAlign="bottom"
                height={36}
                wrapperStyle={{ fontSize: 11 }}
                formatter={(name) =>
                  CHARGE_LABELS[name as FlatChargeCategory] ?? String(name)
                }
              />
              {/* One <Bar> per live category, no stackId, so Recharts
                * groups them side-by-side per month (clustered).
                * minPointSize=3 keeps a small charge (e.g. ₹2K
                * Maintenance) visible next to a giant one (e.g.
                * ₹20L Water bill) — without it the tiny bar would
                * be under a pixel tall on a linear axis. */}
              {liveCategories.map((cat) => (
                <Bar
                  key={cat}
                  dataKey={cat}
                  name={cat}
                  fill={CATEGORY_COLORS[cat]}
                  radius={[4, 4, 0, 0]}
                  minPointSize={3}
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </CollapsibleSection>
  );
}

/**
 * N most recent "YYYY-MM" strings ending at {@code anchor} (inclusive),
 * newest first. Anchor defaults to the current month. Robust to
 * malformed anchor input — falls back to "today" so a bad picker
 * value never blows up the chart.
 */
function lastNMonths(n: number, anchor?: string): string[] {
  const d = new Date();
  if (anchor) {
    const [ys, ms] = anchor.split("-");
    const year = Number(ys);
    const month = Number(ms);
    if (Number.isFinite(year) && Number.isFinite(month) && month >= 1) {
      // JS month is 0-indexed; "2026-01" → January → month=0.
      d.setFullYear(year, month - 1, 1);
    }
  }
  const out: string[] = [];
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
