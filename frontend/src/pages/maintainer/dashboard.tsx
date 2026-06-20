import { useEffect, useMemo, useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  Calendar,
  ChevronRight,
  Droplets,
  HandCoins,
  Pencil,
  Plus,
  Wrench,
  CheckCircle2,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { claimsApi } from "@/lib/api/claims";
import { SocietyBankPanel } from "./society-bank-panel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { useToast } from "@/hooks/use-toast";
import { formatINR } from "@/lib/utils";
import { extractErrorMessage } from "@/lib/api/client";
import type {
  CollectionStatus,
  FlatMaintenanceRow,
  FlatChargeCategory,
  UpsertFlatCollectionRequest,
} from "@/types/api";

const CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  // "Common-area share" was renamed to "Additional Expenses" — the
  // underlying enum value stays COMMON_AREA_SHARE for backwards-compat
  // with existing rows / backend validation. Only the operator-facing
  // label changed.
  COMMON_AREA_SHARE: "Additional Expenses",
  OTHER: "Additional Expenses",
};

/** Pretty month label for table titles — "June 2026" instead of
 *  "2026-06" so the maintainer's eye doesn't have to translate. */
const formatMonthLabel = (yyyyMm: string): string => {
  const [y, m] = yyyyMm.split("-").map(Number);
  if (!y || !m) return yyyyMm;
  const d = new Date(y, m - 1, 1);
  return d.toLocaleString("en-IN", { month: "long", year: "numeric" });
};

const currentMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

const STATUS_LABELS: Record<string, { label: string; tone: string }> = {
  NEW_FLAT: { label: "Not entered", tone: "bg-muted text-muted-foreground" },
  DUE: { label: "Due", tone: "bg-warning/20 text-warning" },
  OVERDUE: { label: "Overdue", tone: "bg-destructive/20 text-destructive" },
  PAID: { label: "Paid", tone: "bg-success/20 text-success" },
  WAIVED: { label: "Waived", tone: "bg-secondary text-secondary-foreground" },
};

/**
 * Maintainer landing page. Shows every society the caller manages
 * (most maintainers have exactly one, but the data model permits N)
 * and routes them into the per-building per-flat dashboard.
 *
 * <p>If the maintainer has exactly one society, this page auto-redirects
 * to that society's flat dashboard so they don't have to click a card.
 */
