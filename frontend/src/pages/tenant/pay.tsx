import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  Copy,
  Loader2,
  ShieldCheck,
  Smartphone,
  Wallet,
  Lock,
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { paymentsApi } from "@/lib/api/payments";
import { paymentGateway } from "@/lib/api/payment-gateway";
import { bankAccountsApi } from "@/lib/api/bank-accounts";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { isRazorpayPaymentsDisabled } from "@/lib/feature-flags";
import { EmptyState } from "@/components/ui/empty-state";
import {
  UpiIdField,
  isVpaUsable,
  type VpaState,
} from "@/components/payment/upi-id-field";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatINR, formatDate, cn } from "@/lib/utils";
import {
  PhonePeIcon,
  GPayIcon,
  PaytmIcon,
  UPIIcon,
  CardIcon,
  NetBankingIcon,
} from "@/components/payment/method-icons";
import type {
  InitiatePaymentResponse,
  PaymentMethod,
  UpiApp,
} from "@/types/api";

type Step = "select" | "checkout" | "success";

interface MethodOption {
  /** UI key (also picker selection token). */
  key: string;
  label: string;
  hint?: string;
  Icon: React.ComponentType<{ className?: string }>;
  group: "upi" | "card";
  recommended?: boolean;
  paymentMethod: PaymentMethod;
  upiApp?: UpiApp;
}

const methods: MethodOption[] = [
  { key: "upi-phonepe", label: "PhonePe",      hint: "UPI · Instant",      Icon: PhonePeIcon, group: "upi", recommended: true, paymentMethod: "UPI", upiApp: "PHONEPE" },
  { key: "upi-gpay",    label: "Google Pay",   hint: "UPI · Instant",      Icon: GPayIcon,    group: "upi",                  paymentMethod: "UPI", upiApp: "GPAY" },
  { key: "upi-paytm",   label: "Paytm UPI",    hint: "UPI · Wallet",       Icon: PaytmIcon,   group: "upi",                  paymentMethod: "UPI", upiApp: "PAYTM" },
  { key: "upi-other",   label: "Other UPI",    hint: "Enter UPI ID",       Icon: UPIIcon,     group: "upi",                  paymentMethod: "UPI", upiApp: "OTHER" },
  { key: "card",        label: "Credit / Debit Card", hint: "Visa · Mastercard · RuPay", Icon: CardIcon, group: "card",  paymentMethod: "CARD" },
  { key: "netbanking",  label: "Net Banking",  hint: "All major banks",    Icon: NetBankingIcon, group: "card",                  paymentMethod: "NET_BANKING" },
];

