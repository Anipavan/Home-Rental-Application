import { useMemo, useState } from "react";
import { useQueries } from "@tanstack/react-query";
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
import { cn, formatINR } from "@/lib/utils";
import type {
  ExpenseCategory,
  FlatChargeCategory,
  PublicFlatBill,
  SocietyLedger,
} from "@/types/api";

/**
 * Shared transparency-view component used by BOTH the public
 * shareable-link page ({@code /society/view/:token}) and the
 * tenant "Maintenance details" tab ({@code /app/society}).
 *
 * <p>The two callers differ only in <em>where</em> the ledger data
 * comes from — a token-based public endpoint or an authenticated
 * per-building endpoint. Both return the same {@link SocietyLedger}
 * shape, so everything downstream (KPIs, chart, tables) is identical.
 * Callers hand a {@code fetchLedger} closure + a unique
 * {@code cacheKey} to keep React Query caches distinct.
 *
 * <p>{@code showMaintainerWidget} adds the floating bottom-left
 * contact widget that public visitors get; tenants leave it off
 * because they can already reach the maintainer via the in-app
 * contact affordances.
 */
export interface SocietyLedgerViewProps {
  /**
   * Function that returns the ledger for a given month. Public
   * callers wrap {@code societyApi.publicLedger(token, month)};
   * authenticated callers wrap {@code societyApi.ledger(bId, month)}.
   */
  fetchLedger: (month: string) => Promise<SocietyLedger>;
  /**
   * React Query key prefix that identifies this ledger. Prevents
   * cache collisions between the public and authenticated views
   * when both are rendered in the same session (e.g. a tenant who
   * also opened the shareable link in another tab).
   */
  cacheKey: string;
  /** Render the floating maintainer-contact widget (public page only). */
  showMaintainerWidget?: boolean;
  /**
   * Extra padding on the bottom of the wrapper so the floating
   * maintainer widget doesn't cover the last table. Ignored when
   * {@code showMaintainerWidget} is false.
   */
  bottomWidgetPad?: boolean;
}

const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  UTILITY: "Utility",
  SALARY: "Staff salary",
  SUPPLIES: "Supplies",
  REPAIR_COMMON: "Common-area repair",
  INSURANCE: "Insurance",
  TAX: "Tax / govt fees",
  OTHER: "Other",
};

const CATEGORY_COLOR: Record<ExpenseCategory, string> = {
  UTILITY: "#3b82f6",
  SALARY: "#8b5cf6",
  SUPPLIES: "#14b8a6",
  REPAIR_COMMON: "#f59e0b",
  INSURANCE: "#06b6d4",
  TAX: "#ef4444",
  OTHER: "#94a3b8",
};

const FLAT_CATEGORY_COLOR: Record<FlatChargeCategory, string> = {
  MAINTENANCE: "#10b981",
  WATER_BILL: "#0891b2",
  GAS_BILL: "#facc15",
  ELECTRICITY: "#f97316",
  COMMON_AREA_SHARE: "#84cc16",
  OTHER: "#a3a3a3",
};

const FLAT_CATEGORY_LEGEND_LABEL: Record<FlatChargeCategory, string> = {
  MAINTENANCE: "Per-flat: Maintenance",
  WATER_BILL: "Per-flat: Water bill",
  GAS_BILL: "Per-flat: Gas bill",
  ELECTRICITY: "Per-flat: Electricity",
  COMMON_AREA_SHARE: "Per-flat: Common-area share",
  OTHER: "Per-flat: Other",
};

