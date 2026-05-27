import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Mail,
  Phone,
  Calendar,
  Building2,
  Receipt,
  Copy,
  Check,
  CreditCard,
  Hash,
  Loader2,
  Download,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { usersApi } from "@/lib/api/users";
import { paymentsApi } from "@/lib/api/payments";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { cn, formatDate, formatINR } from "@/lib/utils";
import type { PaymentResponse, PaymentStatus } from "@/types/api";

/**
 * Admin-only payment detail dialog. Opens when an admin clicks any row
 * on /admin/payments. Shows the full payment picture in one place so
 * the admin can:
 *   • see who owes whom for which flat
 *   • email or phone either party in one tap (no copy-paste)
 *   • download the receipt PDF (if the payment is settled)
 *   • copy the gateway transaction id for a Razorpay dashboard lookup
 *
 * Auth-service profile fetches run in parallel for tenant + owner so
 * the dialog loads in one round-trip from the user's perspective. A
 * 404 from either side is treated as "no full profile yet" — common
 * for accounts that registered but haven't completed onboarding.
 */
export function PaymentDetailDialog({
  payment,
  tenantName,
  ownerName,
  flatLabel,
  open,
  onOpenChange,
}: {
  payment: PaymentResponse | null;
  /** Pre-resolved tenant userName from the table's lookup map. */
  tenantName?: string;
  /** Pre-resolved owner userName from the table's lookup map. */
  ownerName?: string;
  /** Pre-resolved "#101 · Sunshine Residency"-style flat label. */
  flatLabel?: string;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const tenantId = payment?.tenantId ? String(payment.tenantId) : null;
  const ownerId = payment?.ownerId ? String(payment.ownerId) : null;

  // Fetch tenant + owner profiles in parallel — only when the dialog
  // is actually open. React Query caches per-id, so re-opening the
  // same payment hits the cache instead of refetching.
  const tenantQ = useQuery({
    queryKey: ["admin", "user-profile", tenantId],
    queryFn: () => usersApi.byAuthId(tenantId!),
    enabled: open && !!tenantId,
    staleTime: 60_000,
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 404 && failureCount < 1;
    },
  });
  const ownerQ = useQuery({
    queryKey: ["admin", "user-profile", ownerId],
    queryFn: () => usersApi.byAuthId(ownerId!),
    enabled: open && !!ownerId,
    staleTime: 60_000,
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 404 && failureCount < 1;
    },
  });

  const tenant = tenantQ.data;
  const owner = ownerQ.data;
  const tenantEmail = tenant?.email;
  const ownerEmail = owner?.email;
  const tenantPhone = tenant?.phone;
  const ownerPhone = owner?.phone;
  const tenantDisplay =
    tenant && (tenant.firstName || tenant.lastName)
      ? `${tenant.firstName ?? ""} ${tenant.lastName ?? ""}`.trim()
      : tenantName ?? tenantId ?? "Unknown tenant";
  const ownerDisplay =
    owner && (owner.firstName || owner.lastName)
      ? `${owner.firstName ?? ""} ${owner.lastName ?? ""}`.trim()
      : ownerName ?? ownerId ?? "Unknown owner";

  const [downloading, setDownloading] = useState(false);
  async function handleReceiptDownload() {
    if (!payment) return;
    setDownloading(true);
    try {
      const blob = await paymentsApi.receiptPdf(payment.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `receipt-${payment.id.slice(0, 8)}.pdf`;
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Receipt className="size-5 text-primary" />
            Payment details
          </DialogTitle>
          <DialogDescription>
            Full transaction info plus direct contact for both parties.
          </DialogDescription>
        </DialogHeader>

        {!payment ? null : (
          <div className="space-y-5">
            {/* Hero strip — amount + status, on a brand-soft wash to
                draw the eye to the most important number on the
                dialog. */}
            <div className="rounded-xl gradient-brand-soft border border-primary/15 p-5 flex items-center justify-between">
              <div>
                <p className="text-xs uppercase tracking-wider text-muted-foreground">
                  Total payable
                </p>
                <p className="font-display font-bold text-3xl mt-1">
                  {formatINR(payment.totalAmount ?? payment.amount)}
                </p>
                {payment.lateFee && payment.lateFee > 0 ? (
                  <p className="text-xs text-destructive mt-1">
                    Includes {formatINR(payment.lateFee)} late fee
                  </p>
                ) : null}
              </div>
              <StatusBadge status={payment.status} />
            </div>

            {/* Flat row — quick context anchor. */}
            <Row
              icon={Building2}
              label="Flat"
              value={flatLabel ?? `#${String(payment.flatId).slice(0, 8)}…`}
            />

            <Separator />

            {/* Two side-by-side party cards — tenant + owner with
                contact actions baked in. */}
            <div className="grid sm:grid-cols-2 gap-4">
              <PartyCard
                role="Tenant"
                name={tenantDisplay}
                email={tenantEmail}
                phone={tenantPhone}
                loading={tenantQ.isLoading}
                tint="sky"
              />
              <PartyCard
                role="Owner"
                name={ownerDisplay}
                email={ownerEmail}
                phone={ownerPhone}
                loading={ownerQ.isLoading}
                tint="amber"
              />
            </div>

            <Separator />

            {/* Transaction metadata. */}
            <div className="space-y-2.5">
              <Row
                icon={Calendar}
                label="Due"
                value={formatDate(payment.dueDate)}
              />
              {payment.paymentDate && (
                <Row
                  icon={Calendar}
                  label="Paid"
                  value={formatDate(payment.paymentDate)}
                />
              )}
              {payment.paymentMethod && (
                <Row
                  icon={CreditCard}
                  label="Method"
                  value={payment.paymentMethod
                    .toLowerCase()
                    .replace(/_/g, " ")}
                  capitalize
                />
              )}
              {payment.transactionId && (
                <Row
                  icon={Hash}
                  label="Txn ID"
                  value={payment.transactionId}
                  mono
                  copyable
                />
              )}
              <Row
                icon={Hash}
                label="Payment ID"
                value={payment.id}
                mono
                copyable
              />
              {payment.failureReason && (
                <Row
                  icon={Hash}
                  label="Failure"
                  value={payment.failureReason}
                  destructive
                />
              )}
            </div>

            {payment.status === "PAID" && (
              <div className="flex justify-end">
                <Button
                  onClick={handleReceiptDownload}
                  disabled={downloading}
                  variant="outline"
                >
                  {downloading ? (
                    <Loader2 className="animate-spin" />
                  ) : (
                    <Download />
                  )}
                  Download receipt PDF
                </Button>
              </div>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

/* ─────────────────────────── building blocks ─────────────────────────── */

function PartyCard({
  role,
  name,
  email,
  phone,
  loading,
  tint,
}: {
  role: "Tenant" | "Owner";
  name: string;
  email?: string;
  phone?: string;
  loading?: boolean;
  tint: "sky" | "amber";
}) {
  // Tint matches the role colours used on /admin/users so a tenant
  // card here reads as the same identity. Light wash, no harsh
  // saturation — the contact buttons inside need to stay legible.
  const wash =
    tint === "sky"
      ? "bg-sky-50/60 border-sky-200/60"
      : "bg-amber-50/60 border-amber-200/60";

  return (
    <div className={cn("rounded-xl border p-4", wash)}>
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
        {role}
      </p>
      <p className="font-display font-semibold text-base mt-1 truncate">
        {name}
      </p>
      {loading ? (
        <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="size-3 animate-spin" />
          Loading contact info…
        </div>
      ) : (
        <div className="mt-3 space-y-2">
          {email ? (
            <Button
              asChild
              variant="outline"
              size="sm"
              className="w-full justify-start bg-white"
            >
              <a href={`mailto:${email}`}>
                <Mail className="size-3.5" />
                <span className="truncate">{email}</span>
              </a>
            </Button>
          ) : (
            <p className="text-xs text-muted-foreground italic">
              No email on file
            </p>
          )}
          {phone ? (
            <Button
              asChild
              variant="outline"
              size="sm"
              className="w-full justify-start bg-white"
            >
              <a href={`tel:${phone}`}>
                <Phone className="size-3.5" />
                <span className="truncate">{phone}</span>
              </a>
            </Button>
          ) : (
            <p className="text-xs text-muted-foreground italic">
              No phone on file
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function Row({
  icon: Icon,
  label,
  value,
  mono,
  capitalize,
  destructive,
  copyable,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  mono?: boolean;
  capitalize?: boolean;
  destructive?: boolean;
  copyable?: boolean;
}) {
  return (
    <div className="flex items-center gap-3">
      <Icon className="size-4 text-muted-foreground shrink-0" />
      <span className="text-xs uppercase tracking-wider text-muted-foreground w-24 shrink-0">
        {label}
      </span>
      <span
        className={cn(
          "flex-1 truncate text-sm",
          mono && "font-mono text-xs",
          capitalize && "capitalize",
          destructive && "text-destructive",
        )}
        title={value}
      >
        {value}
      </span>
      {copyable && <CopyButton value={value} />}
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
      className="size-7 shrink-0"
      onClick={async (e) => {
        e.stopPropagation();
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
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
        <Check className="size-3.5 text-success" />
      ) : (
        <Copy className="size-3.5" />
      )}
    </Button>
  );
}

function StatusBadge({ status }: { status: PaymentStatus }) {
  if (status === "PAID") return <Badge variant="success">Paid</Badge>;
  if (status === "PROCESSING")
    return <Badge variant="warning">Processing</Badge>;
  if (status === "OVERDUE") return <Badge variant="destructive">Overdue</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  if (status === "CANCELLED")
    return <Badge variant="secondary">Cancelled</Badge>;
  if (status === "REFUNDED")
    return <Badge variant="secondary">Refunded</Badge>;
  return <Badge variant="warning">Pending</Badge>;
}