export function PayPage() {
  const { id } = useParams();
  const paymentId = id ?? "";
  const qc = useQueryClient();

  const [step, setStep] = useState<Step>("select");
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [upiVpa, setUpiVpa] = useState("");
  // Live validation state of the upiVpa above. Empty until the user
  // picks "Other UPI"; once they do, we gate the Pay button on this
  // being {@code valid} so we never send a UPI collect request to a
  // garbage / mistyped VPA.
  const [vpaState, setVpaState] = useState<VpaState>({ kind: "empty" });

  const selected = useMemo(
    () => methods.find((m) => m.key === selectedKey) ?? null,
    [selectedKey],
  );

  const paymentQ = useQuery({
    queryKey: ["payment", paymentId],
    queryFn: () => paymentsApi.get(paymentId),
    enabled: !!paymentId,
  });

  // Resolve flat UUID -> "A-302". Hook tolerates an empty list and only
  // fires once paymentQ.data?.flatId is known.
  const flatLookup = useFlatLookup(
    paymentQ.data?.flatId ? [paymentQ.data.flatId] : [],
  );

  function handlePay() {
    if (!selected) return;
    setStep("checkout");
  }

  if (paymentQ.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 rounded-2xl" />
      </div>
    );
  }
  if (paymentQ.isError || !paymentQ.data) {
    return (
      <div className="text-center py-16">
        <p className="text-muted-foreground">Payment not found.</p>
        <Button asChild variant="link" className="mt-3">
          <Link to="/app/payments">← Back to payments</Link>
        </Button>
      </div>
    );
  }

  const p = paymentQ.data;
  const total = p.totalAmount ?? p.amount;

  if (step === "success" || p.status === "PAID") {
    return (
      <SuccessView
        paymentId={paymentId}
        amount={total}
        dueDate={p.dueDate}
        transactionId={p.transactionId}
        sourceType={p.sourceType}
      />
    );
  }

  // Razorpay-off path: skip the whole method-picker + gateway flow
  // and render the direct-UPI QR (owner's UPI from their bank details)
  // as the only option. Owner marks PAID via their /owner/payments
  // dashboard once they see the deposit in their bank app.
  if (isRazorpayPaymentsDisabled()) {
    return (
      <DirectUpiRentView
        payment={p}
        flatLabel={flatLookup.nameOf(p.flatId)}
      />
    );
  }

  return (
    <div className="animate-fade-in max-w-4xl mx-auto">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/app/payments">
          <ArrowLeft /> Back to payments
        </Link>
      </Button>
      <PageHeader title="Pay rent" description="Choose how you'd like to pay." />

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div>
          {step === "select" && (
            <SelectMethodView
              selectedKey={selectedKey}
              onSelect={setSelectedKey}
              upiVpa={upiVpa}
              onUpiVpaChange={setUpiVpa}
              onVpaStateChange={setVpaState}
            />
          )}
          {step === "checkout" && selected && (
            <CheckoutView
              method={selected}
              upiVpa={upiVpa}
              paymentId={paymentId}
              amount={total}
              onSuccess={() => {
                qc.invalidateQueries({ queryKey: ["payment", paymentId] });
                qc.invalidateQueries({ queryKey: ["my-payments"] });
                setStep("success");
              }}
              onCancel={() => {
                setStep("select");
              }}
            />
          )}
        </div>
        <SummaryCard
          amount={total}
          baseAmount={p.amount}
          lateFee={p.lateFee}
          dueDate={p.dueDate}
          flatLabel={flatLookup.nameOf(p.flatId)}
          onPay={handlePay}
          payDisabled={
            !selected ||
            // For the "Other UPI" path, gate Pay on a fully verified VPA.
            // {@code isVpaUsable(state, false)} returns true ONLY when the
            // backend has confirmed the VPA exists on the UPI directory —
            // not just that the format looks plausible. Prevents firing
            // collect requests at typo'd VPAs.
            (selected.upiApp === "OTHER" && !isVpaUsable(vpaState, false))
          }
          payLoading={false}
          method={selected}
          step={step}
        />
      </div>
    </div>
  );
}

function SelectMethodView({
  selectedKey,
  onSelect,
  upiVpa,
  onUpiVpaChange,
  onVpaStateChange,
}: {
  selectedKey: string | null;
  onSelect: (key: string) => void;
  upiVpa: string;
  onUpiVpaChange: (v: string) => void;
  /** Bubbles the UpiIdField's live validation state up to the parent
   *  so the Pay button can be disabled until the VPA is verified. */
  onVpaStateChange: (s: VpaState) => void;
}) {
  const showVpa =
    methods.find((m) => m.key === selectedKey)?.upiApp === "OTHER";

  return (
    <div className="space-y-6">
      <Section title="UPI · Recommended" icon={Smartphone}>
        <div className="grid gap-2.5 sm:grid-cols-2">
          {methods.filter((m) => m.group === "upi").map((m) => (
            <MethodTile
              key={m.key}
              method={m}
              selected={selectedKey === m.key}
              onClick={() => onSelect(m.key)}
            />
          ))}
        </div>
        {showVpa && (
          <div className="mt-3 p-3.5 rounded-xl border bg-card">
            {/* Live-validated UPI input. Same component the owner's
                bank-details form uses — debounced 600ms call to
                /payments/vpa/validate, renders the bank-registered
                holder name on success. Pay button stays disabled
                until the VPA is verified. */}
            <UpiIdField
              id="tenant-upi-vpa"
              label="UPI ID (VPA)"
              value={upiVpa}
              onChange={onUpiVpaChange}
              onStateChange={onVpaStateChange}
              helper="We'll send a collect request to this UPI app."
            />
          </div>
        )}
      </Section>
      <Section title="Cards & Net Banking" icon={Wallet}>
        <div className="grid gap-2.5 sm:grid-cols-2">
          {methods.filter((m) => m.group === "card").map((m) => (
            <MethodTile
              key={m.key}
              method={m}
              selected={selectedKey === m.key}
              onClick={() => onSelect(m.key)}
            />
          ))}
        </div>
      </Section>
      <p className="text-xs text-muted-foreground flex items-center gap-1.5">
        <Lock className="size-3" /> Secured by 256-bit TLS · PCI-DSS compliant
        gateways
      </p>
    </div>
  );
}

