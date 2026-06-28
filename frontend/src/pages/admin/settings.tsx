import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, MailCheck, ShieldCheck, Wallet } from "lucide-react";
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
const KEY_EMAIL_VERIFICATION = "email_verification_required";

/**
 * The settings page lists every system_settings row as a separate
 * confirmation-gated Switch. `pendingToggle` tracks which toggle the
 * admin is about to confirm so the same Dialog can serve both flips
 * (and any future ones) without duplicating modal markup.
 */
type PendingToggle =
  | { key: typeof KEY_MAINTAINER_PAYMENT; value: boolean }
  | { key: typeof KEY_EMAIL_VERIFICATION; value: boolean };

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
  const [pendingToggle, setPendingToggle] = useState<PendingToggle | null>(null);

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

  const emailToggle = useMemo<SystemSettingResponse | undefined>(
    () =>
      (settingsQ.data ?? []).find(
        (s) => s.settingKey === KEY_EMAIL_VERIFICATION,
      ),
    [settingsQ.data],
  );
  const emailEnabled = emailToggle?.value === "true";

  const flipMut = useMutation({
    mutationFn: (p: PendingToggle) =>
      p.key === KEY_MAINTAINER_PAYMENT
        ? adminSettingsApi.setMaintainerPaymentEnabled(p.value)
        : adminSettingsApi.setEmailVerificationRequired(p.value),
    onSuccess: (row) => {
      qc.setQueryData<SystemSettingResponse[]>(
        ["admin", "settings"],
        (prev) => {
          if (!prev) return [row];
          const others = prev.filter((s) => s.settingKey !== row.settingKey);
          return [...others, row];
        },
      );
      const isOn = row.value === "true";
      const isPayment = row.settingKey === KEY_MAINTAINER_PAYMENT;
      toast({
        title: isPayment
          ? `Maintainer activation fee ${isOn ? "enabled" : "disabled"}`
          : `Email verification ${isOn ? "required" : "no longer required"}`,
        description: isPayment
          ? isOn
            ? "New maintainer signups now go through the 30-day trial."
            : "New maintainer signups are free again. Existing users unaffected."
          : isOn
            ? "New signups must verify their email before logging in. Existing users unaffected."
            : "Email verification is no longer enforced at login.",
      });
      setPendingToggle(null);
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't update setting",
        description: extractErrorMessage(err),
      });
      setPendingToggle(null);
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
                  onCheckedChange={(v: boolean) =>
                    setPendingToggle({ key: KEY_MAINTAINER_PAYMENT, value: v })
                  }
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

      {settingsQ.isError ? null : (
        <Card className="p-5 mt-4">
          <div className="flex items-start gap-4">
            <div className="rounded-xl bg-primary/10 p-2.5 mt-0.5">
              <MailCheck className="size-5 text-primary" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-start gap-3 justify-between">
                <div>
                  <h3 className="font-display font-semibold text-base">
                    Email verification at signup
                  </h3>
                  <p className="text-sm text-muted-foreground mt-0.5">
                    When enabled, new signups must click the magic link in
                    their inbox before they can log in. Existing users are
                    grandfathered and keep logging in normally.
                  </p>
                </div>
                <Switch
                  checked={emailEnabled}
                  disabled={settingsQ.isLoading || flipMut.isPending}
                  onCheckedChange={(v: boolean) =>
                    setPendingToggle({ key: KEY_EMAIL_VERIFICATION, value: v })
                  }
                  aria-label="Toggle email verification at signup"
                />
              </div>
              <div className="mt-4 text-[11px] text-muted-foreground flex items-center gap-3">
                {flipMut.isPending && (
                  <Loader2 className="size-3 animate-spin" />
                )}
                {emailToggle ? (
                  <span>
                    Current: <b>{emailEnabled ? "Required" : "Not required"}</b>
                    {" — last changed "}
                    {emailToggle.updatedAt
                      ? formatDate(emailToggle.updatedAt)
                      : "never"}
                    {emailToggle.updatedBy
                      ? ` by admin #${emailToggle.updatedBy}`
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
          a "are you sure" gate. The Switch above only sets pendingToggle;
          this dialog is what actually fires the mutation. */}
      <Dialog
        open={pendingToggle !== null}
        onOpenChange={(o) => {
          if (!o) setPendingToggle(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pendingToggle?.key === KEY_MAINTAINER_PAYMENT
                ? pendingToggle.value
                  ? "Enable maintainer activation fee?"
                  : "Disable maintainer activation fee?"
                : pendingToggle?.value
                  ? "Require email verification at signup?"
                  : "Stop requiring email verification?"}
            </DialogTitle>
            <DialogDescription>
              {pendingToggle?.key === KEY_MAINTAINER_PAYMENT ? (
                pendingToggle.value ? (
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
                )
              ) : pendingToggle?.value ? (
                <>
                  Every <b>new</b> signup must click a magic link in their
                  inbox before logging in. Existing users are grandfathered
                  and unaffected.
                </>
              ) : (
                <>
                  Email verification at signup will be turned off. Anyone who
                  signs up next can log in immediately, even with an unverified
                  email address.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setPendingToggle(null)}
              disabled={flipMut.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="gradient"
              disabled={flipMut.isPending || pendingToggle === null}
              onClick={() => {
                if (pendingToggle) flipMut.mutate(pendingToggle);
              }}
            >
              {flipMut.isPending ? (
                <Loader2 className="animate-spin size-4 mr-2" />
              ) : null}
              {pendingToggle?.value ? "Enable" : "Disable"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
