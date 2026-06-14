import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { useQueries } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
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
  ChevronDown,
  Droplets,
  Filter,
  Mail,
  Phone,
  User,
  Wallet,
  Wrench,
  X,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { CollapsibleSection } from "@/components/ui/collapsible-section";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Logo } from "@/components/layout/logo";
import { formatINR } from "@/lib/utils";
import { cn } from "@/lib/utils";
import type {
  ExpenseCategory,
  FlatChargeCategory,
  PublicFlatBill,
  SocietyLedger,
} from "@/types/api";

const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  UTILITY: "Utility",
  SALARY: "Staff salary",
  SUPPLIES: "Supplies",
  REPAIR_COMMON: "Common-area repair",
  INSURANCE: "Insurance",
  TAX: "Tax / govt fees",
  OTHER: "Other",
};

/**
 * Bar-chart colour assignment per category. Picked from the brand's
 * existing palette tones (primary / success / warning / destructive /
 * neutrals) so the chart sits naturally inside the page without a
 * loud third-party look.
 */
const CATEGORY_COLOR: Record<ExpenseCategory, string> = {
  UTILITY: "#3b82f6", // blue — utilities
  SALARY: "#8b5cf6", // violet — payroll
  SUPPLIES: "#14b8a6", // teal — supplies
  REPAIR_COMMON: "#f59e0b", // amber — repairs
  INSURANCE: "#06b6d4", // cyan — insurance
  TAX: "#ef4444", // red — taxes
  OTHER: "#94a3b8", // slate — other
};

/** Per-flat charge categories shown on the public table. Same set
 *  as the maintainer dashboard — Gas/Electricity excluded because
 *  they aren't society-collected. */
const FLAT_CHARGE_COLUMNS: FlatChargeCategory[] = [
  "MAINTENANCE",
  "WATER_BILL",
  "COMMON_AREA_SHARE",
  "OTHER",
];

const FLAT_CHARGE_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

const STATUS_BADGE_CLASS: Record<PublicFlatBill["overallStatus"], string> = {
  SETTLED: "bg-success/20 text-success",
  PARTIAL: "bg-warning/20 text-warning",
  PENDING: "bg-destructive/20 text-destructive",
  NONE: "bg-muted text-muted-foreground",
};

const STATUS_BADGE_LABEL: Record<PublicFlatBill["overallStatus"], string> = {
  SETTLED: "Settled",
  PARTIAL: "Partial",
  PENDING: "Pending",
  NONE: "Not billed",
};

const currentMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

/**
 * Generate a list of the last N months as "YYYY-MM" strings, newest first.
 * Used to populate the month multi-select. 12 covers a full year, enough
 * for trend comparison without overwhelming the dropdown.
 */
function lastNMonths(n: number): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 0; i < n; i += 1) {
    const y = d.getFullYear();
    const m = d.getMonth() + 1;
    out.push(`${y}-${String(m).padStart(2, "0")}`);
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}

/** "2026-06" → "Jun 2026" for human display. */
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

/**
 * Public read-only society-ledger view. Reached via the shareable
 * link (https://anirudhhomes.in/society/view/{token}). No login —
 * the token in the URL is the only credential.
 *
 * <p>Redesigned Jun-2026. Previous version snapshotted under the
 * git tag {@code public-ledger-classic-v1} for one-command roll-back.
 *
 * <p>Visual flow (top → bottom):
 * <ol>
 *   <li><b>Filter row</b> — multi-select month + flat (flat scoped to
 *       per-flat bills only; common expenses don't carry a flatId).</li>
 *   <li><b>KPI cards</b> — Expenses across selected months + Net Fund
 *       Balance (lifetime, not affected by the month filter).</li>
 *   <li><b>Common-expenses summary</b> — single row, sum of common
 *       expenses across selected months. Gives the "where did our
 *       money go" answer in one number before the chart drills in.</li>
 *   <li><b>Bar chart</b> — Total spend by category for the selected
 *       months, one stacked-by-category bar per month so trends pop.
 *       Legend maps colours to categories.</li>
 *   <li><b>Common-expenses ledger table</b> — every individual expense
 *       line with the "For month" column added (separate from the
 *       Paid-on date — a May electric bill might be paid in June).</li>
 *   <li><b>Per-flat bills table</b> — filtered by the flat multi-
 *       select. Rows are flats × months when more than one month is
 *       selected, so a resident can see their own flat's trend.</li>
 * </ol>
 *
 * <p>Maintainer contact lives as a floating, fixed-position
 * bottom-left widget (see {@link MaintainerWidget}) — same affordance
 * as the in-app Contact Support widget. Defaults to collapsed so it
 * never crowds the data; one click expands phone + email. The earlier
 * v1 sidebar approach was scrapped in v2 (this file) because the
 * sidebar pushed the data to the right and left a big empty column
 * on tall pages with little maintainer info.
 *
 * <p>The three data sections (Spend by category / Expense entries /
 * Per-flat bills) are wrapped in {@link CollapsibleSection} so a
 * resident can collapse what they're not interested in and focus on
 * the one block they care about. All three default OPEN so the
 * first-paint experience still shows everything.
 */