function Section({
  title,
  icon: Icon,
  children,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h3 className="text-xs uppercase tracking-wider font-semibold text-muted-foreground flex items-center gap-1.5 mb-2.5">
        <Icon className="size-3.5" /> {title}
      </h3>
      {children}
    </div>
  );
}

function MethodTile({
  method,
  selected,
  onClick,
}: {
  method: MethodOption;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-3 p-3.5 rounded-xl border-2 text-left transition-all",
        selected
          ? "border-primary bg-primary/5 ring-4 ring-primary/10"
          : "border-border hover:border-primary/40 bg-card hover:bg-secondary/30",
      )}
    >
      <method.Icon className="size-10 shrink-0" />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="font-medium text-sm">{method.label}</p>
          {method.recommended && (
            <Badge className="text-[10px] px-1.5 py-0">Best</Badge>
          )}
        </div>
        {method.hint && (
          <p className="text-xs text-muted-foreground truncate">{method.hint}</p>
        )}
      </div>
      <div
        className={cn(
          "size-5 rounded-full border-2 grid place-items-center transition-colors",
          selected ? "border-primary bg-primary" : "border-muted-foreground/30",
        )}
      >
        {selected && <span className="size-2 rounded-full bg-white" />}
      </div>
    </button>
  );
}

function CheckoutView(props: {
  method: MethodOption;
  upiVpa: string;
  paymentId: string;
  amount: number;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  if (props.method.paymentMethod === "UPI") return <UpiCheckout {...props} />;
  return <RedirectCheckout {...props} />;
}

function UpiCheckout({
  method,
  upiVpa,
  paymentId,
  onSuccess,
  onCancel,
}: {
  method: MethodOption;
  upiVpa: string;
  paymentId: string;
  amount: number;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [response, setResponse] = useState<InitiatePaymentResponse | null>(null);
  const [phase, setPhase] = useState<"loading" | "waiting" | "verifying">(
    "loading",
  );
  const [secondsLeft, setSecondsLeft] = useState(300);

  useEffect(() => {
    let active = true;
    paymentGateway
      .initiate({
        paymentId,
        paymentMethod: "UPI",
        upiApp: method.upiApp,
        upiVpa: method.upiApp === "OTHER" ? upiVpa : undefined,
        returnUrl: `${window.location.origin}/app/payments/${paymentId}/return`,
      })
      .then((res) => {
        if (!active) return;
        setResponse(res);
        setPhase("waiting");
      })
      .catch((e) => {
        if (!active) return;
        toast({
          variant: "destructive",
          title: "Couldn't start payment",
          description: extractErrorMessage(e),
        });
        onCancel();
      });
    return () => {
      active = false;
    };
  }, [paymentId, method, upiVpa, onCancel]);

  // Countdown
  useEffect(() => {
    if (phase !== "waiting") return;
    const t = setInterval(() => setSecondsLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(t);
  }, [phase]);

  // Poll the payment row — UPI is fully async; the gateway webhook flips status to PAID.
  useEffect(() => {
    if (!response || phase !== "waiting") return;
    let attempt = 0;
    let cancelled = false;
    const poll = async () => {
      attempt += 1;
      try {
        const p = await paymentsApi.get(paymentId);
        if (cancelled) return;
        if (p.status === "PAID") {
          setPhase("verifying");
          setTimeout(() => {
            if (!cancelled) onSuccess();
          }, 600);
          return;
        }
        if (p.status === "FAILED" || p.status === "CANCELLED") {
          toast({
            variant: "destructive",
            title: "Payment didn't go through",
            description: p.failureReason ?? "Try again or pick a different method.",
          });
          onCancel();
          return;
        }
      } catch {
        // transient — keep polling
      }
      if (attempt < 90) setTimeout(poll, 2500);
    };
    const start = setTimeout(poll, 2500);
    return () => {
      cancelled = true;
      clearTimeout(start);
    };
  }, [response, phase, paymentId, onSuccess, onCancel]);

  const intentUrl = response?.upiIntentUrl;
  // On mobile we attempt to open the deep link (e.g. phonepe://, tez://,
  // paytmmp://) automatically as soon as we have it. Desktop browsers
  // ignore custom schemes — they just see the QR.
  const isMobile =
    typeof window !== "undefined" &&
    /android|iphone|ipad|ipod|mobile/i.test(window.navigator.userAgent);

  useEffect(() => {
    if (!intentUrl || phase !== "waiting" || !isMobile) return;
    // Defer slightly so the page renders before the OS app switch.
    const t = setTimeout(() => {
      window.location.href = intentUrl;
    }, 250);
    return () => clearTimeout(t);
  }, [intentUrl, phase, isMobile]);

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-5">
          <method.Icon className="size-12" />
          <div>
            <h3 className="font-display text-xl font-semibold">
              Open {method.label}
            </h3>
            <p className="text-sm text-muted-foreground">
              Approve the payment on your phone
            </p>
          </div>
        </div>

        {phase === "loading" && (
          <div className="py-12 text-center">
            <Loader2 className="animate-spin size-8 mx-auto text-primary" />
            <p className="text-sm text-muted-foreground mt-3">
              Creating secure UPI request…
            </p>
          </div>
        )}

        {phase !== "loading" && response && (
          <div className="grid gap-6 sm:grid-cols-[auto_1fr] items-center">
            {intentUrl ? (
              <QRBlock value={intentUrl} />
            ) : (
              <div className="rounded-2xl bg-secondary p-8 text-xs text-muted-foreground text-center">
                Waiting for collect request…
              </div>
            )}
            <div>
              <ol className="space-y-3 text-sm">
                <Step n={1} text={`Open ${method.label} on your phone`} />
                <Step n={2} text="Scan the QR or tap the button below" />
                <Step n={3} text="Enter your UPI PIN to confirm" />
              </ol>
              {intentUrl && (
                <Button
                  asChild
                  variant="gradient"
                  size="lg"
                  className="w-full mt-4"
                >
                  <a href={intentUrl} target="_blank" rel="noreferrer">
                    <Smartphone /> Open {method.label} app
                  </a>
                </Button>
              )}
              {/* Fallback to universal upi:// in case the app-specific scheme
                  isn't installed on the user's phone. */}
              {intentUrl && intentUrl.startsWith("upi://") === false && (
                <a
                  href={`upi://pay?${intentUrl.split("?")[1] ?? ""}`}
                  className="mt-2 block text-xs text-muted-foreground hover:text-foreground text-center"
                >
                  Use a different UPI app →
                </a>
              )}
              <div className="mt-6 p-3 rounded-lg bg-secondary/60 text-xs text-muted-foreground">
                {phase === "waiting" ? (
                  <span className="inline-flex items-center gap-1.5">
                    <span className="size-1.5 rounded-full bg-amber-500 animate-pulse" />
                    Waiting for confirmation… {secondsLeft}s
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5">
                    <Loader2 className="size-3 animate-spin" />
                    Confirming with your bank…
                  </span>
                )}
              </div>
            </div>
          </div>
        )}

        <Separator className="my-6" />
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground flex items-center gap-1.5">
            <ShieldCheck className="size-3.5" />
            Powered by {response?.gatewayName ?? "your bank"}
          </p>
          <Button variant="ghost" size="sm" onClick={onCancel}>
            Cancel & change method
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function RedirectCheckout({
  method,
  paymentId,
  amount,
  onCancel,
}: {
  method: MethodOption;
  paymentId: string;
  amount: number;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const [phase, setPhase] = useState<"idle" | "starting">("idle");

  async function start() {
    // Audit H29: re-entrancy guard in addition to the disabled button
    // — between a fast double-click and React's commit phase there's
    // a microsecond window where the click handler can fire twice. A
    // synchronous bail on `phase !== "idle"` makes that race
    // impossible, regardless of any rendering lag.
    if (phase !== "idle") return;
    setPhase("starting");
    try {
      const res = await paymentGateway.initiate({
        paymentId,
        paymentMethod: method.paymentMethod,
        returnUrl: `${window.location.origin}/app/payments/${paymentId}/return`,
      });
      if (res.redirectUrl) {
        window.location.href = res.redirectUrl;
        return;
      }
      toast({
        variant: "destructive",
        title: "Gateway didn't return a checkout URL",
        description: "Try a different method.",
      });
      setPhase("idle");
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Couldn't start checkout",
        description: extractErrorMessage(e),
      });
      setPhase("idle");
    }
  }

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-5">
          <method.Icon className="size-12" />
          <div>
            <h3 className="font-display text-xl font-semibold">
              {method.label}
            </h3>
            <p className="text-sm text-muted-foreground">
              Pay {formatINR(amount)} on the gateway's secure page
            </p>
          </div>
        </div>

        {phase === "idle" && (
          <div className="text-center py-8 space-y-4">
            <p className="text-sm text-muted-foreground max-w-sm mx-auto">
              You'll be redirected to a secure checkout page. After paying, we'll
              bring you back here automatically.
            </p>
            {/* Audit H29: disable on click. Without this a fast
                double-click fires two paymentGateway.initiate() calls,
                each generating a separate gateway order — a real risk
                of the user being double-charged. The state transition
                + disabled prop together close the gap. */}
            <Button
              variant="gradient"
              size="lg"
              onClick={start}
              disabled={(phase as string) !== "idle"}
            >
              <Lock /> Continue to checkout
            </Button>
          </div>
        )}

        {phase === "starting" && (
          <div className="py-12 text-center">
            <Loader2 className="animate-spin size-8 mx-auto text-primary" />
            <p className="text-sm text-muted-foreground mt-3">
              Redirecting to checkout…
            </p>
          </div>
        )}

        <Separator className="my-6" />
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground flex items-center gap-1.5">
            <Lock className="size-3.5" /> 256-bit secure · PCI-DSS
          </p>
          <Button variant="ghost" size="sm" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function QRBlock({ value }: { value: string }) {
  const url = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&margin=8&data=${encodeURIComponent(value)}`;
  return (
    <div className="rounded-2xl bg-white p-3 border border-border shadow-soft mx-auto sm:mx-0">
      <img src={url} alt="UPI QR" width={200} height={200} className="rounded-md" />
      <p className="text-[10px] text-center mt-2 text-muted-foreground">
        Scan with any UPI app
      </p>
    </div>
  );
}

function Step({ n, text }: { n: number; text: string }) {
  return (
    <li className="flex items-start gap-3">
      <span className="size-6 rounded-full bg-primary/10 text-primary text-xs font-semibold grid place-items-center shrink-0">
        {n}
      </span>
      <span>{text}</span>
    </li>
  );
}

function SummaryCard({
  amount,
  baseAmount,
  lateFee,
  dueDate,
  flatLabel,
  onPay,
  payDisabled,
  payLoading,
  method,
  step,
}: {
  amount: number;
  baseAmount: number;
  lateFee?: number;
  dueDate: string;
  /** Pre-resolved flat number ("A-302") — passed in by the caller. */
  flatLabel: string;
  onPay: () => void;
  payDisabled: boolean;
  payLoading: boolean;
  method: MethodOption | null;
  step: Step;
}) {
  return (
    <Card className="lg:sticky lg:top-20 self-start">
      <CardContent className="p-6">
        <h3 className="font-display font-semibold mb-3">Order summary</h3>
        <div className="space-y-2 text-sm">
          <Row label="Rent" value={formatINR(baseAmount)} />
          {lateFee && lateFee > 0 ? (
            <Row label="Late fee" value={formatINR(lateFee)} accent />
          ) : null}
          <Row label="Convenience fee" value="₹0" />
          <Separator />
          <div className="flex justify-between items-baseline pt-1">
            <span className="font-semibold">Total payable</span>
            <span className="font-display text-xl font-bold">
              {formatINR(amount)}
            </span>
          </div>
        </div>

        <div className="mt-5 p-3 rounded-lg bg-secondary/40 text-xs text-muted-foreground space-y-1">
          <div className="flex justify-between">
            <span>For flat</span>
            <span className="font-medium text-foreground">{flatLabel}</span>
          </div>
          <div className="flex justify-between">
            <span>Due date</span>
            <span className="font-medium text-foreground">
              {formatDate(dueDate)}
            </span>
          </div>
        </div>

        {step === "select" && (
          <Button
            type="button"
            size="lg"
            variant="gradient"
            className="w-full mt-5"
            onClick={onPay}
            disabled={payDisabled}
          >
            {payLoading && <Loader2 className="animate-spin" />}
            {!method ? "Select a method" : `Continue with ${method.label}`}
          </Button>
        )}

        <p className="text-[11px] text-muted-foreground text-center mt-3 flex items-center justify-center gap-1">
          <ShieldCheck className="size-3" /> 100% secure · No card data stored
        </p>
      </CardContent>
    </Card>
  );
}

function Row({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent?: boolean;
}) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className={accent ? "text-destructive font-medium" : "font-medium"}>
        {value}
      </span>
    </div>
  );
}

function SuccessView({
  paymentId,
  amount,
  dueDate,
  transactionId,
  sourceType,
}: {
  paymentId: string;
  amount: number;
  dueDate: string;
  transactionId?: string;
  /** RENT or SOCIETY_CHARGE — picks which tab the "Back to payments"
   *  link lands on so the user sees the category they just paid. */
  sourceType?: string;
}) {
  // Pick the right tab + heading copy off the source. The Razorpay
  // SuccessView is shared between rent and society — the only thing
  // that changes is the labelling and the back-to-list destination.
  const isSocietyCharge = sourceType === "SOCIETY_CHARGE";
  const backHref = isSocietyCharge
    ? "/app/payments?type=maintenance"
    : "/app/payments";
  const successCopy = isSocietyCharge
    ? `We've received ${formatINR(amount)} for your society charges.`
    : `We've received ${formatINR(amount)} for the rent due ${formatDate(dueDate)}.`;
  const [downloading, setDownloading] = useState(false);

  async function handleReceiptDownload() {
    setDownloading(true);
    try {
      const blob = await paymentsApi.receiptPdf(paymentId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `receipt-${paymentId.slice(0, 8)}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Couldn't download receipt",
        description: extractErrorMessage(e),
      });
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="max-w-xl mx-auto py-12 text-center animate-fade-in">
      <div className="size-20 rounded-full bg-success/15 grid place-items-center mx-auto">
        <CheckCircle2 className="size-10 text-success" />
      </div>
      <h2 className="font-display text-3xl font-bold mt-6">Payment successful</h2>
      <p className="text-muted-foreground mt-2">{successCopy}</p>
      <Card className="mt-7 text-left">
        <CardContent className="p-6 space-y-2 text-sm">
          {transactionId && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Transaction ID</span>
              <span className="font-mono text-xs">{transactionId}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">Amount</span>
            <span className="font-semibold">{formatINR(amount)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Status</span>
            <Badge variant="success">Paid</Badge>
          </div>
        </CardContent>
      </Card>
      <div className="mt-7 flex flex-col sm:flex-row gap-2 justify-center">
        <Button asChild variant="gradient" size="lg">
          <Link to={backHref}>Back to payments</Link>
        </Button>
        <Button
          variant="outline"
          size="lg"
          onClick={handleReceiptDownload}
          disabled={downloading}
        >
          {downloading && <Loader2 className="animate-spin" />}
          Download receipt
        </Button>
      </div>
    </div>
  );
}

/* ─── Direct-UPI rent path (RAZORPAY_PAYMENTS_DISABLED=true) ──── */

/**
 * Alternative pay UI that replaces the Razorpay method picker when
 * the payment gateway is turned off. Renders a UPI QR pointing at
 * the OWNER's UPI ID (fetched via /users/bank-accounts/payout/{ownerId})
 * for the exact rent amount, with the owner's bank details below for
 * NEFT / IMPS fallback.
 *
 * <p>No auto-mark-PAID here — the owner sees the deposit in their bank
 * app + clicks "Mark as paid" on their /owner/payments dashboard.
 * The success page still fires once the Payment row flips PAID.
 */
function DirectUpiRentView({
  payment,
  flatLabel,
}: {
  payment: import("@/types/api").PaymentResponse;
  flatLabel: string;
}) {
  const total = payment.totalAmount ?? payment.amount;
  const payoutQ = useQuery({
    queryKey: ["owner-payout", payment.ownerId],
    queryFn: () => bankAccountsApi.getPayoutByUserId(payment.ownerId),
    enabled: !!payment.ownerId,
    staleTime: 60_000,
  });

  return (
    <div className="animate-fade-in max-w-4xl mx-auto">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/app/payments">
          <ArrowLeft /> Back to payments
        </Link>
      </Button>
      <PageHeader
        title={payment.sourceType === "SOCIETY_CHARGE" ? "Pay society charge" : "Pay rent"}
        description={
          payment.sourceType === "SOCIETY_CHARGE"
            ? "Scan the QR from any UPI app — money goes directly to your society's account."
            : "Scan the QR from any UPI app — money goes directly to your owner's account."
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div>
          {payoutQ.isLoading ? (
            <Skeleton className="h-64 rounded-2xl" />
          ) : !payoutQ.data ? (
            <EmptyState
              variant="info"
              icon={Smartphone}
              title="Your owner hasn't added bank details yet"
              description="Ask them to add a UPI ID from their profile page. Once they do, this page will show a QR you can scan."
              action={
                <Button asChild variant="outline">
                  <Link to="/app/payments">Back to payments</Link>
                </Button>
              }
            />
          ) : !payoutQ.data.upiId ? (
            <FallbackBankOnly
              amount={total}
              payout={payoutQ.data}
              note={`Rent ${flatLabel} · Due ${formatDate(payment.dueDate)}`}
            />
          ) : (
            <DirectUpiPayCard
              amount={total}
              upiId={payoutQ.data.upiId}
              payeeName={payoutQ.data.accountHolderName}
              payout={payoutQ.data}
              note={`Rent ${flatLabel} · Due ${formatDate(payment.dueDate)}`}
            />
          )}
        </div>
        <SummaryCard
          amount={total}
          baseAmount={payment.amount}
          lateFee={payment.lateFee}
          dueDate={payment.dueDate}
          flatLabel={flatLabel}
          onPay={() => {}}
          payDisabled={true}
          payLoading={false}
          method={null}
          step="select"
        />
      </div>
    </div>
  );
}

function DirectUpiPayCard({
  amount,
  upiId,
  payeeName,
  payout,
  note,
}: {
  amount: number;
  upiId: string;
  payeeName: string;
  payout: import("@/lib/api/bank-accounts").BankAccountPayoutResponse;
  note: string;
}) {
  const upiUri =
    `upi://pay?pa=${encodeURIComponent(upiId)}` +
    `&pn=${encodeURIComponent(payeeName ?? "Owner")}` +
    `&am=${encodeURIComponent(String(amount))}` +
    `&cu=INR` +
    `&tn=${encodeURIComponent(note)}`;

  const copyUpi = () => {
    navigator.clipboard.writeText(upiId);
    toast({ title: "UPI ID copied" });
  };

  return (
    <Card>
      <CardContent className="p-6 space-y-5">
        <div className="flex items-start gap-4">
          <div className="size-12 rounded-2xl bg-primary/10 grid place-items-center shrink-0">
            <Smartphone className="size-6 text-primary" />
          </div>
          <div>
            <h3 className="font-display text-lg font-semibold">
              Pay {payeeName ?? "your owner"} via UPI
            </h3>
            <p className="text-sm text-muted-foreground mt-1">
              Money goes directly to their bank — no gateway. Your owner
              will mark this rent PAID once they see the deposit.
            </p>
          </div>
        </div>

        <div className="text-center">
          <p className="text-[11px] uppercase tracking-wider text-muted-foreground mb-3">
            Scan to pay {formatINR(amount)}
          </p>
          <div className="inline-block rounded-xl border-2 border-border/60 bg-white p-4">
            <QRCodeSVG value={upiUri} size={200} includeMargin={false} />
          </div>
          <a
            href={upiUri}
            className="block mt-3 text-sm text-primary underline underline-offset-2"
          >
            Or tap here to open your UPI app
          </a>
        </div>

        <div className="rounded-lg border border-border/60 p-3 space-y-1.5 text-sm">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
              UPI ID
            </span>
            <div className="flex items-center gap-1.5">
              <span className="font-mono text-xs">{upiId}</span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-6 px-1.5"
                onClick={copyUpi}
              >
                <Copy className="size-3" />
              </Button>
            </div>
          </div>
          {payout.accountNumberMasked && (
            <div className="flex items-center justify-between gap-2 pt-1.5 border-t border-border/40">
              <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
                Bank
              </span>
              <span className="text-xs">
                {payout.bankName} · {payout.accountNumberMasked}
              </span>
            </div>
          )}
          {payout.ifscCode && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-[11px] uppercase tracking-wider text-muted-foreground">
                IFSC
              </span>
              <span className="font-mono text-xs">{payout.ifscCode}</span>
            </div>
          )}
        </div>

        <p className="text-[11px] text-muted-foreground text-center">
          Prefer bank transfer? Use the account details above for
          NEFT / IMPS from your banking app.
        </p>
      </CardContent>
    </Card>
  );
}

function FallbackBankOnly({
  amount,
  payout,
  note,
}: {
  amount: number;
  payout: import("@/lib/api/bank-accounts").BankAccountPayoutResponse;
  note: string;
}) {
  return (
    <Card>
      <CardContent className="p-6 space-y-4">
        <div className="flex items-start gap-4">
          <div className="size-12 rounded-2xl bg-warning/10 grid place-items-center shrink-0">
            <Wallet className="size-6 text-warning" />
          </div>
          <div>
            <h3 className="font-display text-lg font-semibold">
              Pay via NEFT / IMPS
            </h3>
            <p className="text-sm text-muted-foreground mt-1">
              Your owner hasn't added a UPI ID yet — use the bank
              details below to transfer {formatINR(amount)} directly.
              They'll mark this rent PAID once the deposit lands.
            </p>
          </div>
        </div>

        <div className="rounded-lg border border-border/60 p-3 space-y-2 text-sm">
          <BankRow label="Account name" value={payout.accountHolderName} />
          <BankRow label="Bank" value={payout.bankName} />
          <BankRow label="Account no." value={payout.accountNumberMasked} mono />
          <BankRow label="IFSC" value={payout.ifscCode} mono />
          <BankRow label="Reference" value={note} />
        </div>
      </CardContent>
    </Card>
  );
}

/** Local row helper used inside {@link FallbackBankOnly}. Kept
 *  separate from the existing {@code Row} above (which is a
 *  differently-styled label/value pair used inside SummaryCard). */
function BankRow({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-2">
      <span className="text-[11px] uppercase tracking-wider text-muted-foreground w-24 shrink-0">
        {label}
      </span>
      <span className={mono ? "font-mono text-xs" : "text-sm"}>{value}</span>
    </div>
  );
}
