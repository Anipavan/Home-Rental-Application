import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import {
  AlertTriangle,
  Banknote,
  Building2,
  Check,
  Copy,
  Landmark,
  Loader2,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import { paymentsApi } from "@/lib/api/payments";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { toast } from "@/hooks/use-toast";
import { formatINR } from "@/lib/utils";
import type { PaymentResponse } from "@/types/api";

/**
 * Tenant pays rent DIRECTLY to the owner's UPI / bank — money
 * never sits in the platform. Two payment paths shown side-by-side:
 *
 *   1. UPI QR (preferred when owner has saved a VPA)
 *      • Big SVG QR rendered from the upi://pay?... deep link the
 *        backend assembled.
 *      • Tappable VPA + copy-to-clipboard for users who'd rather
 *        type it into their UPI app.
 *
 *   2. Bank transfer fallback (NEFT / IMPS)
 *      • Masked account + IFSC + branch.
 *      • Copy buttons next to each value.
 *
 * After paying, the tenant taps "I've paid — notify owner" which is
 * mostly a UX courtesy (no real notification fires today; can be
 * wired to a small notify endpoint later). The actual state change
 * (PENDING → PAID) happens when the OWNER comes back and marks the
 * payment as received — see frontend/pages/owner/payments.tsx for
 * that side of the flow.
 *
 * <p>Empty states:
 *   • Owner hasn't saved payment details → friendly banner +
 *     "contact owner" affordance via the existing complaint /
 *     enquiry channel.
 *   • Backend down → spinner → toast → close.
 */
export function UpiPayDialog({
  open,
  onOpenChange,
  payment,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  payment: PaymentResponse;
}) {
  const q = useQuery({
    queryKey: ["payout-details", payment.id],
    queryFn: () => paymentsApi.payoutDetails(payment.id),
    enabled: open,
    // Don't refetch silently while the dialog is open — the
    // owner's payout details don't change inside a few-minute
    // payment session.
    staleTime: 5 * 60 * 1000,
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Smartphone className="size-5 text-primary" /> Pay rent direct to
            owner
          </DialogTitle>
          <DialogDescription>
            Scan the QR with any UPI app (GPay / PhonePe / Paytm / BHIM), or
            transfer via NEFT / IMPS to the bank account shown below. After
            paying, your owner will mark the payment as received on the
            platform.
          </DialogDescription>
        </DialogHeader>

        {q.isLoading && (
          <div className="space-y-3">
            <Skeleton className="h-56 w-56 mx-auto rounded-xl" />
            <Skeleton className="h-5 w-3/4 mx-auto" />
            <Skeleton className="h-5 w-1/2 mx-auto" />
          </div>
        )}

        {q.isError && (
          <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm flex items-start gap-2">
            <AlertTriangle className="size-4 text-destructive mt-0.5 shrink-0" />
            <div>
              <p className="font-medium">Couldn't load payment details</p>
              <p className="text-xs text-muted-foreground mt-1">
                The platform couldn't reach the owner's saved payout details
                right now. Try again in a moment, or contact your owner
                directly.
              </p>
            </div>
          </div>
        )}

        {q.data && q.data.ownerPayoutMissing && (
          <div className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm space-y-2">
            <div className="flex items-start gap-2">
              <AlertTriangle className="size-4 text-warning mt-0.5 shrink-0" />
              <div>
                <p className="font-medium">Owner hasn't set up payment details</p>
                <p className="text-xs text-muted-foreground mt-1">
                  Your owner hasn't added their UPI ID or bank account yet,
                  so we can't show you where to pay. Reach out via the
                  contact button on your flat's page and ask them to add
                  their details under Profile → Bank details.
                </p>
              </div>
            </div>
          </div>
        )}

        {q.data && !q.data.ownerPayoutMissing && (
          <div className="space-y-5">
            {/* Header — amount + payee identity. */}
            <div className="rounded-xl border bg-secondary/30 p-4 text-center space-y-1">
              <p className="text-xs text-muted-foreground">You're paying</p>
              <p className="font-display text-3xl font-bold">
                {formatINR(q.data.amount)}
              </p>
              {q.data.payeeName && (
                <p className="text-sm text-muted-foreground">
                  to <span className="font-medium text-foreground">{q.data.payeeName}</span>
                </p>
              )}
            </div>

            {/* UPI QR — preferred path. */}
            {q.data.upiQrPayload && q.data.upiVpa && (
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <Smartphone className="size-4 text-primary" />
                  <h3 className="font-semibold text-sm">
                    Option 1 — UPI scan
                  </h3>
                  <Badge variant="success" className="ml-auto text-[10px]">
                    Recommended
                  </Badge>
                </div>
                <div className="rounded-xl border bg-white p-5 flex flex-col items-center gap-3">
                  <QRCodeSVG
                    value={q.data.upiQrPayload}
                    size={224}
                    level="M"
                    includeMargin={false}
                  />
                  <CopyableLine
                    label="UPI ID"
                    value={q.data.upiVpa}
                  />
                </div>
                <p className="text-[11px] text-muted-foreground text-center">
                  Open your UPI app, tap Scan & Pay, point at the QR. The amount
                  and note are pre-filled.
                </p>
              </div>
            )}

            {/* Bank transfer fallback. */}
            {q.data.accountNumberMasked && q.data.ifscCode && (
              <>
                {q.data.upiQrPayload && <Separator />}
                <div className="space-y-3">
                  <div className="flex items-center gap-2">
                    <Landmark className="size-4 text-primary" />
                    <h3 className="font-semibold text-sm">
                      Option 2 — Bank transfer (NEFT / IMPS)
                    </h3>
                  </div>
                  <div className="rounded-xl border bg-secondary/30 p-4 space-y-3 text-sm">
                    {q.data.payeeName && (
                      <Field
                        icon={Building2}
                        label="Account holder"
                        value={q.data.payeeName}
                      />
                    )}
                    {q.data.bankName && (
                      <Field
                        icon={Landmark}
                        label="Bank"
                        value={q.data.bankName}
                      />
                    )}
                    <Field
                      icon={Banknote}
                      label="Account number"
                      value={q.data.accountNumberMasked}
                      copyable
                      mono
                    />
                    <Field
                      icon={ShieldCheck}
                      label="IFSC"
                      value={q.data.ifscCode}
                      copyable
                      mono
                    />
                    {q.data.branch && (
                      <Field
                        icon={Landmark}
                        label="Branch"
                        value={q.data.branch}
                      />
                    )}
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    Note: only the last 4 digits of the account number are
                    shown here. For the full number, contact your owner
                    directly — they can share it from their saved
                    bank-account section.
                  </p>
                </div>
              </>
            )}

            <Separator />

            <p className="text-xs text-muted-foreground">
              <ShieldCheck className="size-3.5 inline-block mr-1 text-success" />
              The platform doesn't process this payment. Once you've paid, your
              owner will mark the rent as received and the platform will issue
              a receipt.
            </p>
          </div>
        )}

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* ───────────────────────── internals ───────────────────────── */

function Field({
  icon: Icon,
  label,
  value,
  copyable,
  mono,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  copyable?: boolean;
  mono?: boolean;
}) {
  return (
    <div className="flex items-start gap-3">
      <Icon className="size-4 text-muted-foreground mt-0.5 shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
          {label}
        </p>
        <p className={"font-medium mt-0.5 " + (mono ? "font-mono" : "")}>
          {value}
        </p>
      </div>
      {copyable && <CopyButton value={value} />}
    </div>
  );
}

function CopyableLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between w-full rounded-lg border bg-secondary/40 px-3 py-2 gap-3">
      <div className="min-w-0">
        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
          {label}
        </p>
        <p className="font-mono text-sm font-medium truncate">{value}</p>
      </div>
      <CopyButton value={value} />
    </div>
  );
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      className="shrink-0"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          toast({ title: "Copied", description: value });
          setTimeout(() => setCopied(false), 2000);
        } catch {
          toast({
            variant: "destructive",
            title: "Couldn't copy",
            description: "Your browser blocked clipboard access.",
          });
        }
      }}
    >
      {copied ? (
        <Check className="size-4 text-success" />
      ) : (
        <Copy className="size-4" />
      )}
    </Button>
  );
}

// Fallback for environments without lucide-react's icon coverage —
// not needed currently but kept here as a tripwire if an icon name
// is renamed upstream.
function _Loading() {
  return <Loader2 className="size-4 animate-spin" />;
}
