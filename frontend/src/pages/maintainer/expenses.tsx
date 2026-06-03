import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Calendar, Plus, Trash2, Wrench } from "lucide-react";
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
import type { AddExpenseRequest, ExpenseCategory } from "@/types/api";

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
          className="w-40"
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
            <div className="space-y-2">
              {ledgerQ.data.expenses.map((e) => (
                <div
                  key={e.id}
                  className="flex items-start gap-3 p-3 rounded-xl border border-border/60 bg-secondary/30"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge variant="secondary" className="text-[10px]">
                        {CATEGORY_LABELS[e.category]}
                      </Badge>
                      <span className="font-medium text-sm">
                        {e.subcategory ?? e.vendorName ?? "—"}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {e.paidOnDate}
                      {e.vendorName && e.subcategory
                        ? ` · paid to ${e.vendorName}`
                        : ""}
                    </p>
                    {e.notes && (
                      <p className="text-xs text-muted-foreground mt-1 italic">
                        {e.notes}
                      </p>
                    )}
                  </div>
                  <div className="text-right">
                    <p className="font-semibold font-display">
                      {formatINR(e.amount)}
                    </p>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 px-2 text-destructive mt-1"
                      onClick={async () => {
                        if (!confirm("Delete this expense?")) return;
                        try {
                          await societyApi.deleteExpense(buildingId, e.id);
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
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

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

  const addMut = useMutation({
    mutationFn: (req: AddExpenseRequest) =>
      societyApi.addExpense(buildingId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society-ledger", buildingId] });
      toast({ title: "Expense added." });
      setOpen(false);
      setForm({
        expenseMonth: month,
        category: "UTILITY",
        paidOnDate: new Date().toISOString().slice(0, 10),
        amount: 0,
      });
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
      <DialogContent>
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
                value={form.amount}
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
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            disabled={addMut.isPending || !form.amount}
            onClick={() => addMut.mutate(form)}
          >
            {addMut.isPending ? "Adding…" : "Add expense"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
