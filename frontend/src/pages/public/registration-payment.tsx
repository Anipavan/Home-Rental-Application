import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCircle2, Loader2, Lock, ShieldCheck, Users } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { authApi } from "@/lib/api/auth";
import { claimsApi } from "@/lib/api/claims";
import { extractErrorMessage } from "@/lib/api/client";
import { registrationPaymentApi } from "@/lib/api/registration-payment";
import { useAuthStore } from "@/stores/auth-store";
import { toast } from "@/hooks/use-toast";

/**
 * Bundle stashed by {@code register.tsx} just before navigating here.
 * Lives in sessionStorage so a tab refresh during the paywall step
 * doesn't strand the user — the Pay button can pick the bundle up
 * again. Cleared on successful activation OR when the user explicitly
 * cancels and goes back.
 *
 * <p>{@code userPassword} is here so the page can auto-log-in after
 * Razorpay confirms PAID, then post the MAINTAINER claim from the
 * fresh session. sessionStorage is per-tab and cleared on close, so
 * the password lifetime is the active paywall session only.
 */
type PendingBundle = {
  authUserId: number;
  paymentId: string;
  paymentToken: string;
  amountInr: number;
  userName: string;
  userPassword: string;
  /** Optional society-claim payload posted after activation. */
  claim?: {
    buildingId: string;
    flatNumber?: string;
    note?: string;
  };
};

const SESSION_KEY = "hra:pending-maintainer-signup";

