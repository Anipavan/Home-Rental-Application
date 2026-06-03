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

  const summary = useMemo(() => {
    const rows = flatsQ.data ?? [];
    const paidCount = rows.filter((r) => r.status === "PAID").length;
    const totalCount = rows.length;
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
          className="w-40"
        />
      </div>

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
            <div className="space-y-2">
              {flatsQ.data.map((row) => (
                <FlatRow
                  key={row.flatId}
                  row={row}
                  buildingId={buildingId}
                  month={month}
                />
              ))}
            </div>
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

function FlatRow({
  row,
  buildingId,
  month,
}: {
  row: FlatMaintenanceRow;
  buildingId: string;
  month: string;
}) {
  const status = STATUS_LABELS[row.status] ?? STATUS_LABELS.NEW_FLAT;
  const overridesDefault =
    row.status !== "NEW_FLAT" && row.monthAmount !== row.defaultAmount;
  const categoryLabel = row.category
    ? CATEGORY_LABELS[row.category]
    : null;

  return (
    <div className="flex flex-col sm:flex-row sm:items-center gap-3 p-3 rounded-xl border border-border/60 bg-secondary/30">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <Badge variant="outline" className="font-mono text-[11px]">
            Flat {row.flatNumber}
          </Badge>
          <span className="font-medium text-sm truncate">{row.tenantName}</span>
          <span
            className={`rounded-full text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 ${status.tone}`}
          >
            {status.label}
          </span>
          {categoryLabel && (
            <Badge variant="secondary" className="text-[10px]">
              {categoryLabel}
            </Badge>
          )}
        </div>
        {row.notes && (
          <p className="text-xs text-muted-foreground mt-1 italic line-clamp-1">
            {row.notes}
          </p>
        )}
      </div>

      <div className="flex items-center gap-4">
        <div className="text-right">
          <p className="font-semibold font-display text-base">
            {formatINR(row.monthAmount)}
          </p>
          {overridesDefault && (
            <p className="text-[10px] text-muted-foreground">
              default {formatINR(row.defaultAmount)}
            </p>
          )}
        </div>
        <SetAmountDialog row={row} buildingId={buildingId} month={month} />
      </div>
    </div>
  );
}

function SetAmountDialog({
  row,
  buildingId,
  month,
}: {
  row: FlatMaintenanceRow;
  buildingId: string;
  month: string;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<UpsertFlatCollectionRequest>({
    forMonth: month,
    amountDue:
      row.status === "NEW_FLAT" ? row.defaultAmount : row.monthAmount,
    status: (row.status === "NEW_FLAT" ? "DUE" : row.status) as CollectionStatus,
    category: (row.category ?? "MAINTENANCE") as FlatChargeCategory,
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
            category: (row.category ?? "MAINTENANCE") as FlatChargeCategory,
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
        <Button variant="outline" size="sm">
          {isNew ? (
            <>
              <Plus className="size-3.5" /> Set amount
            </>
          ) : (
            <>
              <Pencil className="size-3.5" /> Edit
            </>
          )}
        </Button>
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
                {Object.entries(CATEGORY_LABELS).map(([k, label]) => (
                  <SelectItem key={k} value={k}>
                    {label}
                  </SelectItem>
                ))}
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
