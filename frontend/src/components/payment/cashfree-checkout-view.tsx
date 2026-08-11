import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { ArrowLeft, Loader2, Lock, Shield, Wallet } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/page-header";
import { paymentGateway } from "@/lib/api/payment-gateway";
import { openCashfreeCheckout } from "@/lib/cashfree";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse } from "@/types/api";

/**
 * Cashfree Easy Split checkout for tenants whose owner is
 * payout-ready. Called from the Pay page after
 * {@code paymentGateway.isOwnerPayoutReady(ownerId)} returns true.
 *
 * <p>Flow:
 * <ol>
 *   <li>Render a "ready to pay" summary card.</li>
 *   <li>On "Pay now" click: POST /payments/initiate to mint a
 *       Cashfree order (split configured server-side from the
 *       CommissionService).</li>
 *   <li>Take the returned {@code paymentSessionId} and hand it to
 *       Cashfree's Checkout SDK — the tenant leaves our SPA for
 *       Cashfree's hosted payment UI (UPI / cards / netbanking).</li>
 *   <li>After payment, Cashfree redirects to
 *       {@code /app/payments/:id/return}, and the async webhook
 *       marks the Payment row PAID on the backend.</li>
 * </ol>
 *
 * <p>If the initiate call fails OR the SDK can't load (CSP block,
 * offline), toast the error and offer a "Try direct-UPI instead"
 * link so the tenant isn't stranded.
 */
export function CashfreeCheckoutView({
  payment,
  flatLabel,
  onFallbackToDirectUpi,
}: {
  payment: PaymentResponse;
  flatLabel: string;
  /**
   * Called when the Cashfree flow can't start (SDK load failure,
   * server error). Lets the Pay page swap in the direct-UPI QR view
   * so the tenant can still pay.
   */
  onFallbackToDirectUpi: () => void;
}) {
  const total = payment.totalAmount ?? payment.amount;
  const [launching, setLaunching] = useState(false);
  // Cashfree environment — sandbox vs production — is a build-time
  // decision baked in via VITE_CASHFREE_ENV. Sandbox is the default so
  // a misconfigured build stays visibly harmless (real cards get "TEST
  // MODE" banners across the top of Cashfree's checkout).
  const cashfreeEnv =
    (import.meta.env.VITE_CASHFREE_ENV as "sandbox" | "production") ??
    "sandbox";

  const initiateMut = useMutation({
    mutationFn: () =>
      paymentGateway.initiate({
        paymentId: payment.id,
        paymentMethod: "UPI",
        // Return URL points at the SPA return page; Cashfree tacks the
        // order id onto the query string on its way back. The return
        // page cross-checks the payment status via GET /payments/{id}
        // and renders success / failure accordingly.
        returnUrl:
          window.location.origin +
          `/app/payments/${payment.id}/return?src=cashfree`,
      }),
    onSuccess: async (res) => {
      if (!res.paymentSessionId) {
        toast({
          variant: "destructive",
          title: "Couldn't start payment",
          description:
            "Server didn't return a Cashfree session — falling back to direct-UPI.",
        });
        onFallbackToDirectUpi();
        return;
      }
      try {
        setLaunching(true);
        await openCashfreeCheckout(res.paymentSessionId, cashfreeEnv);
        // openCashfreeCheckout redirects the tab, so we normally never
        // reach here. If we do (SDK returned early), give the tenant
        // a way out.
        setLaunching(false);
      } catch (err) {
        setLaunching(false);
        toast({
          variant: "destructive",
          title: "Couldn't open Cashfree",
          description: extractErrorMessage(err),
        });
        onFallbackToDirectUpi();
      }
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't start payment",
        description: extractErrorMessage(err),
      });
    },
  });

  const pending = initiateMut.isPending || launching;

  return (
    <div className="animate-fade-in max-w-4xl mx-auto">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/app/payments">
          <ArrowLeft /> Back to payments
        </Link>
      </Button>
      <PageHeader
        title="Pay rent"
        description="Pick your preferred method — UPI, card, or net-banking — on the next screen."
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <Card>
          <CardContent className="p-6 sm:p-8 space-y-6">
            <div className="flex items-start gap-4">
              <div className="size-12 rounded-xl bg-primary/10 text-primary grid place-items-center shrink-0">
                <Wallet className="size-6" />
              </div>
              <div>
                <h2 className="font-display font-semibold text-xl leading-tight">
                  {formatINR(total)} to your owner
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                  You'll be taken to a secure Cashfree page to complete
                  the payment. Money settles directly to your owner —
                  we only hold our small platform fee.
                </p>
              </div>
            </div>

            <ul className="space-y-2 text-sm text-muted-foreground">
              <li className="flex items-center gap-2">
                <Shield className="size-4 text-primary" />
                Cashfree is RBI-regulated and PCI-DSS certified.
              </li>
              <li className="flex items-center gap-2">
                <Lock className="size-4 text-primary" />
                Your card / UPI credentials never touch our servers.
              </li>
            </ul>

            <div className="pt-2">
              <Button
                variant="gradient"
                size="lg"
                className="w-full sm:w-auto"
                disabled={pending}
                onClick={() => initiateMut.mutate()}
              >
                {pending ? (
                  <>
                    <Loader2 className="animate-spin" />
                    Opening Cashfree…
                  </>
                ) : (
                  <>
                    <Wallet /> Pay {formatINR(total)}
                  </>
                )}
              </Button>
              <p className="text-xs text-muted-foreground mt-3">
                Prefer to scan a QR code?{" "}
                <button
                  type="button"
                  className="text-primary hover:underline"
                  onClick={onFallbackToDirectUpi}
                >
                  Use direct-UPI instead
                </button>
              </p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5 sm:p-6">
            <p className="text-xs uppercase tracking-wider text-muted-foreground font-semibold mb-3">
              Order summary
            </p>
            <div className="space-y-2 text-sm">
              <Row label="Rent" value={formatINR(payment.amount)} />
              {payment.lateFee != null && payment.lateFee > 0 && (
                <Row label="Late fee" value={formatINR(payment.lateFee)} />
              )}
              <div className="border-t pt-2 mt-2">
                <Row
                  label="Total payable"
                  value={formatINR(total)}
                  emphasise
                />
              </div>
              <div className="pt-3 text-xs text-muted-foreground border-t mt-3">
                <p>For flat {flatLabel}</p>
                <p>Due {formatDate(payment.dueDate)}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Row({
  label,
  value,
  emphasise,
}: {
  label: string;
  value: string;
  emphasise?: boolean;
}) {
  return (
    <div className="flex justify-between gap-3">
      <span className={emphasise ? "font-semibold" : "text-muted-foreground"}>
        {label}
      </span>
      <span className={emphasise ? "font-semibold" : ""}>{value}</span>
    </div>
  );
}
