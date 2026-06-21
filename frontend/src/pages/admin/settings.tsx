import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, ShieldCheck, Wallet } from "lucide-react";
import { adminSettingsApi } from "@/lib/api/admin-settings";
import { extractErrorMessage } from "@/lib/api/client";
import { Card } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { toast } from "@/hooks/use-toast";
import { formatDate } from "@/lib/utils";
import type { SystemSettingResponse } from "@/types/api";

const KEY_MAINTAINER_PAYMENT = "maintainer_payment_enabled";

/**
 * Global feature toggles, admin-only. Currently one switch:
 *
 *  - Maintainer activation fee — when ON, every NEW maintainer
 *    signup gets a 30-day free trial then a Pay/Skip modal on the
 *    dashboard (two skips, then forced). When OFF (default on first
 *    deploy), signup is free and the dashboard is never gated.
 *
 * <p>Existing maintainers are grandfathered at signup time and are
 * unaffected by any toggle flip — they stay free forever.
 */
export function AdminSettingsPage() {
  const qc = useQueryClient();
  const [pendingValue, setPendingValue] = useState<boolean | null>(null);

  const settingsQ = useQuery({
    queryKey: ["admin", "settings"],
    queryFn: () => adminSettingsApi.listAll(),
    retry: false,
  });

  const maintainerToggle = useMemo<SystemSettingResponse | undefined>(
    () =>
      (settingsQ.data ?? []).find(
        (s) => s.settingKey === KEY_MAINTAINER_PAYMENT,
      ),
    [settingsQ.data],
  );
  const maintainerEnabled = maintainerToggle?.value === "true";

  const flipMut = useMutation({
    mutationFn: (enabled: boolean) =>
      adminSettingsApi.setMaintainerPaymentEnabled(enabled),
    onSuccess: (row) => {
      qc.setQueryData<SystemSettingResponse[]>(
        ["admin", "settings"],
        (prev) => {
          if (!prev) return [row];
          const others = prev.filter((s) => s.settingKey !== row.settingKey);
          return [...others, row];
        },
      );
      toast({
        title: `Maintainer activation fee ${row.value === "true" ? "enabled" : "disabled"}`,
        description:
          row.value === "true"
            ? "New maintainer signups now go through the 30-day trial."
            : "New maintainer signups are free again. Existing users unaffected.",
      });
      setPendingValue(null);
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't update setting",
        description: extractErrorMessage(err),
      });
      setPendingValue(null);
    },
  });

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Settings"
        description="Platform-wide feature toggles. Admin only."
      />

      {settingsQ.isError ? (
        <EmptyState
          variant="info"
          icon={ShieldCheck}
          title="Couldn't load settings"
          description="If this is a 403 you may not have admin role. Otherwise the auth-service is unreachable."
        />
      ) : (
        <Card className="p-5">
          <div className="flex items-start gap-4">
            <div className="rounded-xl bg-primary/10 p-2.5 mt-0.5">
              <Wallet className="size-5 text-primary" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-start gap-3 justify-between">
                <div>
                  <h3 className="font-display font-semibold text-base">
                    Maintainer activation fee
                  </h3>
                  <p className="text-sm text-muted-foreground mt-0.5">
                    When enabled, new maintainer signups get a 30-day free
                    trial, then a Pay&nbsp;₹999 / Skip prompt on every
                    dashboard visit. Two skips allowed, third prompt is
                    mandatory. Existing maintainers are grandfathered.
                  </p>
                </div>
                <Switch
                  checked={maintainerEnabled}
                  disabled={settingsQ.isLoading || flipMut.isPending}
                  onCheckedChange={(v: boolean) => setPendingValue(v)}
                  aria-label="Toggle maintainer activation fee"
                />
              </div>
              <div className="mt-4 text-[11px] text-muted-foreground flex items-center gap-3">
                {flipMut.isPending && (
                  <Loader2 className="size-3 animate-spin" />
                )}
                {maintainerToggle ? (
                  <span>
                    Current: <b>{maintainerEnabled ? "Enabled" : "Disabled"}</b>
                    {" — last changed "}
                    {maintainerToggle.updatedAt
                      ? formatDate(maintainerToggle.updatedAt)
                      : "never"}
                    {maintainerToggle.updatedBy
                      ? ` by admin #${maintainerToggle.updatedBy}`
                      : " (seed value, never touched)"}
                  </span>
                ) : (
                  <span>Loading…</span>
                )}
              </div>
            </div>
          </div>
        </Card>
      )}

      {/* Confirmation dialog — flipping a platform-wide switch deserves
          a "are you sure" gate. The Switch above only sets pendingValue;
          this dialog is what actually fires the mutation. */}
      <Dialog
        open={pendingValue !== null}
        onOpenChange={(o) => {
          if (!o) setPendingValue(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pendingValue
                ? "Enable maintainer activation fee?"
                : "Disable maintainer activation fee?"}
            </DialogTitle>
            <DialogDescription>
              {pendingValue ? (
                <>
                  Every <b>new</b> maintainer signup from now on goes through
                  the 30-day free trial → 2 skips → forced payment flow.
                  Existing maintainer accounts are unaffected — they stay
                  free forever.
                </>
              ) : (
                <>
                  New maintainer signups become free again. Maintainers
                  currently in TRIAL or SKIP_GRACE state will see the modal
                  disappear on their next dashboard load.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setPendingValue(null)}
              disabled={flipMut.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="gradient"
              disabled={flipMut.isPending || pendingValue === null}
              onClick={() => {
                if (pendingValue !== null) flipMut.mutate(pendingValue);
              }}
            >
              {flipMut.isPending ? (
                <Loader2 className="animate-spin size-4 mr-2" />
              ) : null}
              {pendingValue ? "Enable" : "Disable"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
