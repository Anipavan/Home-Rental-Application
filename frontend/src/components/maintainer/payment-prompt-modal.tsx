import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { CheckCircle2, Loader2, LogOut, ShieldCheck, Users } from "lucide-react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { maintainerPaymentApi } from "@/lib/api/maintainer-payment";
import { extractErrorMessage } from "@/lib/api/client";
import { useAuthStore } from "@/stores/auth-store";
import { authApi } from "@/lib/api/auth";
import { PENDING_MAINTAINER_SESSION_KEY } from "@/pages/public/registration-payment";
import { toast } from "@/hooks/use-toast";
import type { MaintainerPaymentStatus } from "@/types/api";

/**
 * Activation-fee prompt for the maintainer dashboard. Two flavours:
 *
 *  - PROMPT: user is past trial, has skips left. Renders Pay and
 *    Skip buttons. Dismissable via Skip only — clicking outside
 *    does nothing because the parent
 *    {@code MaintainerPaymentGate} re-opens it immediately.
 *  - FORCED: user has burned all skips. Renders Pay only, plus a
 *    Log out link. No dismiss — the only way past this modal is
 *    to pay (or log out and never come back).
 *
 * <p>Pay path: calls {@code /auth/me/payment/initiate}, stashes the
 * REG_PAY bundle in sessionStorage under the same key the existing
 * {@code RegistrationPaymentPage} reads, navigates there. The
 * paywall page already knows how to handle a bundle without a
 * password (logged-in flow).
 *
 * <p>Skip path: POSTs {@code /auth/me/payment-skip}, then asks the
 * parent gate to refetch status (so the user sees SKIP_GRACE).
 */
export function PaymentPromptModal({
  status,
  skipsLeft,
  amountInr,
  userName,
  onSkipped,
}: {
  status: Extract<MaintainerPaymentStatus, "PROMPT" | "FORCED">;
  skipsLeft: number;
  amountInr: number;
  userName: string;
  onSkipped: () => void;
}) {
  const navigate = useNavigate();
  const clearSession = useAuthStore((s) => s.clear);
  const refreshToken = useAuthStore((s) => s.refreshToken);
  const [logoutPending, setLogoutPending] = useState(false);

  const initiateMut = useMutation({
    mutationFn: () => maintainerPaymentApi.initiate(),
    onSuccess: (bundle) => {
      // Stash under the same key /registration-payment reads so the
      // page picks it up automatically. No password / claim — the
      // page already detects "no userPassword" and switches to the
      // logged-in success path (route to /maintainer instead of
      // /login).
      sessionStorage.setItem(
        PENDING_MAINTAINER_SESSION_KEY,
        JSON.stringify({
          authUserId: bundle.authUserId,
          paymentId: bundle.paymentId,
          paymentToken: bundle.paymentToken,
          amountInr: bundle.amountInr,
          userName,
        }),
      );
      navigate("/registration-payment");
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't start payment",
        description: extractErrorMessage(err),
      });
    },
  });

  const skipMut = useMutation({
    mutationFn: () => maintainerPaymentApi.skip(),
    onSuccess: () => {
      toast({
        title: "Reminder snoozed",
        description:
          "We'll prompt you again in 4 days. You can pay any time from this banner.",
      });
      onSkipped();
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't skip",
        description: extractErrorMessage(err),
      });
    },
  });

  async function handleLogout() {
    setLogoutPending(true);
    try {
      if (refreshToken) {
        try {
          await authApi.logout(refreshToken);
        } catch {
          // Best-effort — log out client-side even if the server call
          // fails so the user actually exits the gate.
        }
      }
      clearSession();
      navigate("/login", { replace: true });
    } finally {
      setLogoutPending(false);
    }
  }

  const forced = status === "FORCED";

  return (
    <Dialog
      open
      // Disable outside-click + escape dismissal — the gate owns
      // whether the modal closes.
      onOpenChange={() => {}}
    >
      <DialogContent
        // Remove the default close button when forced — there's no
        // way out except Pay or Log out.
        showClose={!forced}
        onInteractOutside={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => e.preventDefault()}
        className="sm:max-w-md"
      >
        <div className="flex items-center gap-2 text-primary mb-2">
          <Users className="size-5" />
          <span className="text-xs font-semibold tracking-wide uppercase">
            Maintainer Activation
          </span>
        </div>
        <h2 className="font-display text-2xl font-bold tracking-tight">
          {forced
            ? "Activation required to continue"
            : "Activate your maintainer access"}
        </h2>
        <p className="text-sm text-muted-foreground mt-1.5">
          {forced
            ? "You've used both reminder skips. Activate your account with a one-time ₹999 payment to keep using the dashboard."
            : "Your free trial has ended. Activate now to remove this prompt, or skip and we'll remind you in 4 days."}
        </p>

        <div className="mt-5 rounded-xl border border-primary/30 bg-primary/5 p-5">
          <div className="flex items-baseline justify-between">
            <span className="text-sm text-muted-foreground">Activation fee</span>
            <span className="font-display text-3xl font-bold">
              ₹{amountInr.toLocaleString("en-IN")}
            </span>
          </div>
          <ul className="mt-4 space-y-2 text-sm">
            <li className="flex gap-2">
              <CheckCircle2 className="size-4 text-success shrink-0 mt-0.5" />
              <span>One-time — never charged again.</span>
            </li>
            <li className="flex gap-2">
              <CheckCircle2 className="size-4 text-success shrink-0 mt-0.5" />
              <span>Razorpay — UPI, card, or net banking.</span>
            </li>
            {!forced && (
              <li className="flex gap-2">
                <CheckCircle2 className="size-4 text-success shrink-0 mt-0.5" />
                <span>
                  Skip available — {skipsLeft} of 2 reminders remaining.
                </span>
              </li>
            )}
          </ul>
        </div>

        <div className="mt-6 flex flex-col gap-2">
          <Button
            size="lg"
            variant="gradient"
            disabled={initiateMut.isPending}
            onClick={() => initiateMut.mutate()}
          >
            {initiateMut.isPending && (
              <Loader2 className="animate-spin size-4 mr-2" />
            )}
            Pay ₹{amountInr.toLocaleString("en-IN")} &amp; activate
          </Button>
          {!forced ? (
            <Button
              variant="ghost"
              disabled={skipMut.isPending || initiateMut.isPending}
              onClick={() => skipMut.mutate()}
            >
              {skipMut.isPending && (
                <Loader2 className="animate-spin size-4 mr-2" />
              )}
              Skip for 4 more days ({skipsLeft} of 2 left)
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              disabled={logoutPending}
              onClick={handleLogout}
            >
              {logoutPending ? (
                <Loader2 className="animate-spin size-4 mr-2" />
              ) : (
                <LogOut className="size-4 mr-2" />
              )}
              Log out
            </Button>
          )}
        </div>

        <div className="mt-4 flex items-center justify-center gap-2 text-[11px] text-muted-foreground">
          <ShieldCheck className="size-3" />
          <span>Secured by Razorpay · UPI / Card / Net banking</span>
        </div>
      </DialogContent>
    </Dialog>
  );
}
