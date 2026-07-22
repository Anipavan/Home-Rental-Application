import { useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  Copy,
  Loader2,
  Lock,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
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
import { isRazorpayPaymentsDisabled } from "@/lib/feature-flags";
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
  const canPayUpi = !!cfg.upiId;

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
  onCancel,
}: {
  row: FlatMaintenanceRow;
  buildingId: string;
  cfg: SocietyConfig;
  canPayUpiDirect: boolean;
  onCancel: () => void;
}) {
  const navigate = useNavigate();
  const { toast } = useToast();
  const razorpayDisabled = isRazorpayPaymentsDisabled();
  // Direct-UPI is normally collapsed behind a disclosure so the
  // Razorpay button is primary. When Razorpay is disabled we open it
  // by default and hide the Razorpay tile entirely — direct UPI
  // becomes the ONLY path.
  const [upiDirectOpen, setUpiDirectOpen] = useState(razorpayDisabled);

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

  // Razorpay-off path: the ONLY option is direct UPI. Render the
  // block as a primary card with no disclosure wrapper, and a
  // clear "the maintainer will mark this PAID after they see the
  // deposit" affordance so users don't expect auto-confirmation.
  if (razorpayDisabled) {
    return (
      <Card>
        <CardContent className="p-6 space-y-4">
          <div className="flex items-start gap-4">
            <div className="size-12 rounded-2xl bg-primary/10 grid place-items-center shrink-0">
              <Smartphone className="size-6 text-primary" />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="font-display text-lg font-semibold">
                Pay {cfg.payeeName ?? "the society"} via UPI
              </h3>
              <p className="text-sm text-muted-foreground mt-1">
                Scan the QR from any UPI app — money goes directly to
                the society's account. The maintainer will mark your
                charge PAID once the deposit lands.
              </p>
            </div>
          </div>
          {canPayUpiDirect ? (
            <DirectUpiBlock row={row} cfg={cfg} buildingId={buildingId} />
          ) : (
            <EmptyState
              variant="info"
              icon={Smartphone}
              title="UPI not set up yet"
              description="Your maintainer hasn't added a UPI ID for this society. Ask them to add one from their dashboard, then reload this page."
            />
          )}
          <Button variant="ghost" size="sm" className="w-full" onClick={onCancel}>
            <ArrowLeft className="size-4" /> Back to society
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      {/* Primary action — Razorpay launcher */}
      <Card className="mb-4">
        <CardContent className="p-6">
          <div className="flex items-start gap-4 mb-4">
            <div className="size-12 rounded-2xl bg-primary/10 grid place-items-center shrink-0">
              <ShieldCheck className="size-6 text-primary" />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="font-display text-lg font-semibold">
                Pay via Razorpay
              </h3>
              <p className="text-sm text-muted-foreground mt-1">
                Pick PhonePe, Google Pay, Paytm, Credit / Debit Card, or
                Net Banking on the next screen — same secure flow you
                use for rent.
              </p>
            </div>
          </div>
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
          <p className="text-[11px] text-muted-foreground text-center mt-3 flex items-center justify-center gap-1">
            <ShieldCheck className="size-3" /> Secured by Razorpay · 256-bit TLS
          </p>
          <Button
            variant="ghost"
            size="sm"
            className="w-full mt-1"
            onClick={onCancel}
          >
            Cancel & go back
          </Button>
        </CardContent>
      </Card>

      {/* Secondary — UPI / bank fallback, collapsed by default. Only
        * surfaced when the society has actually configured a UPI ID;
        * without one the QR target would be malformed. */}
      {canPayUpiDirect && (
        <Card>
          <CardContent className="p-0">
            <button
              type="button"
              onClick={() => setUpiDirectOpen((v) => !v)}
              className="w-full flex items-center justify-between p-4 text-left hover:bg-secondary/30 transition-colors"
            >
              <div>
                <h4 className="text-sm font-semibold">
                  Pay directly via UPI (no gateway)
                </h4>
                <p className="text-[11px] text-muted-foreground mt-0.5">
                  Scan the society's UPI QR — useful if you'd rather
                  skip the gateway. Maintainer marks PAID manually.
                </p>
              </div>
              <ChevronDown
                className={`size-4 text-muted-foreground shrink-0 transition-transform ${
                  upiDirectOpen ? "rotate-180" : ""
                }`}
              />
            </button>
            {upiDirectOpen && (
              <div className="border-t border-border/60 p-5 space-y-4">
                <DirectUpiBlock row={row} cfg={cfg} buildingId={buildingId} />
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </>
  );
}

/**
 * Direct-UPI fallback content. Same QR + bank-details + reference fields
 * as the previous standalone {@code UpiPaySection}, lifted out of the
 * primary flow so it lives behind the "Pay directly via UPI" disclosure
 * on the Razorpay launcher card. Tenants who choose this path settle
 * outside the gateway — the maintainer marks PAID manually once they
 * see the deposit in their bank app.
 */
function DirectUpiBlock({
  row,
  cfg,
  buildingId,
}: {
  row: FlatMaintenanceRow;
  cfg: SocietyConfig;
  buildingId: string;
}) {
  const { toast } = useToast();
  const [reported, setReported] = useState(false);
  const reportMut = useMutation({
    mutationFn: () => societyApi.reportBankIssue(buildingId),
    onSuccess: () => {
      setReported(true);
      toast({
        title: "Reported — thanks",
        description:
          "The building maintainer will get a warning to double-check the UPI details.",
      });
    },
    onError: (err) =>
      toast({
        variant: "destructive",
        title: "Couldn't send the report",
        description: extractErrorMessage(err),
      }),
  });
  const txnNote = `${row.category ?? "Maintenance"} ${row.forMonth} Flat ${row.flatNumber}`;
  const upiUri =
    `upi://pay?pa=${encodeURIComponent(cfg.upiId ?? "")}` +
    `&pn=${encodeURIComponent(cfg.payeeName ?? cfg.societyDisplayName ?? "Society")}` +
    `&am=${encodeURIComponent(String(row.monthAmount))}` +
    `&cu=INR` +
    `&tn=${encodeURIComponent(txnNote)}`;

  const copyUpi = () => {
    navigator.clipboard.writeText(cfg.upiId ?? "");
    toast({ title: "UPI ID copied" });
  };

  return (
    <>
      <div className="text-center">
        <p className="text-[11px] uppercase tracking-wider text-muted-foreground mb-3">
          Scan to pay {formatINR(row.monthAmount)}
        </p>
        <div className="inline-block rounded-xl border-2 border-border/60 bg-white p-4">
          <QRCodeSVG value={upiUri} size={180} includeMargin={false} />
        </div>
        <a
          href={upiUri}
          className="block mt-2 text-sm text-primary underline underline-offset-2"
        >
          Or tap here to open your UPI app
        </a>
      </div>

      <div className="space-y-2 text-sm">
        <h5 className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          Bank transfer details
        </h5>
        <Row label="UPI ID" value={cfg.upiId!} mono>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-6 px-2"
            onClick={copyUpi}
          >
            <Copy className="size-3.5" />
          </Button>
        </Row>
        <Row label="Payee" value={cfg.payeeName ?? "—"} />
        {cfg.accountNumber && (
          <Row label="A/c no." value={cfg.accountNumber} mono />
        )}
        {cfg.ifscCode && <Row label="IFSC" value={cfg.ifscCode} mono />}
        <Row
          label="Reference"
          value={txnNote}
          note="Use this in your payment note so the maintainer can match the transfer."
        />
      </div>
      <p className="text-[11px] text-muted-foreground">
        After paying, ping the maintainer with your UPI reference — they
        verify the deposit in their bank app and flip the charge to PAID.
      </p>

      {/* Broken-UPI reporter. Small, muted CTA below the pay details —
        * kept low-emphasis so it doesn't prime tenants to click it
        * before they've tried paying. When clicked, flags the society
        * config so the maintainer gets a warning on their dashboard;
        * auto-clears when they next save fresh bank details. */}
      <div className="pt-2 border-t border-border/40">
        {reported ? (
          <p className="text-[11px] text-success flex items-center gap-1.5">
            <CheckCircle2 className="size-3" /> Reported. The maintainer
            will be notified.
          </p>
        ) : (
          <button
            type="button"
            onClick={() => reportMut.mutate()}
            disabled={reportMut.isPending}
            className="text-[11px] text-muted-foreground hover:text-destructive underline underline-offset-2 inline-flex items-center gap-1"
          >
            <AlertTriangle className="size-3" />
            {reportMut.isPending
              ? "Sending report…"
              : "This UPI ID isn't working"}
          </button>
        )}
      </div>
    </>
  );
}

function Row({
  label,
  value,
  mono,
  note,
  children,
}: {
  label: string;
  value: string;
  mono?: boolean;
  note?: string;
  children?: React.ReactNode;
}) {
  return (
    <div>
      <div className="flex items-center gap-2">
        <span className="text-[10px] uppercase tracking-wider text-muted-foreground w-20 shrink-0">
          {label}
        </span>
        <span
          className={`${mono ? "font-mono text-xs" : "text-sm"} flex-1 truncate`}
          title={value}
        >
          {value}
        </span>
        {children}
      </div>
      {note && (
        <p className="text-[10px] text-muted-foreground mt-0.5 ml-22">{note}</p>
      )}
    </div>
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
