import { useMemo, useRef } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowLeft, Loader2, Receipt, Smartphone } from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import { isRazorpayPaymentsDisabled } from "@/lib/feature-flags";
import type { FlatChargeCategory, FlatMaintenanceRow } from "@/types/api";

/**
 * Per-transaction cap for the bulk-pay launcher, in rupees.
 *
 * <p>Razorpay's test-mode hosted checkout rejects single transactions
 * over a per-bank simulator cap (mocksharp, netbanking simulator etc.)
 * with {@code BAD_REQUEST_ERROR / "Amount exceeds maximum"}. The
 * effective ceiling varies by bank but ~₹50k is universally safe.
 *
 * <p>Live-mode Razorpay (post-KYC) has no such cap — when we cut over
 * to live mode, bump this to a much higher value (or read it from an
 * env var). Until then, the cap stops the FE from constructing a
 * single Razorpay order that will fail at the gateway, which would
 * leave the resident on a "Payment didn't go through" page with no
 * clear way out.
 *
 * <p>If the total exceeds the cap, the bulk-pay button is disabled and
 * an inline banner tells the user to pay each charge individually
 * (which works fine for the per-row amounts we have).
 */
const BULK_PAY_MAX_INR = 49_999;

const CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

/**
 * Bulk-pay landing page for all DUE charges in a month.
 *
 * <p>Reached from the "Pay all" button on the tenant's society page
 * footer when the Total Due row is rendered. The destination route
 * is intentionally separate from the per-charge {@link SocietyPayPage}
 * because a Razorpay order covers a single tenant-side payable — bulk
 * pay needs its own gateway-order creation (one Razorpay charge that,
 * on success, marks ALL the linked collection rows PAID atomically).
 *
 * <p>Click flow:
 * <ol>
 *   <li>Tenant lands here with a list of DUE / OVERDUE charges for
 *       the month and a total at the top.</li>
 *   <li>"Pay all via Razorpay" calls the property-service bridge
 *       endpoint, which mints a Payment row (status=PENDING) in
 *       payment-service for the sum and stamps its id on every
 *       collection row.</li>
 *   <li>FE forwards to {@code /app/payments/{paymentId}/pay} —
 *       same UPI / Card / Net-Banking picker as rent.</li>
 *   <li>When Razorpay confirms (webhook), payment-service publishes
 *       PaymentCompletedEvent; property-service's listener flips all
 *       linked maintenance_collection rows PAID atomically.</li>
 * </ol>
 *
 * <p>The individual per-row Pay buttons still link to the legacy QR
 * page for tenants who want to pay just one charge at a time.
 */
