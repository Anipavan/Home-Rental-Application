import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  Loader2,
  ShieldCheck,
  Smartphone,
  Wallet,
  Lock,
  Building2,
} from "lucide-react";
import { paymentsApi } from "@/lib/api/payments";
import { paymentGateway } from "@/lib/api/payment-gateway";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  { key: "netbanking",  label: "Net Banking",  hint: "All major banks",    Icon: Building2 as unknown as React.ComponentType<{ className?: string }>, group: "card",                  paymentMethod: "NET_BANKING" },
];

export function PayPage() {
  const { id } = useParams();
  const paymentId = id ?? "";
  const qc = useQueryClient();

  const [step, setStep] = useState<Step>("select");
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [upiVpa, setUpiVpa] = useState("");

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
            !selected || (selected.upiApp === "OTHER" && !upiVpa)
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
}: {
  selectedKey: string | null;
  onSelect: (key: string) => void;
  upiVpa: string;
  onUpiVpaChange: (v: string) => void;
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
            <label className="text-xs font-medium text-muted-foreground">
              UPI ID (VPA)
            </label>
            <Input
              value={upiVpa}
              onChange={(e) => onUpiVpaChange(e.target.value)}
              placeholder="name@bank"
              className="mt-1.5"
              autoComplete="off"
              spellCheck={false}
            />
            <p className="text-xs text-muted-foreground mt-1.5">
              We'll send a collect request to your UPI app.
            </p>
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
                <Step n={2} text="Scan the QR or tap the link below" />
                <Step n={3} text="Enter your UPI PIN to confirm" />
              </ol>
              {intentUrl && (
                <a
                  href={intentUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-4 block text-sm text-primary font-medium hover:underline truncate"
                >
                  Open {method.label} app →
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
            <Button variant="gradient" size="lg" onClick={start}>
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
}: {
  paymentId: string;
  amount: number;
  dueDate: string;
  transactionId?: string;
}) {
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
      <p className="text-muted-foreground mt-2">
        We've received {formatINR(amount)} for the rent due {formatDate(dueDate)}.
      </p>
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
          <Link to="/app/payments">Back to payments</Link>
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