export function MaintainerHomePage() {
  const navigate = useNavigate();
  const societiesQ = useQuery({
    queryKey: ["my-societies"],
    queryFn: () => societyApi.mine(),
  });

  useEffect(() => {
    // Single-society maintainers — auto-redirect to that society's flats.
    if (societiesQ.data?.length === 1) {
      navigate(`/maintainer/${societiesQ.data[0].buildingId}/flats`, {
        replace: true,
      });
    }
  }, [societiesQ.data, navigate]);

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Maintainer dashboard"
        description="Record per-flat dues, mark payments received, log common-area expenses."
      />
      {societiesQ.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Skeleton className="h-32 rounded-2xl" />
          <Skeleton className="h-32 rounded-2xl" />
        </div>
      ) : !societiesQ.data?.length ? (
        <EmptyState
          variant="info"
          icon={Building2}
          title="No societies assigned to you yet"
          description="An owner has to promote you on their society page before you can manage it. Once they do, the building will appear here automatically."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {societiesQ.data.map((s) => (
            <Card key={s.buildingId}>
              <CardContent className="p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-display font-semibold text-base truncate">
                      {s.societyDisplayName ?? "(unnamed society)"}
                    </p>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Default ₹{s.defaultPerFlatAmount}/flat · due day{" "}
                      {s.monthlyDueDay}
                    </p>
                  </div>
                </div>
                <div className="mt-4">
                  <Button asChild variant="outline" size="sm">
                    <Link to={`/maintainer/${s.buildingId}/flats`}>
                      Open flat dashboard <ChevronRight className="size-4" />
                    </Link>
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Per-building flat dashboard. The maintainer's primary work surface:
 *
 * <ul>
 *   <li>Month selector at the top — defaults to the current calendar month.</li>
 *   <li>One row per flat showing tenant name, default amount, this
 *       month's amount, and status.</li>
 *   <li>"Set amount" dialog per row — edits the usage-based monthly
 *       amount + notes (water meter reading, gas reading, common-area
 *       share). Includes an optional "received" panel to mark PAID
 *       in the same submit when the maintainer collected cash on the
 *       spot.</li>
 * </ul>
 *
 * <p>The dashboard intentionally doesn't show common-area expenses —
 * those are entered via the existing expense flow accessed through a
 * separate menu item. Mixing the two would clutter the per-flat
 * working surface.
 */
export function MaintainerFlatsPage() {
  const { buildingId } = useParams<{ buildingId: string }>();
  const [month, setMonth] = useState<string>(currentMonth());

  const configQ = useQuery({
    queryKey: ["society", buildingId],
    queryFn: () => societyApi.get(buildingId!),
    enabled: !!buildingId,
  });

  const flatsQ = useQuery({
    queryKey: ["society-flats", buildingId, month],
    queryFn: () => societyApi.flatsForMonth(buildingId!, month),
    enabled: !!buildingId,
    staleTime: 15_000,
  });

  const ledgerQ = useQuery({
    queryKey: ["society-ledger", buildingId, month],
    queryFn: () => societyApi.ledger(buildingId!, month),
    enabled: !!buildingId,
    staleTime: 30_000,
  });

  // "Paid flats" is a per-FLAT metric, not a per-row one. V5
  // restructured the backend to emit one row per (flat, category) —
  // counting raw rows inflates the denominator (3 rows for a 2-flat
  // building where one flat has water + maintenance) and misleads
  // the operator. Group by flatId first:
  //   * denominator = total flats in the building. We count EVERY
  //     flat (incl. ones with no charges yet), because the operator
  //     thinks of the ratio as "how many of my flats are settled
  //     this month". A flat with no bills isn't settled — the
  //     maintainer just hasn't billed it yet.
  //   * numerator = flats that have at least one real charge AND
  //     every real charge is PAID. A flat with zero bills is NOT
  //     counted as paid (vacuously-true would be misleading).
  const summary = useMemo(() => {
    // Filter out vacant flats — they're hidden from the matrix and
    // wouldn't be paying anyway, so including them in the denominator
    // skews the "Paid flats 0/3" KPI (it should read "0 of OCCUPIED
    // flats" not "0 of EVERY flat").
    const rows = (flatsQ.data ?? []).filter(
      (r) => r.tenantUserId && r.tenantName !== "(vacant)",
    );
    const byFlat = new Map<string, typeof rows>();
    for (const r of rows) {
      const arr = byFlat.get(r.flatId) ?? [];
      arr.push(r);
      byFlat.set(r.flatId, arr);
    }
    const totalCount = byFlat.size;
    let paidCount = 0;
    for (const [, charges] of byFlat) {
      const real = charges.filter((r) => r.status !== "NEW_FLAT");
      if (real.length > 0 && real.every((r) => r.status === "PAID")) {
        paidCount++;
      }
    }
    return { paidCount, totalCount };
  }, [flatsQ.data]);

  if (!buildingId) {
    return (
      <EmptyState
        variant="info"
        icon={Building2}
        title="No building selected"
        description="Pick one of your societies from the dashboard."
      />
    );
  }

  return (
    <div className="animate-fade-in max-w-6xl">
      <PageHeader
        title={configQ.data?.societyDisplayName ?? "Society — flats"}
        description="Per-flat monthly dues, payments received, and a quick way to enter usage-based amounts (water, gas, common-area share)."
        actions={
          <div className="flex gap-2">
            <Button asChild variant="outline" size="sm">
              <Link to={`/maintainer/${buildingId}/expenses`}>
                Common expenses
              </Link>
            </Button>
            <Button asChild variant="ghost" size="sm">
              <Link to="/maintainer">← All societies</Link>
            </Button>
          </div>
        }
      />

      {/* Month selector */}
      <div className="flex items-center gap-3 mb-4">
        <Calendar className="size-4 text-muted-foreground" />
        <Input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value || currentMonth())}
          className="w-48"
        />
      </div>

      {/* Common bank account — owner OR maintainer can edit */}
      {configQ.data && (
        <SocietyBankPanel buildingId={buildingId} config={configQ.data} />
      )}

      {/* Shareable read-only ledger URL — maintainer paste-shares this
          into the residents' WhatsApp group so anyone with the link
          gets the public expense view without registering. Same URL
          the owner sees on their society page. */}
      {configQ.data?.publicViewUrl && (
        <BuildingExpenseViewerShare url={configQ.data.publicViewUrl} />
      )}

      {/* Dual-approval pending claims — competing maintainers who want
          to take over this society. The current maintainer's approval
          is half of the two-party gate (owner approval being the other
          half). Card hides when nothing's pending. */}
      <MaintainerPendingClaimsWidget />

      {/* KPI strip */}
      <div className="grid gap-4 sm:grid-cols-4 mb-6">
        <Kpi
          icon={HandCoins}
          label="Collected"
          value={formatINR(ledgerQ.data?.collectedThisMonth ?? 0)}
          tone="success"
        />
        <Kpi
          icon={Wrench}
          label="Total Dues"
          value={formatINR(ledgerQ.data?.outstandingThisMonth ?? 0)}
          tone="destructive"
        />
        <Kpi
          icon={Droplets}
          label="Expenses"
          value={formatINR(ledgerQ.data?.expensesThisMonth ?? 0)}
          tone="muted"
        />
        <Kpi
          icon={CheckCircle2}
          label="Paid flats"
          value={
            summary.totalCount
              ? `${summary.paidCount} / ${summary.totalCount}`
              : "—"
          }
          tone="muted"
        />
      </div>

      {/* Per-flat list */}
      <Card>
        <CardContent className="p-6">
          <h3 className="font-display font-semibold text-lg mb-4">
            Flat charges — {formatMonthLabel(month)}
          </h3>

          {flatsQ.isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-16 rounded-xl" />
              <Skeleton className="h-16 rounded-xl" />
              <Skeleton className="h-16 rounded-xl" />
            </div>
          ) : !flatsQ.data?.length ? (
            <EmptyState
              variant="info"
              icon={Building2}
              title="No flats in this building"
              description="The owner needs to add flats before any per-flat dues exist."
            />
          ) : (
            <FlatsTable
              // Hide vacant flats from the matrix — the maintainer
              // doesn't bill an empty unit, and listing them clutters
              // the view (especially in mostly-empty buildings).
              // Backend still sends them; we filter client-side so
              // they reappear automatically once a tenant moves in.
              groups={groupRowsByFlat(flatsQ.data).filter(
                (g) => g.tenantUserId && g.tenantName !== "(vacant)",
              )}
              buildingId={buildingId}
              month={month}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}

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
      <CardContent className="p-4">
        <div className="flex items-center gap-2 text-[11px] uppercase tracking-wider text-muted-foreground">
          <Icon className="size-3.5" />
          {label}
        </div>
        <p className={`font-display font-bold text-xl mt-1.5 ${toneClass}`}>
          {value}
        </p>
      </CardContent>
    </Card>
  );
}

/**
 * Group the flat-row list returned by the backend into one entry per
 * flat with all its line items collected together. The backend already
 * emits placeholder NEW_FLAT rows for flats with no charges yet, so
 * those flats end up with a single-item group whose only row is the
 * placeholder.
 */
type FlatGroup = {
  flatId: string;
  flatNumber: string;
  tenantUserId: string | null;
  tenantName: string;
  defaultAmount: number;
  /** All charges this flat carries for the month, or a single
   *  placeholder row with status=NEW_FLAT when no charges exist. */
  rows: FlatMaintenanceRow[];
};

function groupRowsByFlat(rows: FlatMaintenanceRow[]): FlatGroup[] {
  const byFlat = new Map<string, FlatGroup>();
  for (const r of rows) {
    let g = byFlat.get(r.flatId);
    if (!g) {
      g = {
        flatId: r.flatId,
        flatNumber: r.flatNumber,
        tenantUserId: r.tenantUserId,
        tenantName: r.tenantName,
        defaultAmount: r.defaultAmount,
        rows: [],
      };
      byFlat.set(r.flatId, g);
    }
    g.rows.push(r);
  }
  return Array.from(byFlat.values());
}

/**
 * Synthesise a NEW_FLAT-equivalent row for the Add-charge dialog. Used
 * by every Add-charge action in the table — picks the row that the
 * SetAmountDialog opens in create-mode, with the dialog's category
 * dropdown pre-filtered to skip categories the flat already has.
 */
function makePlaceholderRow(group: FlatGroup): FlatMaintenanceRow {
  return {
    collectionId: null,
    flatId: group.flatId,
    flatNumber: group.flatNumber,
    tenantUserId: group.tenantUserId,
    tenantName: group.tenantName,
    monthAmount: group.defaultAmount,
    status: "NEW_FLAT",
    defaultAmount: group.defaultAmount,
    forMonth: "",
    notes: null,
    paidOn: null,
    paidVia: null,
    amountPaid: null,
    category: null,
  };
}

/**
 * The order of category columns in the matrix view.
 *
 * <p>GAS_BILL and ELECTRICITY are intentionally excluded — those
 * utilities are billed directly to each flat's individual meter by
 * the utility provider in India, not collected by the society. Keep
 * the values in the {@link FlatChargeCategory} type for backwards
 * compat with any rows that already used them, but drop them from
 * the operator-facing dashboard so the maintainer isn't tempted to
 * double-bill a tenant.
 *
 * <p>OTHER was also dropped — the maintainer asked for a single
 * "Additional Expenses" bucket and a computed "Total" column at the
 * right edge. Existing OTHER rows still load into the COMMON_AREA_SHARE
 * cell because both labels resolve to "Additional Expenses" in
 * {@link CATEGORY_LABELS}, but new entries always use COMMON_AREA_SHARE.
 */
/**
 * Categories that get a dedicated column on the per-flat matrix view.
 * COMMON_AREA_SHARE was dropped — the maintainer asked for the matrix
 * to focus on actual category charges plus the new water-meter usage
 * columns; "Additional Expenses" wasn't being used in practice. The
 * enum value still exists server-side so historical rows render fine
 * elsewhere; just no dedicated column here.
 */
const CATEGORY_COLUMNS: FlatChargeCategory[] = [
  "MAINTENANCE",
  "WATER_BILL",
];

/**
 * Matrix view of all flats × all charge categories. Rows = flats,
 * columns = categories. Each cell either shows the recorded charge
 * (amount + status pill + click-to-edit) or an empty "+" affordance
 * that opens the Add-charge dialog scoped to that exact category.
 *
 * <p>The table overflows horizontally on small screens — wrapped in
 * a div with overflow-x-auto so the layout stays readable on mobile
 * without forcing a separate stacked view.
 */
function FlatsTable({
  groups,
  buildingId,
  month,
}: {
  groups: FlatGroup[];
  buildingId: string;
  month: string;
}) {
  return (
    <div className="rounded-xl border border-border/60 overflow-x-auto">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="bg-secondary/40 border-b border-border/60">
            <th className="text-left px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground">
              Flat
            </th>
            <th className="text-left px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground">
              Name
            </th>
            {CATEGORY_COLUMNS.map((c) => (
              <th
                key={c}
                className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap"
              >
                {CATEGORY_LABELS[c]}
              </th>
            ))}
            {/* Water-meter readings — only meaningful for WATER_BILL
              * rows but the columns sit next to the Water bill cell so
              * the reader can compute (curr - prev) at a glance. Empty
              * cells when there's no WATER_BILL row for the flat. */}
            <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Previous Usage
            </th>
            <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Current Usage
            </th>
            <th className="text-right px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Total
            </th>
            <th className="text-center px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Paid
            </th>
            <th className="text-right px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Dues
            </th>
            <th className="text-left px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Remarks
            </th>
          </tr>
        </thead>
        <tbody>
          {groups.map((group) => (
            <FlatRow
              key={group.flatId}
              group={group}
              buildingId={buildingId}
              month={month}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** One row of the FlatsTable — a single flat with one cell per
 *  category, plus the right-edge money summary. Layout: Flat, Name,
 *  Maintenance, Water bill, Additional Expenses, Total (= sum of
 *  category cells), Paid (Yes/No clickable toggle), Dues (Total - Paid),
 *  Remarks (inline-editable note). */
function FlatRow({
  group,
  buildingId,
  month,
}: {
  group: FlatGroup;
  buildingId: string;
  month: string;
}) {
  // Index the flat's charges by category for O(1) lookup per cell.
  // Legacy OTHER rows are surfaced in the COMMON_AREA_SHARE cell
  // (both labels resolve to "Additional Expenses"). If a flat has
  // BOTH OTHER and COMMON_AREA_SHARE rows, COMMON_AREA_SHARE wins —
  // OTHER is the older, deprecated bucket.
  const byCategory = new Map<FlatChargeCategory, FlatMaintenanceRow>();
  for (const r of group.rows) {
    if (r.status === "NEW_FLAT" || !r.category) continue;
    const key: FlatChargeCategory =
      r.category === "OTHER" ? "COMMON_AREA_SHARE" : r.category;
    // First-write-wins is wrong (we'd lose a fresh COMMON_AREA_SHARE
    // to an older OTHER). Resolve preference explicitly.
    const existing = byCategory.get(key);
    if (!existing) {
      byCategory.set(key, r);
    } else if (existing.category === "OTHER" && r.category === "COMMON_AREA_SHARE") {
      byCategory.set(key, r);
    }
  }
  // Money columns on the right edge:
  //   * total      = sum of all non-NEW_FLAT non-WAIVED row amounts
  //                  (this is what the new "Total" column shows).
  //   * paid       = sum of recorded payments.
  //   * dues       = total - paid (what was "Balance" before).
  const realRows = group.rows.filter(
    (r) => r.status !== "NEW_FLAT" && r.status !== "WAIVED",
  );
  const total = realRows.reduce((s, r) => s + r.monthAmount, 0);
  const paid = group.rows
    .filter((r) => r.status === "PAID")
    .reduce((s, r) => s + (r.amountPaid ?? r.monthAmount), 0);
  const dues = total - paid;

  // Paid-Yes/No semantics: a flat is "Yes" when every billed row this
  // month is PAID (matches the per-flat-paid logic in the KPI strip
  // above). "No" otherwise. NEW_FLAT placeholders mean nothing is
  // billed — show "—".
  const billedRows = group.rows.filter(
    (r) => r.status !== "NEW_FLAT" && r.status !== "WAIVED",
  );
  const allPaid =
    billedRows.length > 0 && billedRows.every((r) => r.status === "PAID");
  const hasAny = billedRows.length > 0;

  // Remarks: surface the first non-empty notes value across the flat's
  // rows for read; the inline editor below saves back into the row's
  // notes via upsertFlatCollection. The maintainer typically uses
  // remarks as a single message about the whole month, so first-wins
  // is fine; an explicit edit overwrites just that row's notes.
  const remarkRow = group.rows.find((r) => r.notes) ?? group.rows[0];
  const remarkText = remarkRow?.notes ?? "";

  return (
    <tr className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20">
      {/* Flat number */}
      <td className="px-3 py-2 align-top">
        <Badge variant="outline" className="font-mono text-[11px]">
          {group.flatNumber}
        </Badge>
      </td>

      {/* Name (was 'Tenant') */}
      <td className="px-3 py-2 align-top">
        <span className="text-sm">{group.tenantName}</span>
      </td>

      {/* One cell per category */}
      {CATEGORY_COLUMNS.map((c) => {
        const row = byCategory.get(c);
        return (
          <td key={c} className="px-3 py-2 align-top">
            <CategoryCell
              row={row}
              group={group}
              category={c}
              buildingId={buildingId}
              month={month}
            />
          </td>
        );
      })}

      {/* Previous Usage — readings live on the WATER_BILL row (if any).
        * Showing this column unconditionally even when there's no
        * water-bill yet, with a "—" placeholder, so the column count
        * stays stable across rows / months. */}
      <td className="px-3 py-2 align-top text-right">
        {byCategory.get("WATER_BILL")?.prevUsageReading != null ? (
          <span className="font-mono text-sm whitespace-nowrap">
            {byCategory.get("WATER_BILL")!.prevUsageReading}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Current Usage */}
      <td className="px-3 py-2 align-top text-right">
        {byCategory.get("WATER_BILL")?.currUsageReading != null ? (
          <span className="font-mono text-sm whitespace-nowrap">
            {byCategory.get("WATER_BILL")!.currUsageReading}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Total (= sum across category cells, was 'Other' column) */}
      <td className="px-3 py-2 align-top text-right">
        {total > 0 ? (
          <span className="font-semibold font-display whitespace-nowrap">
            {formatINR(total)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Paid — Yes/No clickable toggle */}
      <td className="px-3 py-2 align-top text-center">
        {hasAny ? (
          <PaidToggle
            buildingId={buildingId}
            flatId={group.flatId}
            month={month}
            rows={billedRows}
            allPaid={allPaid}
          />
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Dues (was 'Balance') */}
      <td className="px-3 py-2 align-top text-right">
        {total > 0 ? (
          <span
            className={`font-semibold whitespace-nowrap ${
              dues <= 0 ? "text-success" : "text-destructive"
            }`}
          >
            {formatINR(dues)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Remarks — inline editable */}
      <td className="px-3 py-2 align-top">
        {remarkRow ? (
          <RemarksCell
            buildingId={buildingId}
            row={remarkRow}
            month={month}
            initial={remarkText}
          />
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
    </tr>
  );
}

/**
 * Click-to-toggle Yes/No for the per-flat Paid column. Flipping
 * Yes→No marks every billed row this month as DUE; flipping No→Yes
 * marks every billed row as PAID. The fan-out runs sequentially via
 * upsertFlatCollection — this is the same path the per-cell dialog
 * uses, just batched. Reasonably cheap: each flat has 1-4 rows.
 */
function PaidToggle({
  buildingId,
  flatId,
  month,
  rows,
  allPaid,
}: {
  buildingId: string;
  flatId: string;
  month: string;
  rows: FlatMaintenanceRow[];
  allPaid: boolean;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);

  const onClick = async () => {
    if (busy) return;
    const target: CollectionStatus = allPaid ? "DUE" : "PAID";
    setBusy(true);
    try {
      // One upsert per row. We don't have a bulk endpoint, but in
      // practice a flat has 1-3 rows per month so the round-trip
      // cost is small.
      for (const r of rows) {
        if (!r.category) continue; // can't upsert without a category
        await societyApi.upsertFlatCollection(buildingId, flatId, {
          forMonth: month,
          amountDue: r.monthAmount,
          status: target,
          category: r.category,
          notes: r.notes ?? "",
          paidOn: target === "PAID" ? new Date().toISOString().slice(0, 10) : undefined,
          amountPaid: target === "PAID" ? r.monthAmount : undefined,
          paidVia: target === "PAID" ? r.paidVia ?? "CASH" : undefined,
        });
      }
      qc.invalidateQueries({ queryKey: ["society-flats", buildingId] });
      qc.invalidateQueries({ queryKey: ["society-ledger", buildingId] });
      toast({
        title: target === "PAID" ? "Marked as paid." : "Marked as due.",
      });
    } catch (err) {
      toast({
        title: "Couldn't update",
        description: extractErrorMessage(err),
        variant: "destructive",
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      disabled={busy}
      onClick={onClick}
      className={`inline-flex items-center justify-center rounded-full px-3 py-0.5 text-[11px] font-semibold uppercase tracking-wider transition-colors disabled:opacity-50 ${
        allPaid
          ? "bg-success/20 text-success hover:bg-success/30"
          : "bg-destructive/20 text-destructive hover:bg-destructive/30"
      }`}
      title={allPaid ? "Click to mark as unpaid" : "Click to mark all paid"}
    >
      {busy ? "…" : allPaid ? "Yes" : "No"}
    </button>
  );
}

/**
 * Inline Remarks cell. Click to expand into a textarea + Save/Cancel.
 * Writes back into the row's notes via upsertFlatCollection — the
 * same mutation the SetAmountDialog uses, just scoped to the notes
 * field. We don't try to keep notes synchronised across sibling rows;
 * the maintainer edits one row's note, that's what changes.
 */
function RemarksCell({
  buildingId,
  row,
  month,
  initial,
}: {
  buildingId: string;
  row: FlatMaintenanceRow;
  month: string;
  initial: string;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(initial);
  const [busy, setBusy] = useState(false);

  // Re-sync local value when the prop changes (e.g. after a query
  // refetch shows a fresh note that another tab wrote).
  useEffect(() => {
    if (!editing) setValue(initial);
  }, [initial, editing]);

  const save = async () => {
    if (!row.category) {
      // No category = NEW_FLAT placeholder; we can't upsert it
      // without an amount + category. Tell the maintainer to add a
      // charge first.
      toast({
        title: "Add a charge first",
        description: "Remarks attach to an existing charge row.",
      });
      setEditing(false);
      return;
    }
    setBusy(true);
    try {
      await societyApi.upsertFlatCollection(buildingId, row.flatId, {
        forMonth: month,
        amountDue: row.monthAmount,
        status: (row.status === "NEW_FLAT" ? "DUE" : row.status) as CollectionStatus,
        category: row.category,
        notes: value,
        paidOn: row.paidOn ?? undefined,
        amountPaid: row.amountPaid ?? undefined,
        paidVia: row.paidVia ?? undefined,
      });
      qc.invalidateQueries({ queryKey: ["society-flats", buildingId] });
      toast({ title: "Remark saved." });
      setEditing(false);
    } catch (err) {
      toast({
        title: "Couldn't save",
        description: extractErrorMessage(err),
        variant: "destructive",
      });
    } finally {
      setBusy(false);
    }
  };

  if (editing) {
    return (
      <div className="flex flex-col gap-1 min-w-[180px]">
        <Textarea
          rows={2}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="e.g. agreed cash on 5th"
          className="text-xs"
        />
        <div className="flex gap-1">
          <Button
            size="sm"
            variant="gradient"
            disabled={busy}
            onClick={save}
            className="h-6 px-2 text-[10px]"
          >
            {busy ? "…" : "Save"}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            disabled={busy}
            onClick={() => {
              setValue(initial);
              setEditing(false);
            }}
            className="h-6 px-2 text-[10px]"
          >
            Cancel
          </Button>
        </div>
      </div>
    );
  }

  return (
    <button
      type="button"
      className="text-left text-xs text-muted-foreground hover:text-foreground hover:bg-secondary/60 rounded px-1.5 py-0.5 max-w-[200px] truncate w-full"
      onClick={() => setEditing(true)}
      title={initial || "Add a remark"}
    >
      {initial || <span className="italic opacity-60">+ Add note</span>}
    </button>
  );
}

/**
 * One cell of the FlatsTable for a specific (flat, category). Either:
 * — Empty (no row yet) → renders a subtle "+ Add" link that opens the
 *   SetAmountDialog in create-mode, pre-selecting this category.
 * — Populated → renders amount + status pill, click-to-edit.
 */
function CategoryCell({
  row,
  group,
  category,
  buildingId,
  month,
}: {
  row: FlatMaintenanceRow | undefined;
  group: FlatGroup;
  category: FlatChargeCategory;
  buildingId: string;
  month: string;
}) {
  if (!row) {
    // Add-mode: synthesize a placeholder row scoped to this exact
    // category. The dialog opens with the category pre-selected (via
    // initialCategory) AND with every OTHER category disabled in the
    // dropdown — so the maintainer can only confirm/cancel for this
    // specific cell.
    const placeholder = { ...makePlaceholderRow(group), category };
    return (
      <SetAmountDialog
        row={placeholder}
        buildingId={buildingId}
        month={month}
        disabledCategories={
          new Set(
            CATEGORY_COLUMNS.filter((c) => c !== category),
          )
        }
        triggerLabel="Add"
        triggerIcon={<Plus className="size-3 -mr-1" />}
      />
    );
  }

  const status = STATUS_LABELS[row.status] ?? STATUS_LABELS.NEW_FLAT;
  return (
    <SetAmountDialog
      row={row}
      buildingId={buildingId}
      month={month}
      triggerNode={
        <button
          type="button"
          className="text-left w-full hover:bg-secondary/60 rounded-md px-1 py-0.5 transition-colors"
          title={row.notes ?? `${CATEGORY_LABELS[category]}: ${formatINR(row.monthAmount)}`}
        >
          <span className="font-semibold font-display text-sm">
            {formatINR(row.monthAmount)}
          </span>
          <span
            className={`block mt-0.5 rounded-full text-[9px] font-semibold uppercase tracking-wide px-1.5 py-0 w-fit ${status.tone}`}
          >
            {status.label}
          </span>
        </button>
      }
    />
  );
}

function SetAmountDialog({
  row,
  buildingId,
  month,
  disabledCategories,
  triggerLabel,
  triggerIcon,
  triggerNode,
}: {
  row: FlatMaintenanceRow;
  buildingId: string;
  month: string;
  /** Categories already used by sibling rows on the same (flat, month).
   *  In add-mode the dropdown disables these so the maintainer can't
   *  trip the (flat, month, category) unique constraint by adding a
   *  duplicate. Edit-mode passes undefined (the row's own category
   *  is always selectable). */
  disabledCategories?: Set<FlatChargeCategory>;
  triggerLabel?: string;
  triggerIcon?: React.ReactNode;
  /** Bring-your-own trigger element. When provided, the default
   *  Button trigger is replaced entirely — used by table cells so the
   *  click target can be the whole cell, not a separate button. */
  triggerNode?: React.ReactNode;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);

  // Initial category: on edit, the row's existing category; on add,
  // the first category NOT already used (so the dropdown opens to a
  // sensible default instead of one the user can't pick).
  const initialCategory = (): FlatChargeCategory => {
    if (row.category) return row.category;
    if (disabledCategories) {
      for (const c of Object.keys(CATEGORY_LABELS) as FlatChargeCategory[]) {
        if (!disabledCategories.has(c)) return c;
      }
    }
    return "MAINTENANCE";
  };

  const [form, setForm] = useState<UpsertFlatCollectionRequest>({
    forMonth: month,
    amountDue:
      row.status === "NEW_FLAT" ? row.defaultAmount : row.monthAmount,
    status: (row.status === "NEW_FLAT" ? "DUE" : row.status) as CollectionStatus,
    category: initialCategory(),
    notes: row.notes ?? "",
    paidOn: row.paidOn ?? undefined,
    amountPaid: row.amountPaid ?? undefined,
    paidVia: row.paidVia ?? undefined,
    prevUsageReading: row.prevUsageReading ?? undefined,
    currUsageReading: row.currUsageReading ?? undefined,
  });

  // Track whether the maintainer is editing the "received" sub-panel.
  // Default to closed unless the row is already PAID — saves a click
  // for the 90% case of "just enter dues; tenant will pay later".
  const [showReceived, setShowReceived] = useState(row.status === "PAID");

  const upsertMut = useMutation({
    mutationFn: (req: UpsertFlatCollectionRequest) =>
      societyApi.upsertFlatCollection(buildingId, row.flatId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society-flats", buildingId] });
      qc.invalidateQueries({ queryKey: ["society-ledger", buildingId] });
      toast({ title: "Flat updated." });
      setOpen(false);
    },
    onError: (err) =>
      toast({
        title: "Couldn't save",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  const isNew = row.status === "NEW_FLAT";

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (o) {
          // Reset form to row's current state on every open so we
          // never carry over stale state from a previous flat.
          setForm({
            forMonth: month,
            amountDue:
              row.status === "NEW_FLAT" ? row.defaultAmount : row.monthAmount,
            status: (row.status === "NEW_FLAT"
              ? "DUE"
              : row.status) as CollectionStatus,
            category: initialCategory(),
            notes: row.notes ?? "",
            paidOn: row.paidOn ?? undefined,
            amountPaid: row.amountPaid ?? undefined,
            paidVia: row.paidVia ?? undefined,
            prevUsageReading: row.prevUsageReading ?? undefined,
            currUsageReading: row.currUsageReading ?? undefined,
          });
          setShowReceived(row.status === "PAID");
        }
      }}
    >
      <DialogTrigger asChild>
        {triggerNode ?? (
          <Button variant="outline" size="sm">
            {triggerIcon ?? (isNew ? <Plus className="size-3.5" /> : <Pencil className="size-3.5" />)}
            {triggerLabel ?? (isNew ? "Set amount" : "Edit")}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            Flat {row.flatNumber} · {month}
          </DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          {row.tenantName === "(vacant)"
            ? "This flat is vacant — entering a charge anyway will keep the running total accurate if it becomes occupied mid-month."
            : `Recording maintenance for ${row.tenantName}.`}
        </p>

        <div className="space-y-3">
          <div>
            <Label>Charge type</Label>
            <Select
              value={form.category ?? "MAINTENANCE"}
              onValueChange={(v) =>
                setForm({ ...form, category: v as FlatChargeCategory })
              }
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {CATEGORY_COLUMNS.map((k) => {
                  // In add-mode, hide categories this flat already has
                  // a row for — the (flat, month, category) unique
                  // constraint would otherwise reject the upsert.
                  // CATEGORY_COLUMNS already excludes GAS_BILL +
                  // ELECTRICITY (utility-meter charges that aren't
                  // collected by the society).
                  const used = disabledCategories?.has(k);
                  if (used) return null;
                  return (
                    <SelectItem key={k} value={k}>
                      {CATEGORY_LABELS[k]}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
            <p className="text-[10px] text-muted-foreground mt-0.5">
              Tag this entry so tenants can see exactly what they're being
              billed for.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Amount due (₹)</Label>
              <Input
                type="number"
                min={0}
                step={1}
                placeholder="0"
                // Empty display when 0 so the maintainer can just type
                // the new amount without having to delete the leading 0.
                value={form.amountDue || ""}
                onChange={(e) =>
                  setForm({
                    ...form,
                    amountDue: Number(e.target.value) || 0,
                  })
                }
              />
              <p className="text-[10px] text-muted-foreground mt-0.5">
                Default {formatINR(row.defaultAmount)} — override based on
                usage if needed.
              </p>
            </div>
            <div>
              <Label>Status</Label>
              <Select
                value={form.status ?? "DUE"}
                onValueChange={(v) => {
                  const s = v as CollectionStatus;
                  setForm({ ...form, status: s });
                  setShowReceived(s === "PAID");
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="DUE">Due</SelectItem>
                  <SelectItem value="OVERDUE">Overdue</SelectItem>
                  <SelectItem value="PAID">Paid</SelectItem>
                  <SelectItem value="WAIVED">Waived</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Water-meter readings — only visible for WATER_BILL rows.
            * Lets the maintainer record the start + end meter readings
            * so every resident can verify the bill themselves (curr -
            * prev = units consumed). Both optional — leave blank if
            * billing on a flat share without a meter. */}
          {form.category === "WATER_BILL" && (
            <div className="rounded-xl border border-primary/30 bg-primary/5 p-3 space-y-2">
              <p className="text-xs font-semibold text-primary uppercase tracking-wider">
                Water meter readings
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>Previous Usage</Label>
                  <Input
                    type="number"
                    min={0}
                    step="0.01"
                    placeholder="e.g. 1240"
                    value={form.prevUsageReading ?? ""}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        prevUsageReading:
                          e.target.value === ""
                            ? undefined
                            : Number(e.target.value),
                      })
                    }
                  />
                </div>
                <div>
                  <Label>Current Usage</Label>
                  <Input
                    type="number"
                    min={0}
                    step="0.01"
                    placeholder="e.g. 1280"
                    value={form.currUsageReading ?? ""}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        currUsageReading:
                          e.target.value === ""
                            ? undefined
                            : Number(e.target.value),
                      })
                    }
                  />
                </div>
              </div>
              {form.prevUsageReading != null &&
                form.currUsageReading != null && (
                  <p className="text-[11px] text-muted-foreground">
                    Units consumed:{" "}
                    <span className="font-semibold text-foreground">
                      {(form.currUsageReading - form.prevUsageReading).toFixed(
                        2,
                      )}
                    </span>
                  </p>
                )}
            </div>
          )}

          <div>
            <Label>Line-item notes</Label>
            <Textarea
              rows={2}
              placeholder="e.g. water 200 + gas 150 + common-area share 100"
              value={form.notes ?? ""}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
            />
            <p className="text-[10px] text-muted-foreground mt-0.5">
              Shown to the tenant on their ledger — keep it short and
              specific so they understand the breakdown.
            </p>
          </div>

          {showReceived && (
            <div className="rounded-xl border border-success/30 bg-success/5 p-3 space-y-3">
              <p className="text-xs font-semibold text-success uppercase tracking-wider">
                Payment received
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>Paid on</Label>
                  <Input
                    type="date"
                    value={form.paidOn ?? ""}
                    onChange={(e) =>
                      setForm({ ...form, paidOn: e.target.value })
                    }
                  />
                </div>
                <div>
                  <Label>Amount paid (₹)</Label>
                  <Input
                    type="number"
                    min={0}
                    step={1}
                    placeholder="0"
                    // Same "empty when 0" treatment as Amount due —
                    // also pre-fills with amountDue when paidAmount
                    // isn't set yet (the common case: "they paid the
                    // full amount").
                    value={form.amountPaid ?? form.amountDue ?? ""}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        amountPaid: Number(e.target.value) || 0,
                      })
                    }
                  />
                </div>
              </div>
              <div>
                <Label>Paid via</Label>
                <Select
                  value={form.paidVia ?? ""}
                  onValueChange={(v) => setForm({ ...form, paidVia: v })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Cash / NEFT / UPI…" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CASH">Cash</SelectItem>
                    <SelectItem value="NEFT">NEFT / Bank transfer</SelectItem>
                    <SelectItem value="UPI_MANUAL">UPI</SelectItem>
                    <SelectItem value="OTHER">Other</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            disabled={upsertMut.isPending || form.amountDue === undefined}
            onClick={() => upsertMut.mutate(form)}
          >
            {upsertMut.isPending
              ? "Saving…"
              : isNew
                ? "Save"
                : "Update"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Dual-approval pending-requests widget for the maintainer dashboard.
 * Mirrors the existing owner-side PendingClaimsWidget but scoped to
 * the claims where the current maintainer's vote is required (i.e.
 * MAINTAINER reassign claims targeting buildings this user runs).
 * Self-hides when nothing's pending.
 */
function MaintainerPendingClaimsWidget() {
  const qc = useQueryClient();
  const { toast: tst } = useToast();
  const pendingQ = useQuery({
    queryKey: ["maintainer-pending-claims"],
    queryFn: () => claimsApi.pendingForMaintainer(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  const approveMut = useMutation({
    mutationFn: (claimId: string) => claimsApi.approve(claimId),
    onSuccess: (claim) => {
      qc.invalidateQueries({ queryKey: ["maintainer-pending-claims"] });
      qc.invalidateQueries({ queryKey: ["society"] });
      // The owner ALSO has to approve for the swap to actually fire.
      // Tell the maintainer that explicitly so they know it's not
      // immediately effective.
      tst({
        title:
          claim.status === "APPROVED"
            ? "Approved — handover complete."
            : "Your approval recorded.",
        description:
          claim.status === "APPROVED"
            ? `${claim.applicantName ?? "The new maintainer"} now manages this society.`
            : "Waiting for the owner's approval before the change takes effect.",
      });
    },
    onError: (e) =>
      tst({
        title: "Couldn't approve",
        description: extractErrorMessage(e),
        variant: "destructive",
      }),
  });

  const rejectMut = useMutation({
    mutationFn: (claimId: string) => claimsApi.reject(claimId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["maintainer-pending-claims"] });
      tst({ title: "Request rejected." });
    },
    onError: (e) =>
      tst({
        title: "Couldn't reject",
        description: extractErrorMessage(e),
        variant: "destructive",
      }),
  });

  const pending = pendingQ.data ?? [];
  if (!pendingQ.isLoading && pending.length === 0) return null;

  return (
    <Card className="mb-6 border-warning/40">
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3 mb-3">
          <div>
            <h3 className="font-display font-semibold text-base">
              Maintainer handover requests
            </h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              People applying to take over as the maintainer. Both you
              and the building owner must approve before the change
              takes effect.
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
                  <p className="font-semibold text-sm truncate">
                    {c.applicantName ?? c.applicantEmail ?? "Applicant"}
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    For{" "}
                    <span className="font-medium">
                      {c.buildingName ?? "your building"}
                    </span>
                    {c.applicantEmail && (
                      <>
                        {" · "}
                        <span className="font-mono">{c.applicantEmail}</span>
                      </>
                    )}
                  </p>
                  {/* Two-party state — show which side has already acted. */}
                  <p className="text-[11px] mt-1.5">
                    <span
                      className={
                        c.ownerApproved
                          ? "text-success font-semibold"
                          : "text-muted-foreground"
                      }
                    >
                      Owner: {c.ownerApproved ? "approved" : "pending"}
                    </span>
                    {"  ·  "}
                    <span
                      className={
                        c.maintainerApproved
                          ? "text-success font-semibold"
                          : "text-muted-foreground"
                      }
                    >
                      You: {c.maintainerApproved ? "approved" : "pending"}
                    </span>
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
                      c.maintainerApproved ||
                      (rejectMut.isPending && rejectMut.variables === c.id)
                    }
                    onClick={() => {
                      if (
                        confirm(
                          `Reject ${c.applicantName ?? "this person"}'s request to take over as maintainer? This kills the request immediately — the owner won't be able to approve it.`,
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
                      c.maintainerApproved ||
                      (approveMut.isPending && approveMut.variables === c.id)
                    }
                    onClick={() => {
                      const msg = c.ownerApproved
                        ? `The owner has already approved this. Your approval will complete the handover — ${c.applicantName ?? "this person"} becomes the maintainer immediately.`
                        : `Approve ${c.applicantName ?? "this person"} as your replacement? The owner still has to approve too before the handover takes effect.`;
                      if (confirm(msg)) {
                        approveMut.mutate(c.id);
                      }
                    }}
                  >
                    {c.maintainerApproved ? "Already approved" : "Approve"}
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

/**
 * Shareable read-only ledger URL panel for the maintainer dashboard.
 * Mirrors the same surface the owner has on /owner/buildings/:id/society
 * — same underlying public_view_token, same Copy-link button. The
 * Rotate button is deliberately NOT exposed here; only the owner
 * should be able to invalidate the URL (rotation makes the old link
 * stop working for everyone, and that's an owner-level governance
 * call).
 */
function BuildingExpenseViewerShare({ url }: { url: string }) {
  const { toast } = useToast();
  return (
    <Card className="mb-6">
      <CardContent className="p-5">
        <p className="text-xs uppercase tracking-wider text-muted-foreground font-mono">
          Building Expense Viewer
        </p>
        <p
          className="text-sm font-mono mt-1 truncate"
          title={url}
        >
          {url}
        </p>
        <p className="text-[11px] text-muted-foreground mt-1.5">
          Share this link with residents. Anyone with the URL sees a
          read-only ledger of expenses and the fund balance — no login
          required.
        </p>
        <div className="mt-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              navigator.clipboard.writeText(url);
              toast({
                title: "Link copied",
                description: "Paste it into the residents' WhatsApp group.",
              });
            }}
          >
            Copy link
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
