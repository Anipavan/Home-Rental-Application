import { useMemo, useRef } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Loader2,
  Lock,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import { isCashfreeSplitCheckoutEnabled } from "@/lib/feature-flags";
import type {
  FlatChargeCategory,
  FlatMaintenanceRow,
  SocietyConfig,
} from "@/types/api";

const CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

/**
 * Dedicated payment page for one society charge. Reached from the
 * tenant's society page ("Pay" button on a DUE charge row). URL
 * shape mirrors the rent-pay route (/app/payments/:id/pay) so the
 * navigation pattern is familiar:
 * /app/society/pay/:buildingId/:collectionId.
 *
 * <p>Default action: a single "Pay via Razorpay" button that bridges
 * to the existing rent-pay UI — same UPI / Card / Net Banking method
 * picker. Under the hood it calls
 * {@code societyApi.initiateSocietyChargePayment([collectionId])}
 * (a one-element array — the bulk-pay endpoint is reused with a list
 * of one), which mints a Payment row and forwards the user to
 * /app/payments/{paymentId}/pay. When Razorpay confirms, a Kafka
 * listener flips this collection row PAID.
 *
 * <p>For tenants who'd rather skip the gateway (saves the convenience
 * fee on large amounts, or works when the gateway is down), a
 * collapsed "Pay directly via UPI" disclosure on the same card
 * reveals the legacy QR + bank-transfer details. That path requires
 * the maintainer to mark PAID manually after verifying the deposit.
 */
export function SocietyPayPage() {
  const { buildingId, collectionId } = useParams<{
    buildingId: string;
    collectionId: string;
  }>();
  const navigate = useNavigate();

  // The society config (for the UPI / bank fields).
  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });

  // The tenant's bills for the current month — find the row matching
  // collectionId. We accept a small inefficiency here (we re-fetch the
  // whole month list rather than expose a single-row GET) because the
  // list is small and React Query has the response cached from the
  // /app/society page that linked here.
  const month = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  })();

  const billsQ = useQuery({
    queryKey: ["tenant-society-bills", buildingId, month],
    queryFn: () => societyApi.myBills(buildingId!, month),
    enabled: !!buildingId,
    staleTime: 15_000,
  });

  // Walk a few months back if the row isn't in the current month —
  // covers the case where the tenant clicks Pay on a row from a
  // past month they were viewing.
  const monthsQ = useQuery({
    queryKey: ["tenant-society-bills-recent", buildingId],
    queryFn: async () => {
      const months = lastNMonths(6);
      const all = await Promise.all(
        months.map((m) => societyApi.myBills(buildingId!, m)),
      );
      return all.flat();
    },
    enabled: !!buildingId,
    staleTime: 30_000,
  });

  const row = useMemo(() => {
    const candidates = [
      ...(billsQ.data ?? []),
      ...(monthsQ.data ?? []),
    ];
    return candidates.find((r) => r.collectionId === collectionId) ?? null;
  }, [billsQ.data, monthsQ.data, collectionId]);

  if (!buildingId || !collectionId) {
    return (
      <EmptyState
        variant="info"
        icon={Smartphone}
        title="Invalid link"
        description="No charge selected. Go back to the society page and pick a Pay button."
      />
    );
  }

  if (configQ.isLoading || billsQ.isLoading) {
    return (
      <div className="max-w-2xl space-y-4">
        <Skeleton className="h-12 rounded-lg" />
        <Skeleton className="h-72 rounded-2xl" />
      </div>
    );
  }

  if (!configQ.data) {
    return (
      <EmptyState
        variant="info"
        icon={Smartphone}
        title="Society not set up"
        description="The owner hasn't enabled society maintenance for your building."
      />
    );
  }

  if (!row) {
    return (
      <EmptyState
        variant="info"
        icon={Smartphone}
        title="Charge not found"
        description="This payment link may be stale. Refresh your society page and try again."
        action={
          <Button asChild variant="outline">
            <Link to="/app/society">← Back to society</Link>
          </Button>
        }
      />
    );
  }

  const cfg = configQ.data;
  const categoryLabel = row.category ? CATEGORY_LABELS[row.category] : "Other";
  const isPaid = row.status === "PAID";
  // Config's bank_config_flagged_at is set when someone has flagged
  // the society's UPI as broken (tenant self-report or maintainer
  // manual flag). While flagged, hide the pay UI so tenants can't
  // send money into a broken VPA — the maintainer needs to fix the
  // UPI ID + save fresh details (which clears the flag).
  const bankFlagged = Boolean(cfg.bankConfigFlaggedAt);
  const canPayUpi = !!cfg.upiId && !bankFlagged;

  return (
    <div className="animate-fade-in max-w-2xl">
      <PageHeader
        title={`Pay ${formatINR(row.monthAmount)}`}
        description={`${categoryLabel} · Flat ${row.flatNumber} · ${row.forMonth}`}
        actions={
          <Button asChild variant="ghost" size="sm">
            <Link to="/app/society">
              <ArrowLeft className="size-4" /> Back to society
            </Link>
          </Button>
        }
      />

      {/* Summary card */}
      <Card className="mb-4">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <Badge variant="secondary" className="text-[10px] mb-2">
                {categoryLabel}
              </Badge>
              <p className="text-sm text-muted-foreground">
                Flat {row.flatNumber} · {row.forMonth}
              </p>
              {row.notes && (
                <p className="text-xs text-muted-foreground italic mt-2">
                  {row.notes}
                </p>
              )}
            </div>
            <p className="font-display font-bold text-2xl">
              {formatINR(row.monthAmount)}
            </p>
          </div>
        </CardContent>
      </Card>

      {isPaid ? (
        <Card>
          <CardContent className="p-6 text-center">
            <CheckCircle2 className="size-12 text-success mx-auto mb-3" />
            <h3 className="font-display font-semibold text-lg">
              Already paid
            </h3>
            <p className="text-sm text-muted-foreground mt-1">
              {row.paidOn
                ? `Marked paid on ${row.paidOn}.`
                : "The maintainer has marked this charge as paid."}
            </p>
            <Button asChild variant="outline" className="mt-4">
              <Link to="/app/society">Back to society</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <RazorpayLaunchSection
          row={row}
          buildingId={buildingId}
          cfg={cfg}
          canPayUpiDirect={canPayUpi}
          bankFlagged={bankFlagged}
          onCancel={() => navigate("/app/society")}
        />
      )}
    </div>
  );
}

