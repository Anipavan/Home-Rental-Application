import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  Calendar,
  CalendarRange,
  Copy,
  Droplets,
  HandCoins,
  RefreshCw,
  Sparkles,
  UserPlus,
  Wallet,
  Wrench,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { propertiesApi } from "@/lib/api/properties";
import { SocietyBankPanel } from "../maintainer/society-bank-panel";
import { authApi } from "@/lib/api/auth";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { CollapsibleSection } from "@/components/ui/collapsible-section";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
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
  EligibleMaintainer,
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
 * Owner landing page for a building's society ledger.
 *
 * <p>Post-restructure responsibilities are READ-ONLY for the owner:
 * <ul>
 *   <li>Setup the society (one-time wizard, still here).</li>
 *   <li>Assign / replace the maintainer — picked from existing
 *       tenants in the building's flats, not free-form user creation.</li>
 *   <li>See collected / outstanding KPIs (per month + per year + lifetime).</li>
 *   <li>Read the expense list the maintainer is recording — the
 *       "Add expense" button has moved off this page. Maintainers now
 *       own expense entry on their per-flat dashboard.</li>
 *   <li>Manage the public read-only shareable URL.</li>
 * </ul>
 *
 * <p>If the same auth user is BOTH the building owner AND the
 * assigned maintainer (the common solo-landlord case), the maintainer
 * dashboard is still reachable from the sidebar — this page stays
 * focused on the financial overview.
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
        description="Track collections, outstanding dues, and common-area expenses. Day-to-day expense entry is owned by the maintainer."
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

          {/* Common bank account */}
          <SocietyBankPanel buildingId={buildingId} config={configQ.data!} />

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

          {/* Collected / outstanding KPIs — the headline owners want to see */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-4">
            <Kpi
              icon={HandCoins}
              label="Collected this month"
              value={formatINR(ledgerQ.data?.collectedThisMonth ?? 0)}
              tone="success"
            />
            <Kpi
              icon={CalendarRange}
              label="Collected this year"
              value={formatINR(ledgerQ.data?.collectedThisYear ?? 0)}
              tone="success"
              hint={`Year ${month.slice(0, 4)}`}
            />
            <Kpi
              icon={Sparkles}
              label="Collected lifetime"
              value={formatINR(ledgerQ.data?.collectedLifetime ?? 0)}
              tone="success"
            />
            <Kpi
              icon={Wrench}
              label="Total Dues this month"
              value={formatINR(ledgerQ.data?.outstandingThisMonth ?? 0)}
              tone="destructive"
            />
          </div>

          {/* Expense + balance KPIs — secondary, less prominent */}
          <div className="grid gap-4 sm:grid-cols-3 mb-6">
            <Kpi
              icon={Droplets}
              label="Expenses this month"
              value={formatINR(ledgerQ.data?.expensesThisMonth ?? 0)}
              tone="destructive"
            />
            <Kpi
              icon={Wallet}
              label="Net Fund Balance"
              value={formatINR(ledgerQ.data?.balanceLifetime ?? 0)}
              tone={
                (ledgerQ.data?.balanceLifetime ?? 0) >= 0
                  ? "success"
                  : "destructive"
              }
              hint="Collected − Expensed"
            />
            <Kpi
              icon={Building2}
              label="Default per flat"
              value={formatINR(configQ.data!.defaultPerFlatAmount)}
              tone="muted"
              hint={`Due on day ${configQ.data!.monthlyDueDay}`}
            />
          </div>

          {/* Expense list — read-only for owner, collapsible + tabular
              like the public ledger so the visual language stays
              consistent across surfaces. */}
          <CollapsibleSection
            title={`Expenses — ${month}`}
            icon={Wrench}
            summary={
              ledgerQ.data?.expenses?.length
                ? `${ledgerQ.data.expenses.length} entr${ledgerQ.data.expenses.length === 1 ? "y" : "ies"} · ${formatINR(ledgerQ.data.expensesThisMonth ?? 0)}`
                : "No entries"
            }
            actions={
              <Badge variant="secondary" className="text-[10px]">
                Recorded by maintainer
              </Badge>
            }
          >
            {ledgerQ.isLoading ? (
              <Skeleton className="h-32 rounded-xl" />
            ) : !ledgerQ.data?.expenses?.length ? (
              <EmptyState
                variant="info"
                icon={Wrench}
                title="No expenses recorded for this month"
                description="The maintainer hasn't logged any common-area expenses for this month yet. Once they do, the entries appear here."
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
                        Vendor
                      </th>
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Paid on
                      </th>
                      <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Amount
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
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CollapsibleSection>

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
          "Assign a maintainer next, then they'll start recording per-flat dues + common expenses.",
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
          assign one of your tenants as the day-to-day maintainer after this.
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
 * Other-user maintainers show their name + email so the owner can
 * verify they assigned the right person.
 */
function MaintainerCard({
  buildingId,
  currentMaintainerUserId,
}: {
  buildingId: string;
  currentMaintainerUserId: string;
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
                Promote one of your tenants so they can take over day-to-day
                entry.
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
          <AssignMaintainerDialog buildingId={buildingId} />
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * Cryptographically-light password generator for the maintainer's
 * temporary login. Mixes 3 char classes (upper / lower / digit) +
 * 12 char length so it passes the auth-service password validator
 * (8+ chars, one upper, one lower, one digit) without being so weird
 * the owner can't read it over the phone.
 *
 * <p>Excludes visually-ambiguous chars (O/0, l/1) so the temp
 * password survives a WhatsApp screenshot or a verbal handoff.
 *
 * <p>{@link crypto.getRandomValues} feeds the picks — never
 * {@code Math.random}, even for a throwaway temp password.
 */
function generateTempPassword(): string {
  const UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ"; // O removed
  const LOWER = "abcdefghjkmnpqrstuvwxyz"; // l removed
  const DIGIT = "23456789";                // 0, 1 removed
  const ALL = UPPER + LOWER + DIGIT;
  const pickFrom = (s: string) => {
    const arr = new Uint32Array(1);
    crypto.getRandomValues(arr);
    return s[arr[0] % s.length];
  };
  const out = [pickFrom(UPPER), pickFrom(LOWER), pickFrom(DIGIT)];
  for (let i = 0; i < 9; i++) out.push(pickFrom(ALL));
  // Shuffle so the first 3 picks aren't always upper/lower/digit in order.
  for (let i = out.length - 1; i > 0; i--) {
    const r = new Uint32Array(1);
    crypto.getRandomValues(r);
    const j = r[0] % (i + 1);
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out.join("");
}

/**
 * Owner-only flow: pick an existing tenant of one of the building's
 * flats from a dropdown, set a temporary password (auto-generate
 * supported), and the backend handles the role flip + password reset
 * + society maintainer link in a single call.
 *
 * <p>The previous version of this dialog created a brand-new user via
 * {@code /auth/register}, which was wrong on two counts: (1) the
 * maintainer is usually already a resident, so the new account
 * duplicated their identity; (2) the owner had to fill in name +
 * email + phone for someone who already had all that in user-service.
 *
 * <p>Pattern: the dropdown is hydrated from
 * {@code GET /society/{buildingId}/eligible-maintainers} (only flats
 * with an assigned tenantId show up). Vacant flats are filtered out
 * server-side. If the owner wants to assign someone outside the
 * building's flats, they have to add that person as a tenant first —
 * which is the right shape, since maintainers are part of the
 * community.
 */
function AssignMaintainerDialog({ buildingId }: { buildingId: string }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [selectedTenantId, setSelectedTenantId] = useState<string>("");
  const [password, setPassword] = useState<string>("");

  // Re-fetch every dialog-open so the list reflects new tenants the
  // owner may have added since the page first loaded.
  const eligibleQ = useQuery({
    queryKey: ["eligible-maintainers", buildingId],
    queryFn: () => societyApi.eligibleMaintainers(buildingId),
    enabled: open,
    staleTime: 30_000,
  });

  const selectedTenant: EligibleMaintainer | undefined = useMemo(
    () => eligibleQ.data?.find((t) => t.tenantUserId === selectedTenantId),
    [eligibleQ.data, selectedTenantId],
  );

  const assignMut = useMutation({
    mutationFn: () =>
      societyApi.promoteTenant(buildingId, {
        tenantUserId: selectedTenantId,
        temporaryPassword: password,
      }),
    onSuccess: (resp) => {
      qc.invalidateQueries({ queryKey: ["society", buildingId] });
      qc.invalidateQueries({ queryKey: ["my-societies"] });
      toast({
        title: "Maintainer assigned",
        description: `${resp.userName} can log in now. Temporary password: ${resp.temporaryPassword}. Share via WhatsApp — they should change it on first login.`,
      });
      setOpen(false);
      setSelectedTenantId("");
      setPassword("");
    },
    onError: (err) =>
      toast({
        title: "Couldn't assign maintainer",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  // Validate against the same regex the backend enforces — keeps the
  // submit button disabled until the password would actually be
  // accepted. Better than letting the user submit + see a 400.
  const passwordOk =
    password.length >= 8 &&
    /[A-Z]/.test(password) &&
    /[a-z]/.test(password) &&
    /\d/.test(password);
  const canSubmit = !!selectedTenantId && passwordOk && !assignMut.isPending;

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (!o) {
          // Reset on close so re-opening doesn't show stale state.
          setSelectedTenantId("");
          setPassword("");
        }
      }}
    >
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
          Pick one of the tenants living in this building — they'll be able
          to log in with a fresh password and start recording per-flat dues
          + common-area expenses.
        </p>

        <div className="space-y-4">
          <div>
            <Label>Tenant</Label>
            {eligibleQ.isLoading ? (
              <Skeleton className="h-10 mt-1.5" />
            ) : eligibleQ.isError ? (
              <p className="text-sm text-destructive mt-1.5">
                Couldn't load tenants — try reopening this dialog.
              </p>
            ) : !eligibleQ.data?.length ? (
              <EmptyState
                variant="info"
                icon={Building2}
                title="No tenants in this building yet"
                description="Add tenants to the building's flats first, then come back to promote one as the maintainer."
              />
            ) : (
              <>
                <Select
                  value={selectedTenantId}
                  onValueChange={(v) => setSelectedTenantId(v)}
                >
                  <SelectTrigger className="mt-1.5">
                    <SelectValue placeholder="Pick a tenant" />
                  </SelectTrigger>
                  <SelectContent>
                    {eligibleQ.data.map((t) => (
                      <SelectItem key={t.tenantUserId} value={t.tenantUserId}>
                        {t.displayName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-[11px] text-muted-foreground mt-1">
                  Only flats with a tenant currently assigned appear here.
                  Vacant flats — and tenants of OTHER buildings — are
                  intentionally excluded.
                </p>
              </>
            )}
            {selectedTenant && (
              <p className="text-xs text-muted-foreground mt-1.5">
                {selectedTenant.email ?? "no email on file"}
                {selectedTenant.phone ? ` · ${selectedTenant.phone}` : ""}
              </p>
            )}
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <Label>Temporary password</Label>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={() => setPassword(generateTempPassword())}
              >
                <Sparkles className="size-3.5" /> Generate
              </Button>
            </div>
            <Input
              type="text"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 chars, 1 upper, 1 lower, 1 digit"
            />
            <p className="text-xs text-muted-foreground mt-1">
              Shown in plain text so you can copy it. Ask the tenant to
              change it after their first login.
            </p>
            {password && !passwordOk && (
              <p className="text-xs text-destructive mt-1">
                Needs 8+ chars including one uppercase, one lowercase, one digit.
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
            disabled={!canSubmit}
            onClick={() => assignMut.mutate()}
          >
            {assignMut.isPending ? "Assigning…" : "Assign maintainer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
