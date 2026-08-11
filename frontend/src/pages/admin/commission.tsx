import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Percent,
  Loader2,
  Trash2,
  Plus,
  AlertTriangle,
  Wallet,
  User,
} from "lucide-react";
import { commissionApi, type CommissionRuleUpsertRequest } from "@/lib/api/commission";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { toast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import { formatINR, cn } from "@/lib/utils";

/**
 * /admin/commission — sets the platform's cut of each rent payment.
 *
 * <p>Two tiers:
 * <ul>
 *   <li><b>Global default</b> — the rate that applies to every owner
 *       who doesn't have a specific override. Change and save;
 *       effect kicks in on the next payment initiate call.</li>
 *   <li><b>Per-owner overrides</b> — waive commissions for specific
 *       owners (loyalty, promo, VIP) or charge them differently.
 *       Wins over the global rate for that owner only.</li>
 * </ul>
 *
 * <p>Zero rate is fully supported — set global to 0 % and every payment
 * routes 100 % to the owner. Handy for the launch phase where the
 * platform absorbs the operating cost to attract owners.
 */
export function AdminCommissionPage() {
  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Commission"
        description="Set what the platform keeps from each rent payment. Zero is fine — change any time; the next payment picks it up."
      />

      <GlobalRateCard />
      <div className="mt-6">
        <OverridesCard />
      </div>
    </div>
  );
}

/* ─────────────────────────── Global rate ─────────────────────────── */

