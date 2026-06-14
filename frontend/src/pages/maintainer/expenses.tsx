import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Calendar, Plus, Split, Trash2, Wrench } from "lucide-react";
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
  AddExpenseRequest,
  CollectionStatus,
  ExpenseCategory,
  FlatChargeCategory,
  FlatMaintenanceRow,
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

const currentMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

/**
 * Maintainer-side common-area expense ledger. The owner no longer has
 * an "Add expense" button on their society page — that responsibility
 * moved here. Owners see the same expenses on their read-only view
 * automatically once a row lands.
 */
export function MaintainerExpensesPage() {
  const { buildingId } = useParams<{ buildingId: string }>();
  const qc = useQueryClient();
  const { toast } = useToast();
  const [month, setMonth] = useState<string>(currentMonth());

  const configQ = useQuery({
    queryKey: ["society", buildingId],
    queryFn: () => societyApi.get(buildingId!),
    enabled: !!buildingId,
  });

  const ledgerQ = useQuery({
    queryKey: ["society-ledger", buildingId, month],
    queryFn: () => societyApi.ledger(buildingId!, month),
    enabled: !!buildingId,
    staleTime: 30_000,
  });

  if (!buildingId) {
    return (
      <EmptyState
        variant="info"
        icon={Wrench}
        title="No building selected"
        description="Pick one of your societies from the dashboard."
      />
    );
  }

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title={`Expenses — ${configQ.data?.societyDisplayName ?? "society"}`}
        description="Common-area expenses for the building. Water bill, security salary, repairs, etc."
        actions={
          <Button asChild variant="ghost" size="sm">
            <Link to={`/maintainer/${buildingId}/flats`}>← Back to flats</Link>
          </Button>
        }
      />

      <div className="flex items-center gap-3 mb-4">
        <Calendar className="size-4 text-muted-foreground" />
        <Input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value || currentMonth())}
          className="w-48"
        />
      </div>

      <Card>
        <CardContent className="p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-display font-semibold text-lg">
                Expenses — {month}
              </h3>
              <p className="text-xs text-muted-foreground mt-0.5">
                Total this month{" "}
                <span className="font-semibold text-destructive">
                  {formatINR(ledgerQ.data?.expensesThisMonth ?? 0)}
                </span>
              </p>
            </div>
            <AddExpenseDialog buildingId={buildingId} month={month} />
          </div>

          {ledgerQ.isLoading ? (
            <Skeleton className="h-32 rounded-xl" />
          ) : !ledgerQ.data?.expenses?.length ? (
            <EmptyState
              variant="info"
              icon={Wrench}
              title="No expenses recorded for this month"
              description="Click 'Add expense' to record the first one (water bill, security salary, etc.)."
            />
          ) : (
            /* Tabular layout — one expense per row, columns line up so
             * the maintainer can scan amounts and dates without their
             * eye jumping the way it did with the card list. The notes
             * line stays as a soft second row underneath the
             * description so a long context doesn't blow out the
             * column widths. */
            <div className="overflow-x-auto rounded-xl border border-border/60">
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
                      Vendor
                    </th>
                    <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                      Paid on
                    </th>
                    <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                      Amount
                    </th>
                    <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {ledgerQ.data.expenses.map((e) => (
                    <tr
                      key={e.id}
                      className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20"
                    >
                      <td className="px-3 py-2 align-top whitespace-nowrap">
                        <Badge variant="secondary" className="text-[10px]">
                          {CATEGORY_LABELS[e.category]}
                        </Badge>
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
                      <td className="px-3 py-2 align-top text-right whitespace-nowrap">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="Delete expense"
                          className="size-7 text-muted-foreground hover:text-destructive"
                          onClick={async () => {
                            if (!confirm("Delete this expense?")) return;
                            try {
                              await societyApi.deleteExpense(
                                buildingId,
                                e.id,
                              );
                              qc.invalidateQueries({
                                queryKey: ["society-ledger", buildingId],
                              });
                              toast({ title: "Expense deleted." });
                            } catch (err) {
                              toast({
                                title: "Couldn't delete",
                                description: extractErrorMessage(err),
                                variant: "destructive",
                              });
                            }
                          }}
                        >
                          <Trash2 className="size-3.5" />
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/**
 * Maps an expense-level category (UTILITY, SALARY, …) to the flat-row
 * charge category we use when fanning the split out across flats. Most
 * expenses fall into the "Additional Expenses" bucket; utility splits
 * land in Water bill; salaries roll up under Maintenance.
 */
const EXPENSE_TO_FLAT_CATEGORY: Record<ExpenseCategory, FlatChargeCategory> = {
  UTILITY: "WATER_BILL",
  SALARY: "MAINTENANCE",
  SUPPLIES: "COMMON_AREA_SHARE",
  REPAIR_COMMON: "COMMON_AREA_SHARE",
  INSURANCE: "COMMON_AREA_SHARE",
  TAX: "COMMON_AREA_SHARE",
  OTHER: "COMMON_AREA_SHARE",
};

const FLAT_CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Additional Expenses",
  OTHER: "Additional Expenses",
};

function AddExpenseDialog({
  buildingId,
  month,
}: {
  buildingId: string;
  month: string;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<AddExpenseRequest>({
    expenseMonth: month,
    category: "UTILITY",
    paidOnDate: new Date().toISOString().slice(0, 10),
    amount: 0,
  });

  // Split UI state — defaults to "split equally among all flats", with
  // a per-flat override map the maintainer can tweak. Off by default
  // for expenses that don't naturally split (e.g. capex; user can flip
  // the toggle to skip). Inspired by Splitwise/Tricount UX.
  const [splitOn, setSplitOn] = useState(true);
  const [flatCategory, setFlatCategory] =
    useState<FlatChargeCategory>("COMMON_AREA_SHARE");
  /** Per-flat amount override. Empty = use equal share. */
  const [overrides, setOverrides] = useState<Record<string, number | "">>({});

  // Fetch the flat roster for the building so we can list every flat
  // in the split panel. The dialog only mounts when open, so this
  // query fires lazily.
  const flatsQ = useQuery({
    queryKey: ["society-flats", buildingId, form.expenseMonth],
    queryFn: () => societyApi.flatsForMonth(buildingId, form.expenseMonth),
    enabled: open,
    staleTime: 30_000,
  });

  // Unique flats (the backend emits one row per (flat, category); we
  // only need each flat once for the split list). Vacant flats are
  // filtered out — they have no tenant to bill, and splitting an
  // expense across them just creates orphan DUE rows nobody will pay.
  const flats = useMemo<FlatMaintenanceRow[]>(() => {
    if (!flatsQ.data) return [];
    const seen = new Map<string, FlatMaintenanceRow>();
    for (const r of flatsQ.data) {
      if (!r.tenantUserId || r.tenantName === "(vacant)") continue;
      if (!seen.has(r.flatId)) seen.set(r.flatId, r);
    }
    return Array.from(seen.values()).sort((a, b) =>
      a.flatNumber.localeCompare(b.flatNumber),
    );
  }, [flatsQ.data]);

  // Auto-pick the flat-charge category whenever the expense category
  // changes — saves the maintainer a click in the common case.
  useEffect(() => {
    setFlatCategory(EXPENSE_TO_FLAT_CATEGORY[form.category]);
  }, [form.category]);

  // Equal share = total / N (rounded to integer rupees). The last flat
  // absorbs the remainder so the split sums exactly to the total even
  // when the division isn't clean (e.g. 1000 ÷ 3).
  const equalShare = useMemo(() => {
    if (!splitOn || flats.length === 0 || !form.amount) return 0;
    return Math.floor(form.amount / flats.length);
  }, [splitOn, flats.length, form.amount]);

  // Effective per-flat amount: override if set + non-empty, else
  // equal share. Computed once so the totals row + the save logic
  // see the same numbers.
  const perFlat = useMemo<Record<string, number>>(() => {
    const out: Record<string, number> = {};
    if (flats.length === 0 || !form.amount) return out;
    let remainder = form.amount;
    for (let i = 0; i < flats.length; i++) {
      const f = flats[i];
      const override = overrides[f.flatId];
      if (override !== undefined && override !== "") {
        out[f.flatId] = Number(override) || 0;
        remainder -= out[f.flatId];
      } else {
        // No override: take equal share. The LAST flat without an
        // override absorbs the rounding remainder.
        out[f.flatId] = equalShare;
        remainder -= equalShare;
      }
    }
    // Push leftover (positive or negative) onto the last flat that
    // didn't have an override — that's the standard "absorb rounding"
    // trick. If every flat is overridden, leave remainder for the
    // validation banner to flag.
    if (remainder !== 0) {
      for (let i = flats.length - 1; i >= 0; i--) {
        const f = flats[i];
        if (
          overrides[f.flatId] === undefined ||
          overrides[f.flatId] === ""
        ) {
          out[f.flatId] = (out[f.flatId] ?? 0) + remainder;
          break;
        }
      }
    }
    return out;
  }, [flats, overrides, equalShare, form.amount]);

  const splitTotal = useMemo(
    () => Object.values(perFlat).reduce((s, v) => s + v, 0),
    [perFlat],
  );
  const splitMatches = splitTotal === form.amount;

  const reset = () => {
    setForm({
      expenseMonth: month,
      category: "UTILITY",
      paidOnDate: new Date().toISOString().slice(0, 10),
      amount: 0,
    });
    setSplitOn(true);
    setOverrides({});
    setFlatCategory("COMMON_AREA_SHARE");
  };

  const addMut = useMutation({
    mutationFn: async (req: AddExpenseRequest) => {
      // Step 1 — always record the expense (ledger / Expenses card).
      await societyApi.addExpense(buildingId, req);
      // Step 2 — if split is enabled, fan out per-flat charges. Each
      // upsert is a single round-trip; in practice societies have
      // <20 flats so the latency stays under a few seconds.
      if (splitOn) {
        for (const f of flats) {
          const amt = perFlat[f.flatId] ?? 0;
          if (amt <= 0) continue; // skip flats with zero share
          await societyApi.upsertFlatCollection(buildingId, f.flatId, {
            forMonth: req.expenseMonth,
            amountDue: amt,
            status: "DUE" as CollectionStatus,
            category: flatCategory,
            notes:
              [req.subcategory, req.vendorName, req.notes]
                .filter(Boolean)
                .join(" · ") ||
              `${CATEGORY_LABELS[req.category]} split`,
          });
        }
      }
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society-ledger", buildingId] });
      qc.invalidateQueries({ queryKey: ["society-flats", buildingId] });
      toast({
        title: splitOn
          ? "Expense added and split across flats."
          : "Expense added.",
      });
      setOpen(false);
      reset();
    },
    onError: (err) =>
      toast({
        title: "Couldn't add expense",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="gradient" size="sm">
          <Plus className="size-4" /> Add expense
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Add a common-area expense</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Expense month</Label>
              <Input
                type="month"
                value={form.expenseMonth}
                onChange={(e) =>
                  setForm({ ...form, expenseMonth: e.target.value })
                }
              />
            </div>
            <div>
              <Label>Paid on</Label>
              <Input
                type="date"
                value={form.paidOnDate}
                onChange={(e) =>
                  setForm({ ...form, paidOnDate: e.target.value })
                }
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Category</Label>
              <Select
                value={form.category}
                onValueChange={(v) =>
                  setForm({ ...form, category: v as ExpenseCategory })
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
            </div>
            <div>
              <Label>Amount (₹)</Label>
              <Input
                type="number"
                min={0}
                step={1}
                placeholder="0"
                // value={amount || ""} so the field is visually empty
                // when amount is 0 — otherwise the literal "0" sits in
                // the input and the maintainer has to manually delete
                // it before typing the real number. With this, they
                // just click and type.
                value={form.amount || ""}
                onChange={(e) =>
                  setForm({ ...form, amount: Number(e.target.value) || 0 })
                }
              />
            </div>
          </div>

          <div>
            <Label>Subcategory / line item</Label>
            <Input
              placeholder="e.g. BWSSB water bill - May"
              value={form.subcategory ?? ""}
              onChange={(e) =>
                setForm({ ...form, subcategory: e.target.value })
              }
            />
          </div>

          <div>
            <Label>Vendor name</Label>
            <Input
              placeholder="e.g. Ramesh (security)"
              value={form.vendorName ?? ""}
              onChange={(e) =>
                setForm({ ...form, vendorName: e.target.value })
              }
            />
          </div>

          <div>
            <Label>Notes (optional)</Label>
            <Textarea
              placeholder="Any context for the residents — annual prepay, rate hike, etc."
              value={form.notes ?? ""}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
              rows={2}
            />
          </div>

          {/* Splitwise-style split-among-flats section. The maintainer
              enters the total above; each flat's share starts at the
              equal-divide value and can be overridden inline. */}
          <div className="rounded-xl border border-primary/30 bg-primary/5 p-3 space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <Split className="size-4 text-primary" />
                <p className="font-semibold text-sm">Split among flats</p>
              </div>
              <label className="text-xs flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={splitOn}
                  onChange={(e) => setSplitOn(e.target.checked)}
                  className="accent-primary"
                />
                {splitOn ? "On" : "Off"}
              </label>
            </div>

            {splitOn ? (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <Label className="text-xs">Bill flats as</Label>
                    <Select
                      value={flatCategory}
                      onValueChange={(v) =>
                        setFlatCategory(v as FlatChargeCategory)
                      }
                    >
                      <SelectTrigger className="h-9">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="MAINTENANCE">
                          Maintenance
                        </SelectItem>
                        <SelectItem value="WATER_BILL">Water bill</SelectItem>
                        <SelectItem value="COMMON_AREA_SHARE">
                          Additional Expenses
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="flex items-end gap-1">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setOverrides({})}
                      title="Reset all overrides to equal share"
                    >
                      Split equally
                    </Button>
                  </div>
                </div>

                {flatsQ.isLoading ? (
                  <Skeleton className="h-32 rounded-lg" />
                ) : flats.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    No flats in this building yet. Add the expense
                    without a split.
                  </p>
                ) : (
                  <div className="rounded-lg border border-border/60 overflow-hidden">
                    <table className="w-full text-sm">
                      <thead className="bg-secondary/40 text-[10px] uppercase tracking-wider text-muted-foreground">
                        <tr>
                          <th className="text-left px-2 py-1.5">Flat</th>
                          <th className="text-left px-2 py-1.5">Name</th>
                          <th className="text-right px-2 py-1.5">
                            Share (₹)
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {flats.map((f) => (
                          <tr
                            key={f.flatId}
                            className="border-t border-border/40"
                          >
                            <td className="px-2 py-1">
                              <Badge
                                variant="outline"
                                className="font-mono text-[10px]"
                              >
                                {f.flatNumber}
                              </Badge>
                            </td>
                            <td className="px-2 py-1 text-xs">
                              {f.tenantName}
                            </td>
                            <td className="px-2 py-1 text-right">
                              <Input
                                type="number"
                                min={0}
                                step={1}
                                value={
                                  overrides[f.flatId] !== undefined &&
                                  overrides[f.flatId] !== ""
                                    ? overrides[f.flatId]
                                    : perFlat[f.flatId] ?? 0
                                }
                                onChange={(e) =>
                                  setOverrides({
                                    ...overrides,
                                    [f.flatId]:
                                      e.target.value === ""
                                        ? ""
                                        : Number(e.target.value) || 0,
                                  })
                                }
                                className="h-7 text-right text-xs w-24 ml-auto"
                              />
                            </td>
                          </tr>
                        ))}
                        <tr className="border-t border-border/60 bg-secondary/30 font-semibold">
                          <td colSpan={2} className="px-2 py-1.5 text-xs">
                            Total of shares
                          </td>
                          <td className="px-2 py-1.5 text-right text-xs">
                            <span
                              className={
                                splitMatches
                                  ? "text-success"
                                  : "text-destructive"
                              }
                            >
                              {formatINR(splitTotal)}
                            </span>
                            {!splitMatches && form.amount > 0 && (
                              <span className="text-[10px] text-muted-foreground ml-1">
                                of {formatINR(form.amount)}
                              </span>
                            )}
                          </td>
                        </tr>
                      </tbody>
                    </table>
                    {!splitMatches && form.amount > 0 && (
                      <p className="text-[10px] text-destructive px-2 py-1.5 border-t border-destructive/30 bg-destructive/5">
                        Sum of shares doesn't match total amount. Adjust
                        overrides or hit "Split equally" to reset.
                      </p>
                    )}
                  </div>
                )}

                <p className="text-[10px] text-muted-foreground">
                  Each flat gets a "{FLAT_CATEGORY_LABELS[flatCategory]}"
                  charge for their share, marked DUE. They'll see it on
                  their society page and on the public ledger.
                </p>
              </>
            ) : (
              <p className="text-xs text-muted-foreground">
                Off — the expense is recorded against the society fund
                only. No per-flat charges are created.
              </p>
            )}
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            disabled={
              addMut.isPending ||
              !form.amount ||
              (splitOn && flats.length > 0 && !splitMatches)
            }
            onClick={() => addMut.mutate(form)}
          >
            {addMut.isPending ? "Adding…" : "Add expense"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
