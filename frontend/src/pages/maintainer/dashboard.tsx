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
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
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
    const rows = flatsQ.data ?? [];
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
          label="Outstanding"
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
            Flats — {month}
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
              groups={groupRowsByFlat(flatsQ.data)}
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
 */
const CATEGORY_COLUMNS: FlatChargeCategory[] = [
  "MAINTENANCE",
  "WATER_BILL",
  "COMMON_AREA_SHARE",
  "OTHER",
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
              Tenant
            </th>
            {CATEGORY_COLUMNS.map((c) => (
              <th
                key={c}
                className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap"
              >
                {CATEGORY_LABELS[c]}
              </th>
            ))}
            <th className="text-right px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Paid
            </th>
            <th className="text-right px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Outstanding
            </th>
            <th className="text-right px-3 py-2 font-semibold text-xs uppercase tracking-wider text-muted-foreground whitespace-nowrap">
              Balance
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
 *  category, plus the Outstanding total at the right. */
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
  const byCategory = new Map<FlatChargeCategory, FlatMaintenanceRow>();
  for (const r of group.rows) {
    if (r.status !== "NEW_FLAT" && r.category) {
      byCategory.set(r.category, r);
    }
  }
  // Money columns on the right edge:
  //   * paid       = sum of recorded payments (amountPaid where the
  //                  maintainer captured it, else fall back to the
  //                  amount due on rows the maintainer marked PAID).
  //   * outstanding = what's still owed (DUE + OVERDUE).
  //   * balance    = total this month - what's been paid. Visually it
  //                  matches `outstanding` once WAIVED rows aren't in
  //                  play; we render both so the operator can quickly
  //                  cross-check the math.
  const paid = group.rows
    .filter((r) => r.status === "PAID")
    .reduce((s, r) => s + (r.amountPaid ?? r.monthAmount), 0);
  const outstanding = group.rows
    .filter((r) => r.status === "DUE" || r.status === "OVERDUE")
    .reduce((s, r) => s + r.monthAmount, 0);
  // Total amount this month across DUE/OVERDUE/PAID (WAIVED rows
  // are excluded since they were explicitly forgiven).
  const totalBilled = group.rows
    .filter((r) => r.status !== "NEW_FLAT" && r.status !== "WAIVED")
    .reduce((s, r) => s + r.monthAmount, 0);
  const balance = totalBilled - paid;

  return (
    <tr className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20">
      {/* Flat number */}
      <td className="px-3 py-2 align-top">
        <Badge variant="outline" className="font-mono text-[11px]">
          {group.flatNumber}
        </Badge>
      </td>

      {/* Tenant */}
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

      {/* Paid */}
      <td className="px-3 py-2 align-top text-right">
        {paid > 0 ? (
          <span className="font-semibold text-success whitespace-nowrap">
            {formatINR(paid)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Outstanding */}
      <td className="px-3 py-2 align-top text-right">
        {outstanding > 0 ? (
          <span className="font-semibold text-destructive whitespace-nowrap">
            {formatINR(outstanding)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>

      {/* Balance (= billed - paid) */}
      <td className="px-3 py-2 align-top text-right">
        {totalBilled > 0 ? (
          <span
            className={`font-semibold whitespace-nowrap ${
              balance <= 0 ? "text-success" : "text-foreground"
            }`}
          >
            {formatINR(balance)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
    </tr>
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
                value={form.amountDue}
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
                    value={form.amountPaid ?? form.amountDue}
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
