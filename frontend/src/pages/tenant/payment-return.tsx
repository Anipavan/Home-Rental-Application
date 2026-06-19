import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { paymentGateway } from "@/lib/api/payment-gateway";
import { paymentsApi } from "@/lib/api/payments";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { extractErrorMessage } from "@/lib/api/client";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse } from "@/types/api";

/**
 * Lands here after the payment gateway redirects the user back. Pulls the four
 * verification params off the URL (gatewayOrderId / transactionId / signature)
 * and calls POST /payments/verify, which marks the payment PAID server-side.
 *
 * Accepts both clean naming (gatewayOrderId / transactionId / signature) and
 * Razorpay's native names (razorpay_order_id / razorpay_payment_id / razorpay_signature).
 */
export function PaymentReturnPage() {
  const { id } = useParams();
  const paymentId = id ?? "";
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [phase, setPhase] = useState<"verifying" | "success" | "failed">(
    "verifying",
  );
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!paymentId) {
      setPhase("failed");
      setError("Missing payment id in URL.");
      return;
    }

    // Audit M25: re-entrancy guard for the verify call. The effect
    // re-runs on every param change (and on remounts during HMR /
    // back-button navigation). Without a guard, a user who closes
    // the tab mid-redirect and re-opens it can fire verify() in
    // parallel with the gateway's webhook + the payment row's
    // status-poll. The local `cancelled` flag drops late results so
    // we don't overwrite a SUCCESS phase with a stale FAILED one,
    // and the backend's idempotent webhook (C13) makes parallel
    // verify calls safe to begin with.
    let cancelled = false;

    const gatewayOrderId =
      params.get("gatewayOrderId") ?? params.get("razorpay_order_id") ?? "";
    const transactionId =
      params.get("transactionId") ?? params.get("razorpay_payment_id") ?? "";
    const signature =
      params.get("signature") ?? params.get("razorpay_signature") ?? "";

    if (!gatewayOrderId || !transactionId || !signature) {
      // No verification params — the user might have hit cancel on the gateway.
      // Fall back to checking the payment row in case a webhook already settled it.
      paymentsApi
        .get(paymentId)
        .then((p) => {
          if (cancelled) return;
          if (p.status === "PAID") {
            setPayment(p);
            setPhase("success");
          } else {
            setPhase("failed");
            setError(
              "Payment was cancelled or didn't return verification details.",
            );
          }
        })
        .catch(() => {
          if (cancelled) return;
          setPhase("failed");
          setError("Could not verify the payment. Try again from the dashboard.");
        });
      return () => {
        cancelled = true;
      };
    }

    paymentGateway
      .verify({ paymentId, gatewayOrderId, transactionId, signature })
      .then((p) => {
        if (cancelled) return;
        setPayment(p);
        if (p.status === "PAID") {
          setPhase("success");
          qc.invalidateQueries({ queryKey: ["payment", paymentId] });
          qc.invalidateQueries({ queryKey: ["my-payments"] });
        } else {
          setPhase("failed");
          setError(p.failureReason ?? `Payment ended in status: ${p.status}`);
        }
      })
      .catch((e) => {
        if (cancelled) return;
        // M25: if the webhook already settled the payment while our
        // verify() was in flight, the verify call may 4xx/409. Fall
        // back to a single GET — if the row is PAID, we win
        // regardless of the verify failure.
        paymentsApi
          .get(paymentId)
          .then((p) => {
            if (cancelled) return;
            if (p.status === "PAID") {
              setPayment(p);
              setPhase("success");
              qc.invalidateQueries({ queryKey: ["payment", paymentId] });
              qc.invalidateQueries({ queryKey: ["my-payments"] });
              return;
            }
            setPhase("failed");
            setError(extractErrorMessage(e, "Verification failed."));
          })
          .catch(() => {
            if (cancelled) return;
            setPhase("failed");
            setError(extractErrorMessage(e, "Verification failed."));
          });
      });

    return () => {
      cancelled = true;
    };
  }, [paymentId, params, qc]);

  if (phase === "verifying") {
    return (
      <div className="min-h-[60vh] grid place-items-center">
        <div className="text-center">
          <Loader2 className="animate-spin size-10 mx-auto text-primary" />
          <h2 className="font-display text-xl font-semibold mt-5">
            Confirming your payment…
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            This usually takes a couple of seconds.
          </p>
        </div>
      </div>
    );
  }

  if (phase === "success" && payment) {
    return (
      <div className="max-w-xl mx-auto py-12 text-center animate-fade-in">
        <div className="size-20 rounded-full bg-success/15 grid place-items-center mx-auto">
          <CheckCircle2 className="size-10 text-success" />
        </div>
        <h2 className="font-display text-3xl font-bold mt-6">
          Payment successful
        </h2>
        <p className="text-muted-foreground mt-2">
          We've received {formatINR(payment.totalAmount ?? payment.amount)}.
        </p>
        <Card className="mt-7 text-left">
          <CardContent className="p-6 space-y-2 text-sm">
            {payment.transactionId && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Transaction ID</span>
                <span className="font-mono text-xs">{payment.transactionId}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-muted-foreground">Amount</span>
              <span className="font-semibold">
                {formatINR(payment.totalAmount ?? payment.amount)}
              </span>
            </div>
            {payment.paymentDate && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Paid on</span>
                <span>{formatDate(payment.paymentDate)}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-muted-foreground">Status</span>
              <Badge variant="success">Paid</Badge>
            </div>
          </CardContent>
        </Card>
        <div className="mt-7 flex justify-center">
          {/* Land the user back on the tab matching what they just
            * paid — SOCIETY_CHARGE → Maintenance, anything else → Rent. */}
          <Button asChild variant="gradient" size="lg">
            <Link
              to={
                payment.sourceType === "SOCIETY_CHARGE"
                  ? "/app/payments?type=maintenance"
                  : "/app/payments"
              }
            >
              Back to payments
            </Link>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-xl mx-auto py-12 text-center animate-fade-in">
      <div className="size-20 rounded-full bg-destructive/15 grid place-items-center mx-auto">
        <XCircle className="size-10 text-destructive" />
      </div>
      <h2 className="font-display text-3xl font-bold mt-6">
        Payment didn't go through
      </h2>
      <p className="text-muted-foreground mt-2">
        {error ?? "Try again or pick a different method."}
      </p>
      <div className="mt-7 flex flex-col sm:flex-row gap-2 justify-center">
        <Button
          variant="gradient"
          size="lg"
          onClick={() => navigate(`/app/payments/${paymentId}/pay`)}
        >
          Try again
        </Button>
        <Button asChild variant="outline" size="lg">
          <Link
            to={
              payment?.sourceType === "SOCIETY_CHARGE"
                ? "/app/payments?type=maintenance"
                : "/app/payments"
            }
          >
            Back to payments
          </Link>
        </Button>
      </div>
    </div>
  );
}