/**
 * Razorpay launcher for ONE society charge. Primary action is the
 * gradient "Pay via Razorpay" button — same backend bridge as the
 * /app/society/pay-all bulk-pay page, just called with a single
 * collectionId. On success the user lands on /app/payments/{id}/pay,
 * the existing rent-pay UI, and picks PhonePe / GPay / Paytm / Card /
 * Net Banking from the familiar method tile grid.
 *
 * <p>Below the primary action, a "Pay directly via UPI" disclosure
 * collapses the legacy QR + bank-transfer flow. Tenants who'd rather
 * pay the society's UPI ID outside the gateway (saves the convenience
 * fee on large amounts, or works when Razorpay/payment-service is
 * having a bad day) still have a path. It's collapsed by default so
 * the gateway flow is the "default behaviour".
 */
function RazorpayLaunchSection({
  row,
  buildingId,
  cfg,
  canPayUpiDirect,
  bankFlagged,
  onCancel,
}: {
  row: FlatMaintenanceRow;
  buildingId: string;
  cfg: SocietyConfig;
  canPayUpiDirect: boolean;
  /** True when someone has flagged the society's UPI as broken.
   *  Suppresses the pay UI so tenants can't send money into a
   *  broken VPA — a warning banner replaces the QR + launchers. */
  bankFlagged: boolean;
  onCancel: () => void;
}) {
  const navigate = useNavigate();
  const { toast } = useToast();
  // Direct-UPI is the only supported society-charge path until Cashfree
  // Easy Split (Phase 5+) lands. When enabled, only society configs
  // with a Cashfree vendor id registered fall through the split path;
  // everyone else keeps using direct-UPI.
  const directUpiOnly = !isCashfreeSplitCheckoutEnabled();

  // Stable idempotency key for this render — two clicks of the Pay
  // button send the SAME key, so payment-service collides on the
  // (idempotency-key, tenant) tuple and returns the existing
  // paymentId instead of minting a second Razorpay order.
  const idempotencyKeyRef = useRef<string>(
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  );

  const payMut = useMutation({
    mutationFn: () =>
      societyApi.initiateSocietyChargePayment(
        buildingId,
        row.collectionId ? [row.collectionId] : [],
        idempotencyKeyRef.current,
      ),
    onSuccess: (res) => {
      // replace:true so the back button doesn't bring the user back
      // here and let them double-launch a second Razorpay order.
      navigate(`/app/payments/${res.paymentId}/pay`, { replace: true });
    },
    onError: (err) =>
      toast({
        variant: "destructive",
        title: "Couldn't start the payment",
        description: extractErrorMessage(err),
      }),
  });

  // Single unified path — regardless of Razorpay on/off, tapping
  // Pay mints a Payment row via the bridge and forwards the tenant
  // to /app/payments/{id}/pay. That destination renders the shared
  // DirectUpiPayCard (with app launchers + auto-confirm dialog on
  // return) when Razorpay is off, or the full Razorpay method
  // picker when it's on. Same tenant reaction either way: one tap,
  // one destination, auto-confirmation.
  const societyLabel = cfg.societyDisplayName ?? "society maintenance";
  const description = directUpiOnly
    ? "Tap Pay to open Google Pay / PhonePe / Paytm with the amount pre-filled. Money goes directly to the society's collection account; once you confirm, we mark the charge PAID and generate a receipt."
    : "Pick PhonePe, Google Pay, Paytm, Credit / Debit Card, or Net Banking on the next screen.";

  return (
    <Card>
      <CardContent className="p-6 space-y-4">
        <div className="flex items-start gap-4">
          <div className="size-12 rounded-2xl bg-primary/10 grid place-items-center shrink-0">
            {directUpiOnly ? (
              <Smartphone className="size-6 text-primary" />
            ) : (
              <ShieldCheck className="size-6 text-primary" />
            )}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="font-display text-lg font-semibold">
              Pay {societyLabel}
            </h3>
            <p className="text-sm text-muted-foreground mt-1">
              {description}
            </p>
          </div>
        </div>

        {bankFlagged ? (
          <EmptyState
            variant="info"
            icon={AlertTriangle}
            title="This society's UPI is being verified"
            description="A previous tenant reported the UPI ID as not working. Payments are paused until the maintainer updates the collection account. Please reach out to your maintainer directly and pay later once they've fixed the details."
          />
        ) : !canPayUpiDirect ? (
          <EmptyState
            variant="info"
            icon={Smartphone}
            title="UPI not set up yet"
            description="Your maintainer hasn't added a UPI ID for this society. Ask them to add one from their dashboard, then reload this page."
          />
        ) : (
          <>
            <Button
              variant="gradient"
              size="lg"
              className="w-full"
              onClick={() => payMut.mutate()}
              disabled={payMut.isPending || !row.collectionId}
            >
              {payMut.isPending ? (
                <>
                  <Loader2 className="size-4 animate-spin" /> Starting…
                </>
              ) : (
                <>
                  <Lock className="size-4" /> Pay {formatINR(row.monthAmount)}
                </>
              )}
            </Button>
            {!directUpiOnly && (
              <p className="text-[11px] text-muted-foreground text-center flex items-center justify-center gap-1">
                <ShieldCheck className="size-3" /> Secured by Razorpay ·
                256-bit TLS
              </p>
            )}
          </>
        )}

        <Button
          variant="ghost"
          size="sm"
          className="w-full"
          onClick={onCancel}
        >
          <ArrowLeft className="size-4" /> Back to society
        </Button>
      </CardContent>
    </Card>
  );
}


function lastNMonths(n: number): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 0; i < n; i++) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}
