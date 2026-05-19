import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Banknote, Download, FileText, Loader2, QrCode, Receipt } from "lucide-react";
import { UpiPayDialog } from "@/components/tenant/upi-pay-dialog";
import { useAuthStore } from "@/stores/auth-store";
import { paymentsApi } from "@/lib/api/payments";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse, PaymentStatus } from "@/types/api";

/**
 * Trigger a browser download for a Blob fetched from the API.
 * Used for receipt + invoice PDFs.
 */
async function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function PaymentsListPage() {
  const { authUserId } = useAuthStore();
  const q = useQuery({
    queryKey: ["my-payments", authUserId],
    queryFn: () => paymentsApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  const payments = q.data ?? [];
  const dueNow = payments.filter(
    (p) => p.status === "PENDING" || p.status === "OVERDUE",
  );
  const history = payments.filter(
    (p) => p.status === "PAID" || p.status === "FAILED",
  );

  // Resolve flatId UUIDs -> "A-302" once for the whole page.
  const flatLookup = useFlatLookup(payments.map((p) => p.flatId));

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Payments"
        description="Pay rent, download receipts, see your history."
      />

      <section className="mb-8">
        <h2 className="font-display font-semibold text-lg mb-3">Due now</h2>
        {q.isLoading && <Skeleton className="h-32 rounded-2xl" />}
        {!q.isLoading && dueNow.length === 0 && (
          <Card className="p-10 text-center bg-success/5 border-success/20">
            <p className="font-display font-semibold text-lg">
              You're all paid up.
            </p>
            <p className="text-muted-foreground text-sm mt-1">
              Your next bill will appear here when it's generated.
            </p>
          </Card>
        )}
        <div className="space-y-3">
          {dueNow.map((p) => (
            <DueCard
              key={p.id}
              payment={p}
              flatLabel={flatLookup.nameOf(p.flatId)}
            />
          ))}
        </div>
      </section>

      <section>
        <h2 className="font-display font-semibold text-lg mb-3">History</h2>
        <Card>
          {q.isLoading &&
            Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="px-6 py-4 border-b border-border/60 last:border-0">
                <Skeleton className="h-12" />
              </div>
            ))}
          {!q.isLoading && history.length === 0 && (
            <div className="p-10 text-center text-muted-foreground">
              No past payments yet.
            </div>
          )}
          {history.map((p) => (
            <HistoryRow key={p.id} payment={p} />
          ))}
        </Card>
      </section>
    </div>
  );
}