export function PublicSocietyLedgerPage() {
  const { token } = useParams<{ token: string }>();

  // Multi-select state. Default = just the current month so the
  // first paint matches the old single-month behaviour. Empty array
  // is "show nothing" — guard against that in the data layer.
  const [selectedMonths, setSelectedMonths] = useState<string[]>([
    currentMonth(),
  ]);
  // Flat multi-select. Empty array == "all flats". Stored as flat
  // numbers (the only flat identifier visible in the public view).
  const [selectedFlats, setSelectedFlats] = useState<string[]>([]);

  /**
   * Parallel fetch per selected month. useQueries lets each month
   * cache independently — flipping a month off in the filter doesn't
   * refetch the survivors, only the new one when toggled back on.
   * Each query has the same shape so we can merge them on render.
   */
  const monthlyQueries = useQueries({
    queries: selectedMonths.map((m) => ({
      queryKey: ["public-ledger", token, m] as const,
      queryFn: () => societyApi.publicLedger(token!, m),
      enabled: !!token,
      retry: false,
    })),
  });

  const anyLoading = monthlyQueries.some((q) => q.isLoading);
  const anyError = monthlyQueries.some((q) => q.isError);
  // First successful response carries the "static" fields (society
  // name, maintainer contact, net balance) that don't change month-
  // to-month. Pull them once instead of recomputing.
  const firstData = monthlyQueries.find((q) => q.data)?.data;

  /**
   * Aggregate across selected months. Each datum keeps its source
   * month so the table can show a "For month" column and the bar
   * chart can group bars by month.
   */
  const aggregate = useMemo(() => {
    type Row = {
      month: string;
      data: SocietyLedger | undefined;
    };
    const rows: Row[] = selectedMonths.map((m, i) => ({
      month: m,
      data: monthlyQueries[i]?.data,
    }));
    // Flatten expenses with their source month attached
    const expenses = rows.flatMap((r) =>
      (r.data?.expenses ?? []).map((e) => ({ ...e, _month: r.month })),
    );
    // Build chart data: one entry per month, one numeric column per
    // category. Recharts BarChart wants this "wide" shape.
    const chart = rows.map((r) => {
      const row: Record<string, number | string> = { month: fmtMonth(r.month) };
      for (const cat of Object.keys(CATEGORY_LABELS) as ExpenseCategory[]) {
        row[cat] = r.data?.byCategory[cat] ?? 0;
      }
      return row;
    });
    // KPI: expenses across selected months. balanceLifetime doesn't
    // depend on which months are picked, so we read it off the first
    // available response.
    const expensesTotal = rows.reduce(
      (acc, r) => acc + (r.data?.expensesThisMonth ?? 0),
      0,
    );
    // Per-flat bills are per-month rows. Tag each with its month
    // so the table can split them when multiple months are picked.
    const perFlatRows = rows.flatMap((r) =>
      (r.data?.flatBills ?? []).map((b) => ({ ...b, _month: r.month })),
    );
    // Distinct flat numbers across all selected months — feeds the
    // flat multi-select options. Sorted lexicographically so flat
    // "001" appears before "201".
    const flatOptions = Array.from(
      new Set(perFlatRows.map((b) => b.flatNumber)),
    ).sort();
    return { expenses, chart, expensesTotal, perFlatRows, flatOptions };
  }, [monthlyQueries, selectedMonths]);

  // Effective flat filter: if nothing selected, treat as "show all".
  const flatsToShow =
    selectedFlats.length === 0
      ? aggregate.flatOptions
      : selectedFlats.filter((f) => aggregate.flatOptions.includes(f));
  const visiblePerFlatRows = aggregate.perFlatRows.filter((b) =>
    flatsToShow.includes(b.flatNumber),
  );

  return (
    <div className="min-h-screen bg-secondary/30">
      <header className="border-b border-border/60 bg-background/85 backdrop-blur-xl">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <Logo />
          <Badge variant="secondary">Public ledger</Badge>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
        {anyLoading && !firstData ? (
          <Skeleton className="h-64 rounded-2xl" />
        ) : anyError && !firstData ? (
          <EmptyState
            variant="info"
            icon={Building2}
            title="Couldn't load this ledger"
            description="The link may have been rotated by the owner or has never existed. Ask the maintainer for a fresh shareable link."
          />
        ) : !firstData ? null : (
          <>
            {/* Single-column main flow. Maintainer info no longer
                sits in a left sidebar — it now lives as a floating
                widget anchored to the bottom-left of the viewport
                (rendered AFTER the main flow, see <MaintainerWidget>
                below). That mirrors the in-app "Contact support"
                widget pattern the user wanted to match: always
                reachable, never crowds the data. Padding-bottom on
                the wrapper gives the floating widget breathing room
                on mobile so the last data card isn't hidden under it. */}
            <section className="pb-40 sm:pb-32">
              <h1 className="font-display font-bold text-2xl sm:text-3xl">
                {firstData.societyDisplayName ?? "Society ledger"}
              </h1>
              <p className="text-muted-foreground mt-1 text-sm">
                View society expenses, maintenance spending, and fund
                balances with complete transparency.
              </p>

              {/* 1. Filter row */}
              <div className="mt-6 flex flex-wrap items-center gap-3">
                <MonthFilter
                  selected={selectedMonths}
                  onChange={setSelectedMonths}
                  options={lastNMonths(12)}
                />
                <FlatFilter
                  selected={selectedFlats}
                  onChange={setSelectedFlats}
                  options={aggregate.flatOptions}
                />
                {(selectedMonths.length > 1 || selectedFlats.length > 0) && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setSelectedMonths([currentMonth()]);
                      setSelectedFlats([]);
                    }}
                    className="text-xs"
                  >
                    <X className="size-3.5" /> Reset filters
                  </Button>
                )}
              </div>

              {/* 2. KPI cards */}
              <div className="grid gap-4 sm:grid-cols-2 mt-5">
                <Kpi
                  icon={Droplets}
                  label={
                    selectedMonths.length === 1
                      ? `Expenses · ${fmtMonth(selectedMonths[0]!)}`
                      : `Expenses · ${selectedMonths.length} months`
                  }
                  value={formatINR(aggregate.expensesTotal)}
                  tone="destructive"
                />
                <Kpi
                  icon={Wallet}
                  label="Net Fund Balance (lifetime)"
                  value={formatINR(firstData.balanceLifetime)}
                  tone={firstData.balanceLifetime >= 0 ? "success" : "destructive"}
                />
              </div>

              {/* 3. Common-expenses summary block — single high-
                  contrast line answering "what did the society
                  spend in total on common stuff this period". Bar
                  chart breaks it down by category right after.
                  Intentionally NOT collapsible — it's the one-line
                  headline for everything below. */}
              <Card className="mt-4 bg-gradient-to-br from-primary/5 to-transparent border-primary/20">
                <CardContent className="p-5 flex items-center justify-between gap-4 flex-wrap">
                  <div>
                    <p className="text-xs uppercase tracking-wider text-muted-foreground">
                      Common expenses ·{" "}
                      {selectedMonths.length === 1
                        ? fmtMonth(selectedMonths[0]!)
                        : `${selectedMonths.length} months`}
                    </p>
                    <p className="font-display font-bold text-3xl mt-1">
                      {formatINR(aggregate.expensesTotal)}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-muted-foreground">
                      Across {aggregate.expenses.length} entr
                      {aggregate.expenses.length === 1 ? "y" : "ies"}
                    </p>
                    <p className="text-[11px] text-muted-foreground mt-0.5">
                      Drill into the chart and table below.
                    </p>
                  </div>
                </CardContent>
              </Card>

              {/* 4. Bar chart — collapsible. Default OPEN so a first-
                  time visitor sees the visualisation immediately;
                  power users collapsing to focus on the table find a
                  one-click affordance. */}
              <CollapsibleSection
                className="mt-4"
                title="Spend by category"
                icon={Wrench}
                summary={
                  aggregate.expensesTotal > 0
                    ? formatINR(aggregate.expensesTotal)
                    : "No data"
                }
              >
                {aggregate.expensesTotal === 0 ? (
                  <p className="text-sm text-muted-foreground py-10 text-center">
                    No expenses recorded for the selected month(s).
                  </p>
                ) : (
                  <CategoryBarChart data={aggregate.chart} />
                )}
              </CollapsibleSection>

              {/* 5. Common-expenses ledger table — collapsible. */}
              <CollapsibleSection
                className="mt-4"
                title="Expense entries"
                icon={Wrench}
                summary={
                  aggregate.expenses.length
                    ? `${aggregate.expenses.length} entr${aggregate.expenses.length === 1 ? "y" : "ies"} · ${formatINR(aggregate.expensesTotal)}`
                    : "No entries"
                }
              >
                {aggregate.expenses.length === 0 ? (
                  <EmptyState
                    variant="info"
                    icon={Wrench}
                    title="No expenses recorded"
                    description="The maintainer hasn't added any bills for the selected months yet."
                  />
                ) : (
                  <ExpenseLedgerTable rows={aggregate.expenses} />
                )}
              </CollapsibleSection>

              {/* 6. Per-flat bills table — collapsible. */}
              <CollapsibleSection
                className="mt-4"
                title="Per-flat bills"
                icon={Building2}
                summary={
                  visiblePerFlatRows.length
                    ? `${visiblePerFlatRows.length} row${visiblePerFlatRows.length === 1 ? "" : "s"}`
                    : "No bills"
                }
              >
                {visiblePerFlatRows.length === 0 ? (
                  <EmptyState
                    variant="info"
                    icon={Building2}
                    title="No bills to show"
                    description={
                      selectedFlats.length > 0
                        ? "The selected flat(s) have no bills in the picked month(s)."
                        : "Once flats are billed, they'll appear here."
                    }
                  />
                ) : (
                  <FlatBillsTable
                    rows={visiblePerFlatRows}
                    showMonth={selectedMonths.length > 1}
                  />
                )}
              </CollapsibleSection>

              <p className="text-xs text-muted-foreground mt-6 text-center">
                Powered by{" "}
                <span className="font-semibold">Anirudh Homes</span> · Public
                view for residents and stakeholders.
              </p>
            </section>

            {/* Floating bottom-left maintainer widget. `fixed` so it
                stays in view as the resident scrolls the long ledger
                tables — same affordance as the in-app Contact Support
                widget. The expand toggle keeps it tiny by default
                (just the avatar + role label) so it never crowds the
                main content; click the chevron to see phone/email. */}
            <MaintainerWidget ledger={firstData} />
          </>
        )}
      </main>
    </div>
  );
}