const FLAT_CHARGE_COLUMNS: FlatChargeCategory[] = [
  "MAINTENANCE",
  "WATER_BILL",
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

export function SocietyLedgerView({
  fetchLedger,
  cacheKey,
  showMaintainerWidget = false,
  bottomWidgetPad = false,
}: SocietyLedgerViewProps) {
  const [selectedMonths, setSelectedMonths] = useState<string[]>([
    currentMonth(),
  ]);
  const [selectedFlats, setSelectedFlats] = useState<string[]>([]);

  const monthlyQueries = useQueries({
    queries: selectedMonths.map((m) => ({
      queryKey: [cacheKey, m] as const,
      queryFn: () => fetchLedger(m),
      retry: false,
    })),
  });

  const anyLoading = monthlyQueries.some((q) => q.isLoading);
  const anyError = monthlyQueries.some((q) => q.isError);
  const firstData = monthlyQueries.find((q) => q.data)?.data;

  const aggregate = useMemo(() => {
    type Row = {
      month: string;
      data: SocietyLedger | undefined;
    };
    const rows: Row[] = selectedMonths.map((m, i) => ({
      month: m,
      data: monthlyQueries[i]?.data,
    }));
    const expenses = rows.flatMap((r) =>
      (r.data?.expenses ?? []).map((e) => ({ ...e, _month: r.month })),
    );
    const hasFlatFilter = selectedFlats.length > 0;
    const chart = rows.map((r) => {
      const row: Record<string, number | string> = {
        month: fmtMonth(r.month),
      };
      for (const cat of Object.keys(CATEGORY_LABELS) as ExpenseCategory[]) {
        row[cat] = r.data?.byCategory[cat] ?? 0;
      }
      if (hasFlatFilter) {
        for (const bill of r.data?.flatBills ?? []) {
          if (!selectedFlats.includes(bill.flatNumber)) continue;
          for (const cat of Object.keys(
            FLAT_CATEGORY_COLOR,
          ) as FlatChargeCategory[]) {
            row[`FLAT_${bill.flatNumber}__${cat}`] = 0;
          }
          for (const charge of bill.charges) {
            const key = `FLAT_${bill.flatNumber}__${charge.category}`;
            row[key] = (Number(row[key]) || 0) + charge.amount;
          }
        }
      } else {
        for (const cat of Object.keys(
          FLAT_CATEGORY_COLOR,
        ) as FlatChargeCategory[]) {
          let total = 0;
          for (const bill of r.data?.flatBills ?? []) {
            for (const charge of bill.charges) {
              if (charge.category === cat) total += charge.amount;
            }
          }
          row[`FLAT_${cat}`] = total;
        }
      }
      return row;
    });
    const expensesTotal = rows.reduce(
      (acc, r) => acc + (r.data?.expensesThisMonth ?? 0),
      0,
    );
    const perFlatRows = rows.flatMap((r) =>
      (r.data?.flatBills ?? []).map((b) => ({ ...b, _month: r.month })),
    );
    const flatChargesTotal = perFlatRows.reduce(
      (acc, b) => acc + b.charges.reduce((s, c) => s + c.amount, 0),
      0,
    );
    const flatOptions = Array.from(
      new Set(perFlatRows.map((b) => b.flatNumber)),
    ).sort();
    return {
      expenses,
      chart,
      expensesTotal,
      flatChargesTotal,
      perFlatRows,
      flatOptions,
    };
  }, [monthlyQueries, selectedMonths, selectedFlats]);

  const flatsToShow =
    selectedFlats.length === 0
      ? aggregate.flatOptions
      : selectedFlats.filter((f) => aggregate.flatOptions.includes(f));
  const visiblePerFlatRows = aggregate.perFlatRows.filter((b) =>
    flatsToShow.includes(b.flatNumber),
  );

  if (anyLoading && !firstData) {
    return <Skeleton className="h-64 rounded-2xl" />;
  }
  if (anyError && !firstData) {
    return (
      <EmptyState
        variant="info"
        icon={Building2}
        title="Couldn't load the ledger"
        description="Try again in a moment, or ask the maintainer to check that expenses have been recorded."
      />
    );
  }
  if (!firstData) {
    return null;
  }

  return (
    <>
      <section className={cn(bottomWidgetPad && "pb-40 sm:pb-32")}>
        <div className="flex flex-wrap items-center gap-3">
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
          {aggregate.expensesTotal === 0 && aggregate.flatChargesTotal === 0 ? (
            <p className="text-sm text-muted-foreground py-10 text-center">
              No expenses or per-flat charges for the selected month(s).
            </p>
          ) : (
            <CategoryBarChart
              data={aggregate.chart}
              selectedFlats={selectedFlats}
            />
          )}
        </CollapsibleSection>

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
      </section>

      {showMaintainerWidget && <MaintainerWidget ledger={firstData} />}
    </>
  );
}

/* ────────────────────────────────────────────────────────────────
 *  Multi-select filter components
 * ──────────────────────────────────────────────────────────────── */

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
      if (selected.length === 1) return;
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
 *  Floating bottom-left maintainer contact widget (public page only)
 * ──────────────────────────────────────────────────────────────── */

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
 *  Bar chart
 * ──────────────────────────────────────────────────────────────── */

function chartLabelFor(name: string): string {
  if (name.startsWith("FLAT_")) {
    const rest = name.slice(5);
    const sep = rest.indexOf("__");
    if (sep !== -1) {
      const flatNum = rest.slice(0, sep);
      const cat = rest.slice(sep + 2) as FlatChargeCategory;
      const catLabel =
        FLAT_CATEGORY_LEGEND_LABEL[cat]?.replace(/^Per-flat: /, "") ?? cat;
      return `Flat ${flatNum} · ${catLabel}`;
    }
    const cat = rest as FlatChargeCategory;
    return FLAT_CATEGORY_LEGEND_LABEL[cat] ?? name;
  }
  return CATEGORY_LABELS[name as ExpenseCategory] ?? name;
}

function CategoryBarChart({
  data,
  selectedFlats,
}: {
  data: Array<Record<string, number | string>>;
  selectedFlats: string[];
}) {
  const hasFlatFilter = selectedFlats.length > 0;

  const liveCommon = (Object.keys(CATEGORY_LABELS) as ExpenseCategory[]).filter(
    (cat) => data.some((d) => Number(d[cat] ?? 0) > 0),
  );
  const liveFlatAggregate = (
    Object.keys(FLAT_CATEGORY_COLOR) as FlatChargeCategory[]
  ).filter((cat) => data.some((d) => Number(d[`FLAT_${cat}`] ?? 0) > 0));

  const liveByFlat: Record<string, FlatChargeCategory[]> = {};
  if (hasFlatFilter) {
    for (const flatNum of selectedFlats) {
      liveByFlat[flatNum] = (
        Object.keys(FLAT_CATEGORY_COLOR) as FlatChargeCategory[]
      ).filter((cat) =>
        data.some((d) => Number(d[`FLAT_${flatNum}__${cat}`] ?? 0) > 0),
      );
    }
  }

  return (
    <div className="h-80 w-full">
      <ResponsiveContainer width="100%" height="100%">
        {/* maxBarSize caps bar thickness so a single-month view
            doesn't render one giant bar — the previous 50%+ chart-
            width bar looked more like a block than a data point.
            barGap=0 makes clustered bars within a month sit flush
            against each other (no whitespace between siblings);
            barCategoryGap keeps the gap BETWEEN months intact. */}
        <BarChart
          data={data}
          margin={{ top: 8, right: 8, left: 8, bottom: 0 }}
          maxBarSize={56}
          barGap={0}
          barCategoryGap="20%"
        >
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
            /* cursor={false} removes Recharts' default grey column
             * highlight on hover — it was crowding out the bars
             * themselves on single-month views. */
            cursor={false}
            formatter={(v: number, name: string) => [
              formatINR(v),
              chartLabelFor(name),
            ]}
            contentStyle={{
              borderRadius: 10,
              border: "1px solid hsl(var(--border))",
              fontSize: 12,
            }}
          />
          <Legend
            verticalAlign="bottom"
            height={48}
            wrapperStyle={{ fontSize: 11 }}
            formatter={(name) => chartLabelFor(name as string)}
          />
          {/* No stackId anywhere — every category becomes its own
              side-by-side bar within the month tick (clustered).
              Easier to compare category-to-category at a glance
              than the earlier stacked layout, which packed
              everything into one column per month. */}
          {liveCommon.map((cat) => (
            <Bar
              key={cat}
              dataKey={cat}
              name={cat}
              fill={CATEGORY_COLOR[cat]}
              radius={[4, 4, 0, 0]}
            />
          ))}
          {!hasFlatFilter
            ? liveFlatAggregate.map((cat) => (
                <Bar
                  key={`FLAT_${cat}`}
                  dataKey={`FLAT_${cat}`}
                  name={`FLAT_${cat}`}
                  fill={FLAT_CATEGORY_COLOR[cat]}
                  radius={[4, 4, 0, 0]}
                />
              ))
            : selectedFlats.flatMap((flatNum) =>
                (liveByFlat[flatNum] ?? []).map((cat) => (
                  <Bar
                    key={`FLAT_${flatNum}__${cat}`}
                    dataKey={`FLAT_${flatNum}__${cat}`}
                    name={`FLAT_${flatNum}__${cat}`}
                    fill={FLAT_CATEGORY_COLOR[cat]}
                    radius={[4, 4, 0, 0]}
                  />
                )),
              )}
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
