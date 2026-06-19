import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Banknote, CheckCircle2, Download, FileText, Home, Inbox, Loader2, Receipt, Wallet, Wrench } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { paymentsApi } from "@/lib/api/payments";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse, PaymentStatus } from "@/types/api";

/**
 * Payment sourceType bucket. Anything not explicitly tagged
 * SOCIETY_CHARGE falls into "rent" so legacy rows (pre-V2 migration)
 * stay on the Rent tab where they were always assumed to live.
 */
type PaymentBucket = "rent" | "maintenance";

function bucketOf(p: PaymentResponse): PaymentBucket {
  return p.sourceType === "SOCIETY_CHARGE" ? "maintenance" : "rent";
}

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
  const [searchParams, setSearchParams] = useSearchParams();

  // Initial tab pulled from the URL — the Razorpay SuccessView lands
  // here with ?type=rent or ?type=maintenance so the user sees the
  // category they just paid. Default to "rent" because rent is the
  // higher-volume, daily-default case.
  const tabFromUrl = searchParams.get("type") === "maintenance"
    ? "maintenance"
    : "rent";
  const [tab, setTab] = useState<PaymentBucket>(tabFromUrl);

  const q = useQuery({
    queryKey: ["my-payments", authUserId],
    queryFn: () => paymentsApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  const payments = q.data ?? [];

  // Split once by bucket so each tab can filter dueNow + history off
  // a stable slice rather than re-walking the full array twice.
  const rentPayments = useMemo(
    () => payments.filter((p) => bucketOf(p) === "rent"),
    [payments],
  );
  const maintenancePayments = useMemo(
    () => payments.filter((p) => bucketOf(p) === "maintenance"),
    [payments],
  );

  // Resolve flatId UUIDs -> "A-302" once for the whole page.
  const flatLookup = useFlatLookup(payments.map((p) => p.flatId));

  /** Keep the URL in sync as the user clicks between tabs so a copy-
   *  paste of the URL or a back-button hop lands on the same view. */
  const handleTabChange = (next: string) => {
    const value = (next === "maintenance" ? "maintenance" : "rent") as PaymentBucket;
    setTab(value);
    if (value === "rent") {
      searchParams.delete("type");
    } else {
      searchParams.set("type", value);
    }
    setSearchParams(searchParams, { replace: true });
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Payments"
        description="Pay rent, download receipts, see your history."
      />

      <Tabs value={tab} onValueChange={handleTabChange} className="w-full">
        <TabsList className="mb-6">
          <TabsTrigger value="rent">
            <Home className="size-4" /> Rent
          </TabsTrigger>
          <TabsTrigger value="maintenance">
            <Wrench className="size-4" /> Maintenance
          </TabsTrigger>
        </TabsList>

        <TabsContent value="rent" className="mt-0">
          <PaymentsSection
            scope="rent"
            payments={rentPayments}
            loading={q.isLoading}
            flatLookup={flatLookup}
          />
        </TabsContent>

        <TabsContent value="maintenance" className="mt-0">
          <PaymentsSection
            scope="maintenance"
            payments={maintenancePayments}
            loading={q.isLoading}
            flatLookup={flatLookup}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}

/**
 * One tab's content. Same "Due now + History" shape we had before, just
 * scoped to a single payment bucket. Empty-state copy varies by scope
 * so the success / quiet states read naturally for either flow.
 */
function PaymentsSection({
  scope,
  payments,
  loading,
  flatLookup,
}: {
  scope: PaymentBucket;
  payments: PaymentResponse[];
  loading: boolean;
  flatLookup: ReturnType<typeof useFlatLookup>;
}) {
  const dueNow = payments.filter(
    (p) => p.status === "PENDING" || p.status === "OVERDUE",
  );
  const history = payments.filter(
    (p) => p.status === "PAID" || p.status === "FAILED",
  );

  const dueEmptyTitle =
    scope === "rent" ? "You're all paid up." : "No maintenance dues right now.";
  const dueEmptyDesc =
    scope === "rent"
      ? "Your next rent bill will appear here when it's generated. Until then, enjoy the home."
      : "Society charges show up here when the maintainer adds new bills. Until then, you're settled.";
  const historyEmptyTitle =
    scope === "rent" ? "No past rent payments yet." : "No past maintenance payments yet.";
  const historyEmptyDesc =
    scope === "rent"
      ? "Receipts and invoices appear here after your first rent payment goes through."
      : "Maintenance receipts appear here after your first society payment goes through.";

  return (
    <>
      <section className="mb-8">
        <h2 className="font-display font-semibold text-lg mb-3">Due now</h2>
        {loading && <Skeleton className="h-32 rounded-2xl" />}
        {!loading && dueNow.length === 0 && (
          <EmptyState
            variant="success"
            icon={CheckCircle2}
            title={dueEmptyTitle}
            description={dueEmptyDesc}
          />
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
          {loading &&
            Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="px-6 py-4 border-b border-border/60 last:border-0">
                <Skeleton className="h-12" />
              </div>
            ))}
          {!loading && history.length === 0 && (
            <EmptyState
              variant="info"
              icon={Inbox}
              title={historyEmptyTitle}
              description={historyEmptyDesc}
              className="border-0 shadow-none rounded-none"
            />
          )}
          {history.map((p) => (
            <HistoryRow key={p.id} payment={p} />
          ))}
        </Card>
      </section>
    </>
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
          {/* Route to the rich checkout page (`/app/payments/:id/pay`)
              instead of the lightweight QR + bank-fallback dialog.
              That page renders the UPI app picker (PhonePe, GPay,
              Paytm, Other UPI) plus the live-validated UpiIdField and
              card / net-banking fallback, which is what the user
              expects from a modern rent-collection flow. */}
          <Button asChild variant="gradient" size="lg">
            <Link to={`/app/payments/${payment.id}/pay`}>
              <Wallet /> Pay {formatINR(payment.totalAmount ?? payment.amount)}
            </Link>
          </Button>
        </div>
      </CardContent>
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