function GlobalRateCard() {
  const qc = useQueryClient();
  const globalQ = useQuery({
    queryKey: ["admin", "commission", "global"],
    queryFn: commissionApi.getGlobal,
  });

  // Local form state — populated once the query resolves. Untouched
  // saves are cheap on the backend (upsert on the same row) but we
  // still gate the Save button on "dirty" so the tap surface is
  // meaningful.
  const [pctInput, setPctInput] = useState<string>("");
  const [notes, setNotes] = useState<string>("");
  const [initialised, setInitialised] = useState(false);

  if (globalQ.data && !initialised) {
    setPctInput(String(globalQ.data.ratePercent));
    setNotes(globalQ.data.notes ?? "");
    setInitialised(true);
  }

  const parsedPct = useMemo(() => {
    const n = Number.parseFloat(pctInput);
    return Number.isFinite(n) ? n : NaN;
  }, [pctInput]);
  const invalid =
    !Number.isFinite(parsedPct) || parsedPct < 0 || parsedPct > 100;

  const setMut = useMutation({
    mutationFn: (body: CommissionRuleUpsertRequest) =>
      commissionApi.setGlobal(body),
    onSuccess: (updated) => {
      toast({
        title: "Global commission updated",
        description: `Now ${updated.ratePercent}% — every payment initiated from this moment onwards uses the new rate.`,
      });
      qc.invalidateQueries({ queryKey: ["admin", "commission"] });
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't save",
        description: extractErrorMessage(err),
      });
    },
  });

  const preview = useMemo(() => {
    if (invalid) return null;
    const total = 10000; // fixed sample so the copy reads naturally
    const fee = Math.round((total * (parsedPct * 100)) / 10000);
    return {
      total,
      fee,
      ownerAmount: total - fee,
    };
  }, [parsedPct, invalid]);

  const handleSave = () => {
    if (invalid) return;
    setMut.mutate({
      ratePercent: parsedPct,
      notes: notes.trim() || null,
    });
  };

  return (
    <Card>
      <CardContent className="p-6 sm:p-7">
        <div className="flex items-start gap-3 mb-5">
          <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
            <Percent className="size-5" />
          </div>
          <div>
            <h2 className="font-display font-semibold text-lg leading-tight">
              Global default
            </h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              Applies to every owner who doesn't have a specific override
              below.
            </p>
          </div>
        </div>

        {globalQ.isLoading && <Skeleton className="h-32" />}

        {globalQ.isError && (
          <p className="text-sm text-destructive">
            Couldn't load the current rate.{" "}
            {extractErrorMessage(globalQ.error)}
          </p>
        )}

        {globalQ.data && (
          <>
            <div className="grid gap-4 sm:grid-cols-[220px_1fr] items-end">
              <div>
                <Label htmlFor="pct">Commission rate</Label>
                <div className="relative mt-1.5">
                  <Input
                    id="pct"
                    inputMode="decimal"
                    value={pctInput}
                    onChange={(e) => setPctInput(e.target.value)}
                    placeholder="0"
                    className="pr-9 font-mono text-lg"
                  />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none">
                    %
                  </span>
                </div>
                {invalid && pctInput.trim() !== "" && (
                  <p className="text-xs text-destructive mt-1 flex items-center gap-1.5">
                    <AlertTriangle className="size-3.5" />
                    Enter a number between 0 and 100.
                  </p>
                )}
              </div>
              <div>
                <Label htmlFor="notes">Notes (optional)</Label>
                <Input
                  id="notes"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value.slice(0, 500))}
                  placeholder="e.g. Q4 launch promo — 0% until 31 Dec"
                  maxLength={500}
                  className="mt-1.5"
                />
              </div>
            </div>

            {preview && (
              <div className="mt-5 p-4 rounded-lg border bg-muted/40 grid gap-2">
                <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground">
                  <Wallet className="size-3.5" />
                  Preview on a {formatINR(preview.total)} rent
                </div>
                <div className="flex items-baseline gap-3 flex-wrap">
                  <p className="text-base">
                    Owner gets{" "}
                    <span className="font-semibold text-success">
                      {formatINR(preview.ownerAmount)}
                    </span>
                  </p>
                  <span className="text-muted-foreground">·</span>
                  <p className="text-base">
                    You keep{" "}
                    <span className="font-semibold text-primary">
                      {formatINR(preview.fee)}
                    </span>
                  </p>
                </div>
              </div>
            )}

            <div className="mt-5 flex items-center gap-3">
              <Button
                variant="gradient"
                onClick={handleSave}
                disabled={invalid || setMut.isPending}
              >
                {setMut.isPending && (
                  <Loader2 className="animate-spin size-4" />
                )}
                Save rate
              </Button>
              <p className="text-xs text-muted-foreground">
                Currently saved:{" "}
                <span className="font-mono font-medium text-foreground">
                  {globalQ.data.ratePercent}%
                </span>
              </p>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

/* ─────────────────────────── Per-owner overrides ─────────────────────────── */

function OverridesCard() {
  const qc = useQueryClient();
  const overridesQ = useQuery({
    queryKey: ["admin", "commission", "overrides"],
    queryFn: commissionApi.listOverrides,
  });

  const [newOwnerId, setNewOwnerId] = useState("");
  const [newPct, setNewPct] = useState("");
  const [newNotes, setNewNotes] = useState("");

  const upsertMut = useMutation({
    mutationFn: (args: {
      ownerId: string;
      body: CommissionRuleUpsertRequest;
    }) => commissionApi.upsertOverride(args.ownerId, args.body),
    onSuccess: (row) => {
      toast({
        title: "Override saved",
        description: `Owner ${row.ownerId?.slice(0, 8)}… now on ${row.ratePercent}%.`,
      });
      qc.invalidateQueries({ queryKey: ["admin", "commission"] });
      setNewOwnerId("");
      setNewPct("");
      setNewNotes("");
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't save override",
        description: extractErrorMessage(err),
      });
    },
  });

  const deleteMut = useMutation({
    mutationFn: (ownerId: string) => commissionApi.deleteOverride(ownerId),
    onSuccess: () => {
      toast({
        title: "Override removed",
        description: "That owner now uses the global default.",
      });
      qc.invalidateQueries({ queryKey: ["admin", "commission"] });
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't delete",
        description: extractErrorMessage(err),
      });
    },
  });

  const parsedNewPct = Number.parseFloat(newPct);
  const canAdd =
    newOwnerId.trim().length > 0 &&
    Number.isFinite(parsedNewPct) &&
    parsedNewPct >= 0 &&
    parsedNewPct <= 100;

  return (
    <Card>
      <CardContent className="p-6 sm:p-7">
        <div className="flex items-start gap-3 mb-5">
          <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
            <User className="size-5" />
          </div>
          <div>
            <h2 className="font-display font-semibold text-lg leading-tight">
              Per-owner overrides
            </h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              Charge specific owners a different rate — waivers, promos,
              higher rates. Leave blank to keep everyone on the global
              default.
            </p>
          </div>
        </div>

        {overridesQ.isLoading && <Skeleton className="h-20 mb-5" />}

        {overridesQ.data && overridesQ.data.length === 0 && (
          <p className="text-sm text-muted-foreground py-4 text-center">
            No overrides yet — every owner uses the global default.
          </p>
        )}

        {overridesQ.data && overridesQ.data.length > 0 && (
          <div className="border rounded-lg divide-y mb-5">
            {overridesQ.data.map((row) => (
              <div
                key={row.id}
                className="px-4 py-3 grid grid-cols-[1fr_auto_auto] gap-3 items-center"
              >
                <div className="min-w-0">
                  <p className="text-sm font-mono truncate">{row.ownerId}</p>
                  {row.notes && (
                    <p className="text-xs text-muted-foreground truncate">
                      {row.notes}
                    </p>
                  )}
                </div>
                <Badge variant="secondary" className="font-mono">
                  {row.ratePercent}%
                </Badge>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => {
                    if (!row.ownerId) return;
                    if (
                      window.confirm(
                        `Remove override for owner ${row.ownerId}? They'll revert to the global default rate.`,
                      )
                    ) {
                      deleteMut.mutate(row.ownerId);
                    }
                  }}
                  disabled={deleteMut.isPending}
                  title="Remove override"
                >
                  <Trash2 className="size-4 text-destructive" />
                </Button>
              </div>
            ))}
          </div>
        )}

        {/* Add form — always visible so a new override is one form fill away. */}
        <div className={cn("border rounded-lg p-4 bg-muted/30")}>
          <p className="text-xs uppercase tracking-wider text-muted-foreground font-semibold mb-3">
            Add or update an override
          </p>
          <div className="grid gap-3 sm:grid-cols-[1fr_140px]">
            <div>
              <Label htmlFor="ownerId">Owner ID (auth user id)</Label>
              <Input
                id="ownerId"
                value={newOwnerId}
                onChange={(e) => setNewOwnerId(e.target.value.trim())}
                placeholder="e.g. 47"
                className="mt-1.5 font-mono text-sm"
              />
            </div>
            <div>
              <Label htmlFor="newPct">Rate</Label>
              <div className="relative mt-1.5">
                <Input
                  id="newPct"
                  inputMode="decimal"
                  value={newPct}
                  onChange={(e) => setNewPct(e.target.value)}
                  placeholder="0"
                  className="pr-9 font-mono"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none text-sm">
                  %
                </span>
              </div>
            </div>
          </div>
          <div className="mt-3">
            <Label htmlFor="newNotes">Notes (optional)</Label>
            <Input
              id="newNotes"
              value={newNotes}
              onChange={(e) => setNewNotes(e.target.value.slice(0, 500))}
              placeholder="e.g. Loyalty discount — first 100 owners"
              maxLength={500}
              className="mt-1.5"
            />
          </div>
          <div className="mt-4 flex items-center gap-2">
            <Button
              variant="default"
              size="sm"
              disabled={!canAdd || upsertMut.isPending}
              onClick={() => {
                if (!canAdd) return;
                upsertMut.mutate({
                  ownerId: newOwnerId.trim(),
                  body: {
                    ratePercent: parsedNewPct,
                    notes: newNotes.trim() || null,
                  },
                });
              }}
            >
              {upsertMut.isPending ? (
                <Loader2 className="animate-spin size-4" />
              ) : (
                <Plus className="size-4" />
              )}
              Save override
            </Button>
            <p className="text-xs text-muted-foreground">
              Existing override for the same owner is overwritten.
            </p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
