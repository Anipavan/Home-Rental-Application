import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  Calendar,
  Copy,
  Droplets,
  Plus,
  RefreshCw,
  Trash2,
  UserPlus,
  Wallet,
  Wrench,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { propertiesApi } from "@/lib/api/properties";
import { authApi } from "@/lib/api/auth";
import { useAuthStore } from "@/stores/auth-store";
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
  ExpenseCategory,
  SetupSocietyRequest,
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
 * Owner / maintainer landing page for a building's society ledger.
 * Combines: one-time "Set up society" wizard (shown when no config
 * exists yet), monthly ledger view (expenses + KPIs), add-expense
 * dialog, and shareable-link UI. Tenants see a slimmer read-only
 * version of the same data at /app/society.
 */
export function OwnerSocietyPage() {
  const { id: buildingId } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const { toast } = useToast();
  const [month, setMonth] = useState<string>(currentMonth());

  const buildingQ = useQuery({
    queryKey: ["building", buildingId],
    queryFn: () => propertiesApi.buildings.get(buildingId!),
    enabled: !!buildingId,
  });

  const configQ = useQuery({
    queryKey: ["society", buildingId],
    queryFn: () => societyApi.get(buildingId!),
    enabled: !!buildingId,
    // 404 = not set up yet; we render the setup wizard in that case.
    retry: (failCount, err) => {
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 400 && status !== 404 && failCount < 1;
    },
  });

  const ledgerQ = useQuery({
    queryKey: ["society-ledger", buildingId, month],
    queryFn: () => societyApi.ledger(buildingId!, month),
    enabled: !!buildingId && !!configQ.data,
    staleTime: 30_000,
  });

  const isLoading = buildingQ.isLoading || configQ.isLoading;
  const isNotSetUp = configQ.isError || (!configQ.isLoading && !configQ.data);

  if (!buildingId) {
    return (
      <EmptyState
        variant="info"
        icon={Building2}
        title="No building selected"
        description="Pick a building from your list to manage its society."
      />
    );
  }

  return (
    <div className="animate-fade-in max-w-6xl">
      <PageHeader
        title={`Society — ${buildingQ.data?.buildingName ?? ""}`}
        description="Track common-area expenses (water bill, security salary, etc.) and share the ledger with residents."
        actions={
          <Button asChild variant="ghost" size="sm">
            <Link to={`/owner/buildings/${buildingId}`}>← Back to building</Link>
          </Button>
        }
      />

      {isLoading ? (
        <div className="space-y-4">
          <Skeleton className="h-48 rounded-2xl" />
          <Skeleton className="h-32 rounded-2xl" />
        </div>
      ) : isNotSetUp ? (
        <SetupWizard buildingId={buildingId} />
      ) : (
        <>
          {/* Maintainer + Public link side by side on desktop, stacked on mobile */}
          <div className="grid gap-4 sm:grid-cols-2 mb-6">
            <MaintainerCard
              buildingId={buildingId}
              currentMaintainerUserId={configQ.data!.maintainerUserId}
              currentDefaultAmount={configQ.data!.defaultPerFlatAmount}
            />
            <Card>
              <CardContent className="p-5 h-full flex flex-col">
                <p className="text-xs uppercase tracking-wider text-muted-foreground font-mono">
                  Public read-only ledger
                </p>
                <p
                  className="text-sm font-mono mt-1 truncate"
                  title={configQ.data!.publicViewUrl}
                >
                  {configQ.data!.publicViewUrl}
                </p>
                <div className="flex gap-2 mt-3">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      navigator.clipboard.writeText(configQ.data!.publicViewUrl);
                      toast({ title: "Link copied — share it in the residents' WhatsApp group." });
                    }}
                  >
                    <Copy className="size-4" /> Copy link
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={async () => {
                      if (
                        !confirm(
                          "Rotate the link? The old URL will stop working immediately.",
                        )
                      )
                        return;
                      await societyApi.regenerateToken(buildingId);
                      qc.invalidateQueries({ queryKey: ["society", buildingId] });
                      toast({ title: "Link rotated — share the new one." });
                    }}
                  >
                    <RefreshCw className="size-4" /> Rotate
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Month selector + KPIs */}
          <div className="flex items-center gap-3 mb-4">
            <Calendar className="size-4 text-muted-foreground" />
            <Input
              type="month"
              value={month}
              onChange={(e) => setMonth(e.target.value || currentMonth())}
              className="w-40"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-3 mb-6">
            <Kpi
              icon={Droplets}
              label="Expenses this month"
              value={formatINR(ledgerQ.data?.expensesThisMonth ?? 0)}
              tone="destructive"
            />
            <Kpi
              icon={Wallet}
              label="Lifetime balance"
              value={formatINR(ledgerQ.data?.balanceLifetime ?? 0)}
              tone={
                (ledgerQ.data?.balanceLifetime ?? 0) >= 0
                  ? "success"
                  : "destructive"
              }
            />
            <Kpi
              icon={Building2}
              label="Default per flat"
              value={formatINR(configQ.data!.defaultPerFlatAmount)}
              tone="muted"
              hint={`Due on day ${configQ.data!.monthlyDueDay}`}
            />
          </div>

          {/* Add expense + expense list */}
          <Card>
            <CardContent className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-display font-semibold text-lg">
                  Expenses — {month}
                </h3>
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

          <div className="mt-6 text-center">
            <Button asChild variant="ghost">
              <Link to="/owner/society">All my societies</Link>
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

function Kpi({
  icon: Icon,
  label,
  value,
  tone,
  hint,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  tone: "success" | "destructive" | "muted";
  hint?: string;
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
        {hint && (
          <p className="text-[11px] text-muted-foreground mt-1">{hint}</p>
        )}
      </CardContent>
    </Card>
  );
}

function SetupWizard({ buildingId }: { buildingId: string }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [form, setForm] = useState<SetupSocietyRequest>({
    defaultPerFlatAmount: 2000,
    monthlyDueDay: 5,
    societyDisplayName: "",
  });

  const setupMut = useMutation({
    mutationFn: (req: SetupSocietyRequest) => societyApi.setup(buildingId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society", buildingId] });
      toast({
        title: "Society set up!",
        description:
          "Now record your first expense or share the public link with residents.",
      });
    },
    onError: (err) =>
      toast({
        title: "Setup failed",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  return (
    <Card>
      <CardContent className="p-6 max-w-xl">
        <h3 className="font-display font-semibold text-xl">
          Set up society maintenance
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          One-time setup. You'll be assigned as the maintainer by default;
          you can hand it to someone else later.
        </p>

        <div className="grid gap-4 mt-6">
          <div>
            <Label>Society display name</Label>
            <Input
              placeholder="e.g. Anirudh Residency Welfare Fund"
              value={form.societyDisplayName ?? ""}
              onChange={(e) =>
                setForm({ ...form, societyDisplayName: e.target.value })
              }
            />
            <p className="text-xs text-muted-foreground mt-1">
              Defaults to the building name. Tenants see this on the ledger.
            </p>
          </div>

          <div>
            <Label>Default per-flat monthly amount (₹)</Label>
            <Input
              type="number"
              min={0}
              step={50}
              value={form.defaultPerFlatAmount}
              onChange={(e) =>
                setForm({
                  ...form,
                  defaultPerFlatAmount: Number(e.target.value) || 0,
                })
              }
            />
          </div>

          <div>
            <Label>Monthly due day</Label>
            <Input
              type="number"
              min={1}
              max={28}
              value={form.monthlyDueDay}
              onChange={(e) =>
                setForm({
                  ...form,
                  monthlyDueDay: Number(e.target.value) || 5,
                })
              }
            />
            <p className="text-xs text-muted-foreground mt-1">
              Day of every month dues are considered due (1–28).
            </p>
          </div>

          <Button
            variant="gradient"
            disabled={
              setupMut.isPending || !form.defaultPerFlatAmount
            }
            onClick={() => setupMut.mutate(form)}
          >
            {setupMut.isPending ? "Setting up…" : "Enable society"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * Shows the currently-assigned maintainer's name + an "Assign / Replace"
 * button that opens the {@link AssignMaintainerDialog}. The owner-as-
 * maintainer (the common case) is rendered as "You're managing this".
 * Other-user maintainers show their name + username so the owner can
 * verify they assigned the right person.
 */
function MaintainerCard({
  buildingId,
  currentMaintainerUserId,
  currentDefaultAmount,
}: {
  buildingId: string;
  currentMaintainerUserId: string;
  currentDefaultAmount: number;
}) {
  const { authUserId } = useAuthStore();
  const isSelf = currentMaintainerUserId === authUserId;

  const maintainerQ = useQuery({
    queryKey: ["auth-user", currentMaintainerUserId],
    queryFn: () => authApi.byId(currentMaintainerUserId),
    enabled: !!currentMaintainerUserId && !isSelf,
    // Maintainer details rarely change. Cache 5 minutes.
    staleTime: 5 * 60_000,
  });

  return (
    <Card>
      <CardContent className="p-5 h-full flex flex-col">
        <p className="text-xs uppercase tracking-wider text-muted-foreground font-mono">
          Maintainer
        </p>
        <div className="mt-1 flex-1">
          {isSelf ? (
            <>
              <p className="text-sm font-semibold">You're managing this</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Assign someone else to share the workload.
              </p>
            </>
          ) : maintainerQ.isLoading ? (
            <Skeleton className="h-6 w-40" />
          ) : maintainerQ.data ? (
            <>
              <p className="text-sm font-semibold truncate">
                {maintainerQ.data.userName ?? "—"}
              </p>
              <p className="text-xs text-muted-foreground mt-0.5 truncate">
                {maintainerQ.data.email ?? ""}
              </p>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">
              External user (id {currentMaintainerUserId.slice(0, 8)}…)
            </p>
          )}
        </div>
        <div className="mt-3">
          <AssignMaintainerDialog
            buildingId={buildingId}
            currentDefaultAmount={currentDefaultAmount}
          />
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * Owner-only flow that creates a MAINTAINER user with credentials the
 * owner picks, then links them as the maintainer on the building. Two
 * sequential API calls (auth-service /register + society/:id PUT)
 * intentionally NOT atomic — if the second fails, the user account
 * still exists (the owner can retry the link via the API or re-run
 * the dialog with the same username, which surfaces a "duplicate"
 * error that the owner can act on).
 *
 * <p>Owner shares the username + password with the maintainer manually
 * (WhatsApp, in-person, email). This is by design — no auto-emailing
 * credentials, which is a non-starter for password hygiene.
 */
function AssignMaintainerDialog({
  buildingId,
  currentDefaultAmount,
}: {
  buildingId: string;
  currentDefaultAmount: number;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    userName: "",
    email: "",
    phone: "",
    userPassword: "",
  });

  const assignMut = useMutation({
    mutationFn: async () => {
      // 1. Create the user with MAINTAINER role. The owner is acting
      //    on behalf of the maintainer — T&C acceptance is implicit
      //    in that the owner is a signed-in user who already accepted
      //    it themselves. The auth-service /register endpoint doesn't
      //    require a separate consent flag at the wire level.
      const created = await authApi.register({
        userName: form.userName.trim(),
        userPassword: form.userPassword,
        userRole: "MAINTAINER",
        email: form.email.trim(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phone: form.phone.trim(),
      });
      // 2. Link the new authUserId as the maintainer on this building.
      //    The backend SetupSocietyRequest DTO marks defaultPerFlatAmount
      //    as @NotNull (it's shared with the setup endpoint), so we
      //    echo the current value alongside the maintainer change. The
      //    service treats absent monthlyDueDay / societyDisplayName as
      //    "keep current".
      await societyApi.update(buildingId, {
        defaultPerFlatAmount: currentDefaultAmount,
        maintainerUserId: created.authUserId,
      });
      return created;
    },
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: ["society", buildingId] });
      qc.invalidateQueries({ queryKey: ["my-societies"] });
      toast({
        title: "Maintainer assigned",
        description: `${created.userName} can now log in and start recording expenses. Share the username + password with them.`,
      });
      setOpen(false);
      setForm({
        firstName: "",
        lastName: "",
        userName: "",
        email: "",
        phone: "",
        userPassword: "",
      });
    },
    onError: (err) =>
      toast({
        title: "Couldn't assign maintainer",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  const canSubmit =
    form.firstName.trim() &&
    form.lastName.trim() &&
    form.userName.trim() &&
    form.email.trim() &&
    form.phone.trim() &&
    form.userPassword.length >= 8;

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <UserPlus className="size-4" /> Assign / replace
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Assign a maintainer</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          Create an account for the person who'll manage this building's
          society ledger. Share the username + password with them — they
          can change the password after logging in.
        </p>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>First name</Label>
              <Input
                value={form.firstName}
                onChange={(e) =>
                  setForm({ ...form, firstName: e.target.value })
                }
              />
            </div>
            <div>
              <Label>Last name</Label>
              <Input
                value={form.lastName}
                onChange={(e) =>
                  setForm({ ...form, lastName: e.target.value })
                }
              />
            </div>
          </div>
          <div>
            <Label>Username</Label>
            <Input
              placeholder="e.g. ramesh.society"
              value={form.userName}
              onChange={(e) => setForm({ ...form, userName: e.target.value })}
            />
            <p className="text-xs text-muted-foreground mt-1">
              They'll log in with this. Letters, digits, dots — no spaces.
            </p>
          </div>
          <div>
            <Label>Email</Label>
            <Input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </div>
          <div>
            <Label>Phone</Label>
            <Input
              placeholder="+91-9876543210"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
            />
          </div>
          <div>
            <Label>Temporary password</Label>
            <Input
              type="text"
              placeholder="At least 8 chars, 1 upper, 1 lower, 1 digit"
              value={form.userPassword}
              onChange={(e) =>
                setForm({ ...form, userPassword: e.target.value })
              }
            />
            <p className="text-xs text-muted-foreground mt-1">
              Visible so you can copy it. Ask them to change it on first
              login.
            </p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            disabled={!canSubmit || assignMut.isPending}
            onClick={() => assignMut.mutate()}
          >
            {assignMut.isPending ? "Creating…" : "Create + assign"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