export function RegistrationPaymentPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [bundle, setBundle] = useState<PendingBundle | null>(null);
  const [phase, setPhase] = useState<
    "idle" | "opening" | "verifying" | "activating" | "done" | "error"
  >("idle");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Hydrate the stashed bundle on mount. If it's missing, the user
  // hit /registration-payment directly without going through
  // /register — send them back.
  useEffect(() => {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) {
      toast({
        variant: "destructive",
        title: "No pending registration",
        description: "Start over from the signup screen.",
      });
      navigate("/register", { replace: true });
      return;
    }
    try {
      setBundle(JSON.parse(raw) as PendingBundle);
    } catch {
      sessionStorage.removeItem(SESSION_KEY);
      navigate("/register", { replace: true });
    }
  }, [navigate]);

  /**
   * Ensure Razorpay's Checkout.js is loaded. We inject it on demand
   * (not in index.html) so the script cost only hits users who
   * actually reach the paywall. Idempotent — re-renders don't double-
   * inject.
   */
  function loadCheckoutScript(): Promise<void> {
    return new Promise((resolve, reject) => {
      const w = window as unknown as { Razorpay?: unknown };
      if (w.Razorpay) {
        resolve();
        return;
      }
      const existing = document.querySelector(
        'script[src="https://checkout.razorpay.com/v1/checkout.js"]',
      );
      if (existing) {
        existing.addEventListener("load", () => resolve());
        existing.addEventListener("error", () =>
          reject(new Error("Razorpay checkout script failed to load")),
        );
        return;
      }
      const s = document.createElement("script");
      s.src = "https://checkout.razorpay.com/v1/checkout.js";
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error("Razorpay checkout script failed to load"));
      document.body.appendChild(s);
    });
  }

  async function startPayment() {
    if (!bundle) return;
    setPhase("opening");
    setErrorMsg(null);
    try {
      await loadCheckoutScript();
      // Razorpay test mode supports NET_BANKING as a one-click happy
      // path — same method the existing rent / society flows default
      // to when launching the modal. The user can switch to UPI / card
      // inside the Razorpay modal itself.
      const init = await registrationPaymentApi.initiate(bundle.paymentToken, {
        paymentId: bundle.paymentId,
        paymentMethod: "NET_BANKING",
      });

      if (!init.gatewayKeyId) {
        throw new Error(
          "Payment gateway not fully configured — please contact support.",
        );
      }

      // Open the Razorpay Checkout.js modal. The order id + key id
      // came back from /initiate. handler() fires on user-confirmed
      // success — we verify on our side and only then trust it.
      const w = window as unknown as {
        Razorpay: new (opts: Record<string, unknown>) => { open: () => void };
      };
      const rzp = new w.Razorpay({
        key: init.gatewayKeyId,
        order_id: init.gatewayOrderId,
        amount: Math.round(bundle.amountInr * 100), // paise
        currency: "INR",
        name: "Anirudh Homes",
        description: "Maintainer activation fee",
        prefill: { name: bundle.userName },
        theme: { color: "#0d9488" },
        handler: async (resp: {
          razorpay_payment_id: string;
          razorpay_order_id: string;
          razorpay_signature: string;
        }) => {
          try {
            setPhase("verifying");
            const verifyResp = await registrationPaymentApi.verify(
              bundle.paymentToken,
              {
                paymentId: bundle.paymentId,
                razorpayPaymentId: resp.razorpay_payment_id,
                razorpayOrderId: resp.razorpay_order_id,
                razorpaySignature: resp.razorpay_signature,
              },
            );
            if (verifyResp.status !== "PAID") {
              throw new Error("Payment was not confirmed as PAID");
            }
            await activateAndContinue();
          } catch (err) {
            setPhase("error");
            setErrorMsg(extractErrorMessage(err));
          }
        },
        modal: {
          ondismiss: () => {
            // User closed the modal — go back to idle so they can
            // retry. The PENDING Payment row stays valid; the
            // expiry-sweep handles abandonment after 30 minutes.
            setPhase("idle");
          },
        },
      });
      rzp.open();
    } catch (err) {
      setPhase("error");
      setErrorMsg(extractErrorMessage(err));
    }
  }

  /**
   * Post-PAID: log the user in with their stashed credentials, post
   * the society membership claim if one was queued, then send them to
   * the pending-claim screen.
   */
  async function activateAndContinue() {
    if (!bundle) return;
    setPhase("activating");
    try {
      const auth = await authApi.login({
        userName: bundle.userName,
        password: bundle.userPassword,
      });
      setSession(auth);
      if (bundle.claim) {
        await claimsApi.create({
          buildingId: bundle.claim.buildingId,
          requestedRole: "MAINTAINER",
          claimedFlatNumber: bundle.claim.flatNumber || undefined,
          applicantNote: bundle.claim.note || undefined,
        });
      }
      sessionStorage.removeItem(SESSION_KEY);
      setPhase("done");
      toast({
        title: "Account activated",
        description: bundle.claim
          ? "Your claim is with the building owner."
          : "Welcome to Anirudh Homes.",
      });
      navigate(bundle.claim ? "/pending-claim" : "/app", { replace: true });
    } catch (err) {
      setPhase("error");
      setErrorMsg(extractErrorMessage(err));
    }
  }

  if (!bundle) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="animate-spin size-6 text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-secondary/30 relative overflow-hidden">
      <div
        aria-hidden
        className="pointer-events-none absolute -top-32 left-[-10%] size-[480px] rounded-full bg-gradient-to-br from-emerald-400/25 via-teal-400/15 to-transparent blur-3xl animate-ambient-drift-slow"
      />
      <div className="w-full max-w-md relative animate-fade-in">
        <div className="flex justify-center mb-6">
          <Logo size="lg" />
        </div>

        <Card className="p-6 sm:p-8">
          <div className="flex items-center gap-2 text-primary mb-2">
            <Users className="size-5" />
            <span className="text-xs font-semibold tracking-wide uppercase">
              Maintainer Activation
            </span>
          </div>
          <h1 className="font-display text-2xl sm:text-3xl font-bold tracking-tight">
            Activate your account
          </h1>
          <p className="text-muted-foreground mt-1.5">
            One-time fee to start managing a society. No recurring charges.
          </p>

          <div className="mt-6 rounded-xl border border-primary/30 bg-primary/5 p-5">
            <div className="flex items-baseline justify-between">
              <span className="text-sm text-muted-foreground">Activation fee</span>
              <span className="font-display text-3xl font-bold">
                ₹{bundle.amountInr.toLocaleString("en-IN")}
              </span>
            </div>
            <ul className="mt-4 space-y-2 text-sm">
              <li className="flex gap-2">
                <CheckCircle2 className="size-4 text-success shrink-0 mt-0.5" />
                <span>One-time payment — never charged again.</span>
              </li>
              <li className="flex gap-2">
                <CheckCircle2 className="size-4 text-success shrink-0 mt-0.5" />
                <span>Full society dashboard once the owner approves you.</span>
              </li>
              <li className="flex gap-2">
                <CheckCircle2 className="size-4 text-success shrink-0 mt-0.5" />
                <span>Payments processed by Razorpay over a secure channel.</span>
              </li>
            </ul>
          </div>

          {errorMsg && (
            <p
              role="alert"
              className="mt-4 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md px-3 py-2"
            >
              {errorMsg}
            </p>
          )}

          <Button
            type="button"
            size="lg"
            variant="gradient"
            className="w-full mt-6"
            disabled={
              phase === "opening" ||
              phase === "verifying" ||
              phase === "activating" ||
              phase === "done"
            }
            onClick={startPayment}
          >
            {phase === "opening" && (
              <Loader2 className="animate-spin size-4 mr-2" />
            )}
            {phase === "verifying" && (
              <Loader2 className="animate-spin size-4 mr-2" />
            )}
            {phase === "activating" && (
              <Loader2 className="animate-spin size-4 mr-2" />
            )}
            {phase === "idle" || phase === "error"
              ? `Pay ₹${bundle.amountInr.toLocaleString("en-IN")} & activate`
              : phase === "opening"
                ? "Opening Razorpay…"
                : phase === "verifying"
                  ? "Verifying payment…"
                  : phase === "activating"
                    ? "Activating account…"
                    : "Activated"}
          </Button>

          <div className="mt-4 flex items-center justify-center gap-2 text-[11px] text-muted-foreground">
            <Lock className="size-3" />
            <span>Secured by Razorpay</span>
            <span aria-hidden>·</span>
            <ShieldCheck className="size-3" />
            <span>UPI / Card / Net banking</span>
          </div>
        </Card>

        <p className="text-xs text-muted-foreground text-center mt-6">
          Closing this tab before payment is fine — we hold your details
          for 24 hours so you can come back and finish from the sign-in
          screen.
        </p>
      </div>
    </div>
  );
}

/**
 * Exported so the register page can stash the bundle under the same
 * key the paywall reads from. Keeping it co-located prevents the
 * two files from drifting on the storage shape.
 */
export const PENDING_MAINTAINER_SESSION_KEY = SESSION_KEY;