/* ────────────────────────────────────────────────────────────────
 *  Multi-select filter components
 * ──────────────────────────────────────────────────────────────── */

/**
 * Month multi-select. Always keeps at least one month selected
 * (clicking the only-selected one is a no-op) — otherwise the
 * page goes blank, which isn't a useful state.
 */
function MonthFilter({
  selected,
  onChange,
  options,
}: {
  selected: string[];
  onChange: (next: string[]) => void;
  options: string[];
}) {
  function toggle(m: string) {
    if (selected.includes(m)) {
      if (selected.length === 1) return; // keep at least one
      onChange(selected.filter((s) => s !== m));
    } else {
      onChange([...selected, m]);
    }
  }
  const label =
    selected.length === 1
      ? fmtMonth(selected[0]!)
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
      <DropdownMenuContent className="w-56 max-h-80 overflow-y-auto p-1">
        {options.map((m) => {
          const isSelected = selected.includes(m);
          return (
            <button
              key={m}
              type="button"
              onClick={() => toggle(m)}
              className={cn(
                "w-full flex items-center justify-between gap-2 px-2 py-1.5 text-sm rounded-md hover:bg-secondary/60",
                isSelected && "font-semibold",
              )}
            >
              <span>{fmtMonth(m)}</span>
              {isSelected && <Check className="size-3.5 text-primary" />}
            </button>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Flat multi-select. Empty selection means "show all" — the most
 * intuitive default for a resident hitting the page (they want to
 * see all flats first, then narrow down). "Select all" / "Clear"
 * buttons at the top of the menu for one-click bulk ops.
 */
function FlatFilter({
  selected,
  onChange,
  options,
}: {
  selected: string[];
  onChange: (next: string[]) => void;
  options: string[];
}) {
  function toggle(f: string) {
    if (selected.includes(f)) {
      onChange(selected.filter((s) => s !== f));
    } else {
      onChange([...selected, f]);
    }
  }
  const label =
    selected.length === 0
      ? "All flats"
      : selected.length === 1
        ? `Flat ${selected[0]}`
        : `${selected.length} flats`;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <Filter className="size-3.5" />
          {label}
          <ChevronDown className="size-3.5 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-48 max-h-80 overflow-y-auto p-1">
        <div className="flex gap-1 px-1 pb-1 border-b border-border/60 mb-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="flex-1 text-xs h-7"
            onClick={() => onChange(options)}
            disabled={options.length === 0}
          >
            Select all
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="flex-1 text-xs h-7"
            onClick={() => onChange([])}
            disabled={selected.length === 0}
          >
            Clear
          </Button>
        </div>
        {options.length === 0 ? (
          <p className="text-xs text-muted-foreground px-2 py-3 text-center">
            No flats yet
          </p>
        ) : (
          options.map((f) => {
            const isSelected = selected.includes(f);
            return (
              <button
                key={f}
                type="button"
                onClick={() => toggle(f)}
                className={cn(
                  "w-full flex items-center justify-between gap-2 px-2 py-1.5 text-sm rounded-md hover:bg-secondary/60 font-mono",
                  isSelected && "font-semibold",
                )}
              >
                <span>Flat {f}</span>
                {isSelected && <Check className="size-3.5 text-primary" />}
              </button>
            );
          })
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/* ────────────────────────────────────────────────────────────────
 *  Floating bottom-left widget — maintainer contact
 * ──────────────────────────────────────────────────────────────── */

/**
 * Fixed-position bottom-left widget that mirrors the in-app
 * "Contact Support" affordance shown on the maintainer's
 * authenticated dashboard. Stays visible as the resident scrolls
 * the long ledger tables; one click toggles expanded vs collapsed.
 *
 * <p>Defaults to collapsed — just the icon and "Society maintainer"
 * label — so the widget never crowds the data on first paint. The
 * expanded panel reveals the maintainer's name + clickable phone
 * and email links, identical content to the old sidebar.
 *
 * <p>z-index of 40 sits below modal overlays (typically 50) but
 * above the page content. Visually anchored 16px from both edges
 * on every breakpoint so it doesn't collide with the browser's
 * scroll bar or any OS chrome.
 */
function MaintainerWidget({ ledger }: { ledger: SocietyLedger }) {
  const [open, setOpen] = useState(false);
  const hasAny =
    ledger.maintainerName || ledger.maintainerPhone || ledger.maintainerEmail;

  return (
    <div className="fixed bottom-4 left-4 z-40 max-w-[calc(100vw-2rem)] w-72">
      {open && hasAny && (
        <Card className="shadow-lift border-primary/20 mb-2 animate-fade-in">
          <CardContent className="p-4">
            <div className="flex items-start justify-between gap-3 mb-2">
              <div>
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
                  Society maintainer
                </p>
                {ledger.maintainerName && (
                  <p className="font-display font-semibold text-sm mt-0.5">
                    {ledger.maintainerName}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Close maintainer panel"
                className="text-muted-foreground hover:text-foreground -mt-1 -mr-1 p-1"
              >
                <X className="size-3.5" />
              </button>
            </div>
            <div className="space-y-2 text-sm">
              {ledger.maintainerPhone && (
                <a
                  href={`tel:${ledger.maintainerPhone}`}
                  className="flex items-center gap-2 text-primary hover:underline"
                >
                  <Phone className="size-3.5 shrink-0" />
                  <span className="truncate">{ledger.maintainerPhone}</span>
                </a>
              )}
              {ledger.maintainerEmail && (
                <a
                  href={`mailto:${ledger.maintainerEmail}`}
                  className="flex items-center gap-2 text-primary hover:underline"
                >
                  <Mail className="size-3.5 shrink-0" />
                  <span className="truncate break-all">
                    {ledger.maintainerEmail}
                  </span>
                </a>
              )}
            </div>
            <p className="text-[10px] text-muted-foreground mt-3 pt-2 border-t border-border/40 leading-snug">
              Got a question about a charge? Reach out before raising
              a complaint — most things are quick clarifications.
            </p>
          </CardContent>
        </Card>
      )}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label="Contact society maintainer"
        className={cn(
          "w-full flex items-center gap-2 px-3 py-2.5 rounded-xl border bg-card shadow-lift hover:bg-secondary/40 transition-colors text-left",
          !hasAny && "opacity-60 cursor-default pointer-events-none",
        )}
      >
        <div className="size-8 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
          <User className="size-4" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
            Society maintainer
          </p>
          <p className="text-xs truncate">
            {hasAny
              ? ledger.maintainerName ?? "Tap to contact"
              : "Not assigned yet"}
          </p>
        </div>
        {hasAny && (
          <ChevronDown
            className={cn(
              "size-4 text-muted-foreground shrink-0 transition-transform",
              open && "rotate-180",
            )}
          />
        )}
      </button>
    </div>
  );
}

/* ────────────────────────────────────────────────────────────────
 *  Bar chart — spend by category
 * ──────────────────────────────────────────────────────────────── */

function CategoryBarChart({
  data,
}: {
  data: Array<Record<string, number | string>>;
}) {
  // Discover which categories actually have nonzero data anywhere in
  // the dataset — keeps the legend tight by hiding always-zero
  // categories.
  const liveCategories = (Object.keys(CATEGORY_LABELS) as ExpenseCategory[])
    .filter((cat) => data.some((d) => Number(d[cat] ?? 0) > 0));

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
          <XAxis
            dataKey="month"
            tickLine={false}
            axisLine={false}
            fontSize={12}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            fontSize={12}
            tickFormatter={(v) =>
              Number(v) >= 1000
                ? `₹${(Number(v) / 1000).toFixed(0)}K`
                : `₹${v}`
            }
          />
          <Tooltip
            formatter={(v: number, name: string) => [
              formatINR(v),
              CATEGORY_LABELS[name as ExpenseCategory] ?? name,
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
            wrapperStyle={{ fontSize: 12 }}
            formatter={(name) =>
              CATEGORY_LABELS[name as ExpenseCategory] ?? name
            }
          />
          {liveCategories.map((cat) => (
            <Bar
              key={cat}
              dataKey={cat}
              name={cat}
              stackId="a"
              radius={[6, 6, 0, 0]}
            >
              {data.map((_, idx) => (
                <Cell key={`${cat}-${idx}`} fill={CATEGORY_COLOR[cat]} />
              ))}
            </Bar>
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/* ────────────────────────────────────────────────────────────────
 *  Tables
 * ──────────────────────────────────────────────────────────────── */

type ExpenseRow = SocietyLedger["expenses"][number] & { _month: string };

function ExpenseLedgerTable({ rows }: { rows: ExpenseRow[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border/60">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-secondary/40 border-b border-border/60">
            <Th>Category</Th>
            {/* NEW — "For month" column. Separates "what month this
                expense relates to" from "when it was paid", which can
                differ by weeks (May electric bill paid mid-June). */}
            <Th>For month</Th>
            <Th>Description</Th>
            <Th>Vendor</Th>
            <Th>Paid on</Th>
            <Th align="right">Amount</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((e) => (
            <tr
              key={e.id}
              className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20"
            >
              <td className="px-3 py-2 align-top whitespace-nowrap">
                <Badge
                  variant="secondary"
                  className="text-[10px]"
                  style={{
                    backgroundColor: `${CATEGORY_COLOR[e.category]}20`,
                    color: CATEGORY_COLOR[e.category],
                  }}
                >
                  {CATEGORY_LABELS[e.category]}
                </Badge>
              </td>
              <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap font-medium">
                {fmtMonth(e.expenseMonth ?? e._month)}
              </td>
              <td className="px-3 py-2 align-top">
                <p className="font-medium text-sm">
                  {e.subcategory ?? e.vendorName ?? "—"}
                </p>
                {e.notes && (
                  <p className="text-[11px] text-muted-foreground italic mt-0.5 line-clamp-2">
                    {e.notes}
                  </p>
                )}
              </td>
              <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap">
                {e.vendorName ?? "—"}
              </td>
              <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap">
                {e.paidOnDate}
              </td>
              <td className="px-3 py-2 align-top text-right">
                <span className="font-semibold font-display whitespace-nowrap">
                  {formatINR(e.amount)}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

type FlatBillRow = PublicFlatBill & { _month: string };

function FlatBillsTable({
  rows,
  showMonth,
}: {
  rows: FlatBillRow[];
  showMonth: boolean;
}) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border/60">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-secondary/40 border-b border-border/60">
            <Th>Flat</Th>
            {/* Month column appears only when comparing multiple
                months — otherwise it'd be a noisy duplicate. */}
            {showMonth && <Th>Month</Th>}
            {FLAT_CHARGE_COLUMNS.map((c) => (
              <Th key={c}>{FLAT_CHARGE_LABELS[c]}</Th>
            ))}
            <Th align="right">Total Due</Th>
            <Th align="right">Status</Th>
          </tr>
        </thead>
        <tbody>
          {rows.map((bill, i) => (
            <FlatBillRowTr
              key={`${bill.flatNumber}-${bill._month}-${i}`}
              bill={bill}
              showMonth={showMonth}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FlatBillRowTr({
  bill,
  showMonth,
}: {
  bill: FlatBillRow;
  showMonth: boolean;
}) {
  const byCategory = new Map<
    FlatChargeCategory,
    PublicFlatBill["charges"][number]
  >();
  for (const c of bill.charges) byCategory.set(c.category, c);

  return (
    <tr className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20">
      <td className="px-3 py-2 align-top">
        <Badge variant="outline" className="font-mono text-[11px]">
          {bill.flatNumber}
        </Badge>
      </td>
      {showMonth && (
        <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap font-medium">
          {fmtMonth(bill._month)}
        </td>
      )}
      {FLAT_CHARGE_COLUMNS.map((c) => {
        const charge = byCategory.get(c);
        return (
          <td key={c} className="px-3 py-2 align-top">
            {charge ? (
              <div>
                <p className="font-semibold font-display text-sm">
                  {formatINR(charge.amount)}
                </p>
                <span
                  className={`block mt-0.5 rounded-full text-[9px] font-semibold uppercase tracking-wide px-1.5 py-0 w-fit ${
                    charge.status === "PAID"
                      ? "bg-success/20 text-success"
                      : charge.status === "OVERDUE"
                        ? "bg-destructive/20 text-destructive"
                        : charge.status === "WAIVED"
                          ? "bg-muted text-muted-foreground"
                          : "bg-warning/20 text-warning"
                  }`}
                >
                  {charge.status}
                </span>
              </div>
            ) : (
              <span className="text-xs text-muted-foreground">—</span>
            )}
          </td>
        );
      })}
      <td className="px-3 py-2 align-top text-right">
        {bill.totalDue > 0 ? (
          <span className="font-semibold text-destructive whitespace-nowrap">
            {formatINR(bill.totalDue)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
      <td className="px-3 py-2 align-top text-right">
        <span
          className={`inline-block rounded-full text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 ${STATUS_BADGE_CLASS[bill.overallStatus]}`}
        >
          {STATUS_BADGE_LABEL[bill.overallStatus]}
        </span>
      </td>
    </tr>
  );
}

function Th({
  children,
  align = "left",
}: {
  children: React.ReactNode;
  align?: "left" | "right";
}) {
  return (
    <th
      className={cn(
        "px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap",
        align === "right" ? "text-right" : "text-left",
      )}
    >
      {children}
    </th>
  );
}

/* ────────────────────────────────────────────────────────────────
 *  KPI card
 * ──────────────────────────────────────────────────────────────── */

function Kpi({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  tone: "success" | "destructive" | "muted";
}) {
  const toneClass =
    tone === "success"
      ? "text-success"
      : tone === "destructive"
        ? "text-destructive"
        : "text-foreground";
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground">
          <Icon className="size-4" />
          {label}
        </div>
        <p className={`font-display font-bold text-2xl mt-2 ${toneClass}`}>
          {value}
        </p>
      </CardContent>
    </Card>
  );
}
