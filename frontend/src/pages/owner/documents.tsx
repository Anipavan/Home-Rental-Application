import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Download,
  Receipt,
  Loader2,
  ScrollText,
  FileText,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { paymentsApi } from "@/lib/api/payments";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { EmptyState } from "@/components/ui/empty-state";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { formatDate, formatINR } from "@/lib/utils";

/**
 * Owner Document Vault — the single place an owner finds every PDF
 * tied to their rentals: receipts for every PAID rent payment they've
 * received, plus quick links to the dedicated Leases page (which has
 * full lease lifecycle controls beyond just "download PDF").
 *
 * <p>Why a separate page from /owner/leases: the owner's lease page
 * is a busy operational dashboard (renew / terminate / generate-RERA
 * / send-reminder). The vault is reading-only paperwork retrieval —
 * a tax-return-prep mental model, not a tenant-management one.
 *
 * <p>Tenant docs (Aadhaar / PAN uploaded by tenants) are
 * intentionally NOT shown here — those belong to the tenant; the
 * owner sees them only via the Documents review surface attached
 * to their assign-tenant flow.
 */
export function OwnerDocumentsPage() {
  const { authUserId } = useAuthStore();

  const paymentsQ = useQuery({
    queryKey: ["owner-payments", authUserId],
    queryFn: () => paymentsApi.byOwner(authUserId!),
    enabled: !!authUserId,
    staleTime: 60_000,
    retry: false,
  });

  // Resolve flatId → "A-302" so the receipt list reads as human
  // labels instead of UUID stubs. Single batched fetch.
  const flatLookup = useFlatLookup(
    (paymentsQ.data ?? []).map((p) => p.flatId),
  );

  const paid = (paymentsQ.data ?? [])
    .filter((p) => p.status === "PAID")
    .sort((a, b) => {
      const aD = a.paymentDate ?? a.dueDate ?? "";
      const bD = b.paymentDate ?? b.dueDate ?? "";
      return bD.localeCompare(aD);
    });

  return (
    <div className="max-w-4xl">
      <PageHeader
        title="Document vault"
        description="Receipts and lease PDFs for everything you've collected."
      />

      <ReceiptsCard
        paid={paid}
        loading={paymentsQ.isLoading}
        flatLookup={flatLookup}
      />

      {/* Leases pointer card — the dedicated /owner/leases page already
          does generation, signing, renewal, termination, and PDF
          download. Linking to it instead of duplicating that surface. */}
      <Card className="mt-6">
        <CardContent className="p-6 sm:p-8">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-start gap-3">
              <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center">
                <ScrollText className="size-5" />
              </div>
              <div>
                <h3 className="font-display font-semibold">
                  Lease agreements
                </h3>
                <p className="text-sm text-muted-foreground mt-0.5">
                  Manage lifecycle (draft / sign / renew / terminate) and
                  download PDFs from the dedicated Leases page.
                </p>
              </div>
            </div>
            <Button asChild variant="outline">
              <a href="/owner/leases">
                <FileText /> Open Leases
              </a>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

/* ─────────────────────── Receipts card ─────────────────────── */

function ReceiptsCard({
  paid,
  loading,
  flatLookup,
}: {
  paid: Array<{
    id: string;
    flatId: string;
    paymentDate?: string;
    dueDate: string;
    amount: number;
    totalAmount?: number;
    status: string;
    transactionId?: string;
    paymentMethod?: string;
  }>;
  loading: boolean;
  flatLookup: { nameOf: (id: string) => string };
}) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  async function handleDownload(paymentId: string) {
    setDownloadingId(paymentId);
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
      setDownloadingId(null);
    }
  }

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <h3 className="font-display font-semibold text-lg flex items-center gap-2">
          <Receipt className="size-4 text-primary" />
          Rent receipts {paid.length > 0 && `(${paid.length})`}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          One PDF per settled rent payment — GST-compliant, ready for
          your CA at year-end.
        </p>

        {loading ? (
          <div className="mt-4 space-y-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 rounded-xl" />
            ))}
          </div>
        ) : paid.length === 0 ? (
          <div className="mt-4">
            <EmptyState
              variant="info"
              icon={Receipt}
              title="No paid rent yet"
              description="Receipts appear here as soon as your first tenant settles a rent payment."
            />
          </div>
        ) : (
          <div className="mt-4 space-y-2">
            {paid.map((p) => (
              <div
                key={p.id}
                className="rounded-xl border bg-secondary/30 p-3 flex items-center gap-3"
              >
                <div className="size-9 rounded-lg bg-background grid place-items-center border shrink-0">
                  <Receipt className="size-4 text-muted-foreground" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="font-medium text-sm">
                      {formatINR(p.totalAmount ?? p.amount)}
                    </p>
                    <Badge variant="secondary" className="text-[10px]">
                      Flat {flatLookup.nameOf(p.flatId)}
                    </Badge>
                    <span className="text-xs text-muted-foreground">
                      paid {formatDate(p.paymentDate ?? p.dueDate)}
                    </span>
                  </div>
                  {p.transactionId && (
                    <p className="text-[11px] text-muted-foreground font-mono mt-0.5 truncate">
                      Txn {p.transactionId}
                      {p.paymentMethod &&
                        ` · ${p.paymentMethod.toLowerCase().replace(/_/g, " ")}`}
                    </p>
                  )}
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => handleDownload(p.id)}
                  disabled={downloadingId === p.id}
                >
                  {downloadingId === p.id ? (
                    <Loader2 className="animate-spin" />
                  ) : (
                    <Download />
                  )}
                  PDF
                </Button>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