export function SocietyPayAllPage() {
  const { buildingId, month } = useParams<{
    buildingId: string;
    month: string;
  }>();
  const navigate = useNavigate();
  const { toast } = useToast();

  // Stable idempotency key for this page-render. Two clicks of the
  // Pay-all button send the SAME key, so payment-service collides
  // on the (idempotency-key, tenant) tuple and returns the existing
  // paymentId instead of minting a second Razorpay order. Generated
  // once via useRef so re-renders during the mutation don't churn it.
  const idempotencyKeyRef = useRef<string>(
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  );

  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });

  const billsQ = useQuery({
    queryKey: ["tenant-society-bills", buildingId, month],
    queryFn: () => societyApi.myBills(buildingId!, month!),
    enabled: !!buildingId && !!month,
    staleTime: 15_000,
  });

  // Only charges in DUE / OVERDUE need paying. PAID and WAIVED rows
  // are filtered out — they shouldn't show up on a "Pay all" screen
  // and counting them in the total would mislead the tenant.
  const dueRows: FlatMaintenanceRow[] = useMemo(
    () =>
      (billsQ.data ?? []).filter(
        (r) => r.status === "DUE" || r.status === "OVERDUE",
      ),
    [billsQ.data],
  );

  const total = useMemo(
    () => dueRows.reduce((s, r) => s + r.monthAmount, 0),
    [dueRows],
  );

  // Razorpay test-mode bank simulators reject single transactions over
  // a per-bank cap with BAD_REQUEST_ERROR. Detect ahead of time and
  // route the user to per-charge Pay buttons instead — those work
  // because each individual amount is under the cap.
  const exceedsBulkCap = total > BULK_PAY_MAX_INR;

  // Bridge to the Razorpay flow. Calls the backend to mint a Payment
  // row covering the DUE rows, then navigates to the existing rent
  // pay page — same UPI / Card / Net-Banking picker user already
  // knows from rent. On success, the PaymentCompleted Kafka event
  // flips every linked collection row PAID atomically (handled in
  // property-service's SocietyChargePaymentListener).
  const payAllMut = useMutation({
    mutationFn: () =>
      societyApi.initiateSocietyChargePayment(
        buildingId!,
        dueRows
          .map((r) => r.collectionId)
          .filter((id): id is string => !!id),
        idempotencyKeyRef.current,
      ),
    onSuccess: (res) => {
      // Use replace:true so the back button on the pay page doesn't
      // bounce the user back to this loading state — it'd re-create
      // another Razorpay order. Forward to the existing rent pay UI.
      navigate(`/app/payments/${res.paymentId}/pay`, { replace: true });
    },
    onError: (err) =>
      toast({
        variant: "destructive",
        title: "Couldn't start the bulk payment",
        description: extractErrorMessage(err),
      }),
  });

  if (!buildingId || !month) {
    return (
      <EmptyState
        variant="info"
        icon={Receipt}
        title="Invalid link"
        description="No building or month specified. Go back to the society page and pick Pay all from the My charges footer."
      />
    );
  }

  if (configQ.isLoading || billsQ.isLoading) {
    return (
      <div className="max-w-3xl space-y-4">
        <Skeleton className="h-12 rounded-lg" />
        <Skeleton className="h-72 rounded-2xl" />
      </div>
    );
  }

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title={`Pay all · ${month}`}
        description={`Settle every outstanding charge against your flat in ${month}.`}
        actions={
          <Button asChild variant="ghost" size="sm">
            <Link to="/app/society">
              <ArrowLeft className="size-4" /> Back to society
            </Link>
          </Button>
        }
      />

      {!dueRows.length ? (
        <EmptyState
          variant="info"
          icon={Receipt}
          title="Nothing to pay this month"
          description="You're fully paid up for this month. The Pay all button only shows when there are DUE / OVERDUE charges to settle."
          action={
            <Button asChild variant="outline">
              <Link to="/app/society">Back to society</Link>
            </Button>
          }
        />
      ) : (
        <>
          {/* Over-the-cap warning. Only surfaces on the Razorpay path
            * — the cap comes from Razorpay's test-bank simulator, not
            * the app itself. When Razorpay is disabled, this warning
            * disappears because there's no cap on direct UPI. */}
          {!isRazorpayPaymentsDisabled() && exceedsBulkCap && (
            <Card className="mb-4 border-warning/40 bg-warning/5">
              <CardContent className="p-4 flex items-start gap-3">
                <AlertTriangle className="size-5 text-warning shrink-0 mt-0.5" />
                <div className="text-sm">
                  <p className="font-semibold">
                    Total exceeds Razorpay test-mode limit (
                    {formatINR(BULK_PAY_MAX_INR)})
                  </p>
                  <p className="text-muted-foreground mt-0.5">
                    Razorpay's test bank simulators reject single
                    transactions over a per-bank cap. Please use the
                    individual <strong>Pay</strong> buttons on each row
                    below — those work for any amount. The cap goes
                    away once we switch to Razorpay live mode (post-KYC).
                  </p>
                </div>
              </CardContent>
            </Card>
          )}
          {isRazorpayPaymentsDisabled() && (
            <Card className="mb-4 border-primary/30 bg-primary/5">
              <CardContent className="p-4 flex items-start gap-3">
                <Smartphone className="size-5 text-primary shrink-0 mt-0.5" />
                <div className="text-sm">
                  <p className="font-semibold">
                    Bulk pay isn't available on direct UPI
                  </p>
                  <p className="text-muted-foreground mt-0.5">
                    A single UPI QR can only carry one charge at a time,
                    and the maintainer needs to mark each charge PAID
                    individually after seeing the deposit. Use the{" "}
                    <strong>Pay</strong> button on each row below — each
                    opens a QR pointing at the society's UPI ID.
                  </p>
                </div>
              </CardContent>
            </Card>
          )}
          {/* Totals card. Mirrors the "Order summary" style from the
            * rent-pay page so the tenant sees the same visual language
            * across both flows. */}
          <Card className="mb-4">
            <CardContent className="p-5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs uppercase tracking-wider text-muted-foreground">
                    Total outstanding
                  </p>
                  <p className="font-display font-bold text-3xl mt-1">
                    {formatINR(total)}
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {dueRows.length} charge{dueRows.length === 1 ? "" : "s"}{" "}
                    · {month}
                  </p>
                </div>
                {/* Bulk-pay launcher. Disabled state is gated on the
                  * mutation phase + having at least one DUE row + a
                  * known buildingId. On click → backend mints a
                  * Payment + we forward to the existing
                  * /app/payments/{id}/pay page (UPI / Card / Net
                  * Banking method picker, same as rent). */}
                {/* Bulk-pay button stays enabled even when over the
                  * test-mode cap — the user might be on live mode now,
                  * or just willing to try. The warning banner above
                  * tells them what's likely to happen; the error
                  * surface on payment-return shows Razorpay's actual
                  * message if it does fail. Disabling would block
                  * legitimate live-mode use cases. */}
                {!isRazorpayPaymentsDisabled() && (
                  <Button
                    variant="gradient"
                    size="lg"
                    onClick={() => payAllMut.mutate()}
                    disabled={payAllMut.isPending || !dueRows.length}
                  >
                    {payAllMut.isPending ? (
                      <>
                        <Loader2 className="size-4 animate-spin" /> Starting…
                      </>
                    ) : (
                      `Pay all via Razorpay · ${formatINR(total)}`
                    )}
                  </Button>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Per-charge list — same row shape as the My charges table
            * on the society page but laid out as cards because this
            * page is single-purpose (pay these things). Each card
            * routes to the existing /app/society/pay/:b/:c page. */}
          <div className="space-y-2">
            {dueRows.map((row) => {
              const label = row.category
                ? CATEGORY_LABELS[row.category]
                : "Other";
              return (
                <Card
                  key={row.collectionId ?? `${row.category}-${row.flatId}`}
                  className="hover:border-primary/40 transition-colors"
                >
                  <CardContent className="p-4 flex items-center gap-3">
                    <Badge variant="secondary" className="text-[10px] shrink-0">
                      {label}
                    </Badge>
                    <div className="flex-1 min-w-0">
                      {row.notes ? (
                        <p className="text-xs text-muted-foreground italic line-clamp-2">
                          {row.notes}
                        </p>
                      ) : (
                        <p className="text-xs text-muted-foreground">
                          Flat {row.flatNumber} · {row.forMonth}
                        </p>
                      )}
                    </div>
                    <p className="font-display font-semibold whitespace-nowrap">
                      {formatINR(row.monthAmount)}
                    </p>
                    <Button asChild variant="gradient" size="sm">
                      <Link
                        to={`/app/society/pay/${buildingId}/${row.collectionId}`}
                      >
                        Pay
                      </Link>
                    </Button>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
