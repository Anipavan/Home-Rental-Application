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
          setPhase("failed");
          setError("Could not verify the payment. Try again from the dashboard.");
        });
      return;
    }

    paymentGateway
      .verify({ paymentId, gatewayOrderId, transactionId, signature })
      .then((p) => {
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
        setPhase("failed");
        setError(extractErrorMessage(e, "Verification failed."));
      });
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
          <Button asChild variant="gradient" size="lg">
            <Link to="/app/payments">Back to payments</Link>
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
          <Link to="/app/payments">Back to payments</Link>
        </Button>
      </div>
    </div>
  );
}