function DueCard({
  payment,
  flatLabel,
}: {
  payment: PaymentResponse;
  /** Pre-resolved flat number ("A-302") — passed in by the caller. */
  flatLabel: string;
}) {
  const overdue = payment.status === "OVERDUE";
  // Invoice is only meaningful once the payment is settled. Before
  // payment, an "invoice" would just be a copy of the demand on file
  // — the GST invoice line items, transaction id, paid-on date etc.
  // don't exist yet. Mirror the same gate the History row uses on its
  // Receipt button so the UX is consistent: a successfully-paid
  // payment is the only state where an invoice is downloadable.
  const isPaid = payment.status === "PAID";
  const [downloadingInvoice, setDownloadingInvoice] = useState(false);
  const [upiOpen, setUpiOpen] = useState(false);

  async function handleInvoiceDownload() {
    setDownloadingInvoice(true);
    try {
      const blob = await paymentsApi.invoicePdf(payment.id);
      await downloadBlob(blob, `invoice-${payment.id.slice(0, 8)}.pdf`);
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Couldn't download invoice",
        description: extractErrorMessage(e),
      });
    } finally {
      setDownloadingInvoice(false);
    }
  }

  return (
    <Card className={overdue ? "border-destructive/40" : "border-warning/40"}>
      <CardContent className="p-5 sm:p-6 grid gap-4 sm:grid-cols-[1fr_auto] items-center">
        <div>
          <div className="flex items-center gap-2">
            <p className="font-display font-semibold text-xl">
              {formatINR(payment.totalAmount ?? payment.amount)}
            </p>
            <StatusBadge status={payment.status} />
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Due {formatDate(payment.dueDate)} ·{" "}
            <span className="text-foreground">Flat {flatLabel}</span>
          </p>
          {payment.lateFee && payment.lateFee > 0 ? (
            <p className="text-xs text-destructive mt-1">
              Late fee: {formatINR(payment.lateFee)}
            </p>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2 justify-end">
          <Button
            variant="outline"
            size="lg"
            onClick={handleInvoiceDownload}
            disabled={!isPaid || downloadingInvoice}
            title={
              isPaid
                ? "Download GST invoice PDF"
                : "Invoice will be available after payment is completed"
            }
          >
            {downloadingInvoice ? (
              <Loader2 className="animate-spin" />
            ) : (
              <FileText />
            )}
            Invoice
          </Button>
          {/* Pay-via-UPI is the primary, India-realistic path: tenant
              scans owner's QR, money goes direct to owner's bank,
              owner marks as received. The gradient "Pay" button
              below still routes to the Razorpay flow for users who'd
              rather have the platform mediate via card / netbanking. */}
          <Button
            variant="gradient"
            size="lg"
            onClick={() => setUpiOpen(true)}
          >
            <QrCode /> Pay {formatINR(payment.totalAmount ?? payment.amount)}
          </Button>
        </div>
      </CardContent>
      <UpiPayDialog
        open={upiOpen}
        onOpenChange={setUpiOpen}
        payment={payment}
      />
    </Card>
  );
}

function HistoryRow({ payment }: { payment: PaymentResponse }) {
  const [downloading, setDownloading] = useState(false);
  const isPaid = payment.status === "PAID";

  async function handleReceiptDownload() {
    setDownloading(true);
    try {
      const blob = await paymentsApi.receiptPdf(payment.id);
      await downloadBlob(blob, `receipt-${payment.id.slice(0, 8)}.pdf`);
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
    <div className="px-5 sm:px-6 py-4 border-b border-border/60 last:border-0 grid grid-cols-[1fr_auto_auto] gap-3 items-center">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <p className="font-medium">
            {formatINR(payment.totalAmount ?? payment.amount)}
          </p>
          <StatusBadge status={payment.status} />
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">
          {payment.paymentDate
            ? `Paid ${formatDate(payment.paymentDate)}`
            : `Due ${formatDate(payment.dueDate)}`}
          {payment.transactionId && ` · ${payment.transactionId}`}
        </p>
      </div>
      {/* Method label — "Cash" gets a coin icon + slightly more
          prominent styling so the tenant can immediately spot owner-
          recorded cash receipts vs gateway-driven settlements they'd
          done themselves. */}
      <div className="hidden sm:block text-xs">
        {payment.paymentMethod === "CASH" ? (
          <span className="inline-flex items-center gap-1 font-medium text-foreground">
            <Banknote className="size-3.5" /> Cash
          </span>
        ) : (
          <span className="text-muted-foreground capitalize">
            {payment.paymentMethod?.toLowerCase().replace("_", " ") ?? "—"}
          </span>
        )}
      </div>
      <Button
        variant="ghost"
        size="sm"
        disabled={!isPaid || downloading}
        onClick={handleReceiptDownload}
        title={isPaid ? "Download receipt PDF" : "Receipt available once paid"}
      >
        {downloading ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <Download className="size-4" />
        )}
        <span className="hidden sm:inline">Receipt</span>
      </Button>
    </div>
  );
}

function StatusBadge({ status }: { status: PaymentStatus }) {
  if (status === "PAID")
    return (
      <Badge variant="success">
        <Receipt className="size-3" /> Paid
      </Badge>
    );
  if (status === "PROCESSING")
    return <Badge variant="warning">Processing</Badge>;
  if (status === "OVERDUE") return <Badge variant="destructive">Overdue</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  if (status === "CANCELLED") return <Badge variant="secondary">Cancelled</Badge>;
  if (status === "REFUNDED") return <Badge variant="secondary">Refunded</Badge>;
  return <Badge variant="warning">Pending</Badge>;
}
