import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQueries, useQuery } from "@tanstack/react-query";
import { Banknote, CheckCircle2, Download, FileText, Home, Inbox, Loader2, Receipt, Wallet, Wrench } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { paymentsApi } from "@/lib/api/payments";
import { societyApi } from "@/lib/api/society";
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
import type {
  FlatChargeCategory,
  FlatMaintenanceRow,
  PaymentResponse,
  PaymentStatus,
} from "@/types/api";

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
          {/* Wrapper fetches society collection rows (which is
              where maintainer-added DUE charges actually live) and
              feeds them in as extra "Due now" items so the tenant
              sees them here, not just on the Society tab. Payment
              rows tagged SOCIETY_CHARGE still surface too — those
              are historical or bridge-in-flight items. */}
          <MaintenanceSectionWrapper
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
  extraDueItems,
  extraDueLoading,
}: {
  scope: PaymentBucket;
  payments: PaymentResponse[];
  loading: boolean;
  flatLookup: ReturnType<typeof useFlatLookup>;
  /** Extra "Due now" rows rendered ABOVE the Payment-derived
   *  cards. Used by the Maintenance tab to inject society-charge
   *  collection rows (which live in property-service, not
   *  payment-service) so a maintainer-added DUE bill still shows
   *  up as a payable item on the tenant's Payments page. */
  extraDueItems?: React.ReactNode[];
  /** Loading flag for whatever produced extraDueItems. When true,
   *  the "Due now" empty state is suppressed so the tenant doesn't
   *  see a false "you're all paid up" flash on first paint. */
  extraDueLoading?: boolean;
}) {
  const dueNow = payments.filter(
    (p) => p.status === "PENDING" || p.status === "OVERDUE",
  );
  const history = payments.filter(
    (p) => p.status === "PAID" || p.status === "FAILED",
  );
  const anyDue = dueNow.length > 0 || (extraDueItems?.length ?? 0) > 0;
  const anyLoading = loading || Boolean(extraDueLoading);

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
        {anyLoading && <Skeleton className="h-32 rounded-2xl" />}
        {!anyLoading && !anyDue && (
          <EmptyState
            variant="success"
            icon={CheckCircle2}
            title={dueEmptyTitle}
            description={dueEmptyDesc}
          />
        )}
        <div className="space-y-3">
          {/* Extra items first — for the Maintenance tab these are
              society-collection DUE rows, which are the newest
              info the tenant just discovered on their /app/society
              page. Showing them above legacy DUE Payment rows
              matches the order of intent. */}
          {extraDueItems}
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

/** Labels for the per-flat charge categories rendered inside a
 *  SocietyDueCard. Mirrors the maintainer Flat charges table so the
 *  tenant sees the same "Maintenance" / "Water bill" wording
 *  everywhere. */
const CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

/** Six most recent "YYYY-MM" strings, newest first — used to fetch
 *  the tenant's bills across a window that comfortably covers any
 *  DUE/OVERDUE charges. */
function lastNMonths(n: number): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 0; i < n; i += 1) {
    out.push(
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`,
    );
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}

/**
 * Wrapper for the Maintenance tab that pulls society-collection
 * rows from property-service (which is where maintainer-added DUE
 * charges actually live) and injects them into PaymentsSection as
 * extra "Due now" items.
 *
 * <p>Without this bridge, the Payments page's Maintenance tab only
 * saw Payment rows tagged SOCIETY_CHARGE — and those only exist
 * after the tenant has already gone through the Pay bridge. A
 * DUE charge the maintainer just added would live in
 * maintenance_collections until then, so the tenant would land on
 * the Payments page and see a misleading "You're all paid up".
 */
function MaintenanceSectionWrapper({
  payments,
  loading,
  flatLookup,
}: {
  payments: PaymentResponse[];
  loading: boolean;
  flatLookup: ReturnType<typeof useFlatLookup>;
}) {
  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });
  const buildingId = configQ.data?.buildingId ?? null;

  // Fetch six months in parallel; keeps a small window of open dues
  // visible without hammering the backend. Same cache keys the
  // Society tab uses so navigating between the two pages reuses
  // whichever fetches already ran.
  const months = useMemo(() => lastNMonths(6), []);
  const billQueries = useQueries({
    queries: months.map((m) => ({
      queryKey: ["tenant-society-bills", buildingId, m],
      queryFn: () => societyApi.myBills(buildingId!, m),
      enabled: !!buildingId,
      staleTime: 15_000,
    })),
  });

  const societyLoading =
    configQ.isLoading || billQueries.some((q) => q.isLoading);

  // Group DUE / OVERDUE rows by (flatNumber, forMonth) so the
  // Payments page renders ONE card per outstanding month instead of
  // one per charge (Maintenance + Water bill for the same month
  // would otherwise show as two look-alike cards). Dedup by
  // collectionId defensively — the (flat, month, category) unique
  // constraint on the collection table makes true duplicates
  // impossible, but a stray render is worse than a cheap Set check.
  const societyMonthGroups = useMemo(() => {
    const rows = billQueries.flatMap((q) => q.data ?? []);
    const seen = new Set<string>();
    const groups = new Map<
      string,
      {
        key: string;
        flatNumber: string;
        forMonth: string;
        rows: FlatMaintenanceRow[];
        total: number;
        overdue: boolean;
      }
    >();
    for (const r of rows) {
      if (r.status !== "DUE" && r.status !== "OVERDUE") continue;
      if (r.collectionId) {
        if (seen.has(r.collectionId)) continue;
        seen.add(r.collectionId);
      }
      const key = `${r.flatNumber ?? r.flatId}::${r.forMonth ?? ""}`;
      const existing = groups.get(key);
      if (existing) {
        existing.rows.push(r);
        existing.total += r.monthAmount;
        existing.overdue = existing.overdue || r.status === "OVERDUE";
      } else {
        groups.set(key, {
          key,
          flatNumber: r.flatNumber ?? r.flatId ?? "",
          forMonth: r.forMonth ?? "",
          rows: [r],
          total: r.monthAmount,
          overdue: r.status === "OVERDUE",
        });
      }
    }
    // Newest month first — matches "here's what I owe right now".
    return Array.from(groups.values()).sort((a, b) =>
      b.forMonth.localeCompare(a.forMonth),
    );
  }, [billQueries]);

  const extraDueItems = societyMonthGroups.map((g) => (
    <SocietyMonthDueCard key={g.key} group={g} buildingId={buildingId!} />
  ));

  return (
    <PaymentsSection
      scope="maintenance"
      payments={payments}
      loading={loading}
      flatLookup={flatLookup}
      extraDueItems={extraDueItems}
      extraDueLoading={societyLoading && !!buildingId}
    />
  );
}

/**
 * One card per outstanding month — aggregates every DUE / OVERDUE
 * category for that (flat, month) into a single line. Shows the
 * total + a category chip strip + a single "Pay all for <month>"
 * button that routes to the bulk-pay page. Individual per-charge
 * Pay buttons still live on the Society tab for tenants who want
 * to settle one category at a time; the Payments page's job is
 * "one tap, everything for this month done".
 */
function SocietyMonthDueCard({
  group,
  buildingId,
}: {
  group: {
    key: string;
    flatNumber: string;
    forMonth: string;
    rows: FlatMaintenanceRow[];
    total: number;
    overdue: boolean;
  };
  buildingId: string;
}) {
  return (
    <Card
      className={group.overdue ? "border-destructive/40" : "border-warning/40"}
    >
      <CardContent className="p-5 sm:p-6 grid gap-4 sm:grid-cols-[1fr_auto] items-center">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <p className="font-display font-semibold text-xl">
              {formatINR(group.total)}
            </p>
            <Badge variant={group.overdue ? "destructive" : "warning"}>
              {group.overdue ? "Overdue" : "Due"}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {group.forMonth} ·{" "}
            <span className="text-foreground">Flat {group.flatNumber}</span>
            {group.rows.length > 1 && (
              <> · {group.rows.length} charges</>
            )}
          </p>
          <div className="flex items-center gap-1.5 flex-wrap mt-2">
            {group.rows.map((r) => {
              const label = r.category
                ? CATEGORY_LABELS[r.category]
                : "Other";
              return (
                <Badge
                  key={r.collectionId ?? `${r.forMonth}-${r.category}`}
                  variant="secondary"
                  className="text-[10px]"
                >
                  {label} · {formatINR(r.monthAmount)}
                </Badge>
              );
            })}
          </div>
        </div>
        <div className="flex justify-end">
          <Button asChild variant="gradient" size="lg">
            <Link
              to={`/app/society/pay-all/${buildingId}/${group.forMonth}`}
            >
              <Wallet /> Pay all · {formatINR(group.total)}
            </Link>
          </Button>
        </div>
      </CardContent>
    </Card>
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
