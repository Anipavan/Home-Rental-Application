import { useState, type MouseEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  FileText,
  Loader2,
  ScrollText,
  XCircle,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { agreementsApi } from "@/lib/api/agreements";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { useUserLookup } from "@/hooks/use-user-lookup";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, formatINR } from "@/lib/utils";
import type {
  AgreementResponseDTO,
  AgreementStatus,
} from "@/types/api";

export function OwnerAgreementsPage() {
  const { authUserId } = useAuthStore();
  const [selected, setSelected] = useState<AgreementResponseDTO | null>(null);

  const q = useQuery({
    queryKey: ["owner-agreements", authUserId],
    queryFn: () => agreementsApi.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  // Defensive id-dedup. If the API ever returns the same agreement
  // row twice (e.g. a Kafka-consumer retry hydrated the row a second
  // time), the owner page would render two identical cards under the
  // same status tab. Unlike the tenant view we don't dedup by flatId
  // here — the owner legitimately wants to see every agreement they
  // have across every flat, including the historical SIGNED row plus
  // any current PENDING_SIGNATURE for the same flat. Just collapse
  // exact-id duplicates.
  const all = Array.from(
    new Map((q.data ?? []).map((a) => [a.id, a])).values(),
  );
  const pending = all.filter((a) => a.status === "PENDING_SIGNATURE");
  const signed = all.filter((a) => a.status === "SIGNED");
  const rejected = all.filter((a) => a.status === "REJECTED");

  if (selected) {
    return (
      <AgreementDetail
        agreement={selected}
        onBack={() => setSelected(null)}
      />
    );
  }

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Lease agreements"
        description="Every agreement between you and your tenants."
      />

      {q.isLoading && (
        <div className="grid gap-3 lg:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-32 rounded-2xl" />
          ))}
        </div>
      )}

      {!q.isLoading && all.length === 0 && (
        <Card className="p-12 text-center">
          <ScrollText className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            No agreements yet
          </p>
          <p className="text-muted-foreground text-sm mt-1 max-w-sm mx-auto">
            Agreements are created automatically when you assign a tenant to a flat.
          </p>
        </Card>
      )}

      {!q.isLoading && all.length > 0 && (
        <Tabs defaultValue="pending">
          <TabsList>
            <TabsTrigger value="pending">
              Awaiting ({pending.length})
            </TabsTrigger>
            <TabsTrigger value="signed">Signed ({signed.length})</TabsTrigger>
            <TabsTrigger value="rejected">
              Rejected ({rejected.length})
            </TabsTrigger>
            <TabsTrigger value="all">All ({all.length})</TabsTrigger>
          </TabsList>
          <TabsContent value="pending">
            <Grid items={pending} onSelect={setSelected} />
          </TabsContent>
          <TabsContent value="signed">
            <Grid items={signed} onSelect={setSelected} />
          </TabsContent>
          <TabsContent value="rejected">
            <Grid items={rejected} onSelect={setSelected} />
          </TabsContent>
          <TabsContent value="all">
            <Grid items={all} onSelect={setSelected} />
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}

function Grid({
  items,
  onSelect,
}: {
  items: AgreementResponseDTO[];
  onSelect: (a: AgreementResponseDTO) => void;
}) {
  // Resolve flatId UUIDs -> "A-302" and tenantId Long-strings -> "First Last"
  // for every card in this grid in a single batched fetch (60 s cache).
  const flatLookup = useFlatLookup(items.map((a) => a.flatId));
  const userLookup = useUserLookup(items.map((a) => a.tenantId));

  if (items.length === 0) {
    return (
      <Card className="p-12 text-center text-muted-foreground">
        Nothing here.
      </Card>
    );
  }
  return (
    <div className="grid gap-3 lg:grid-cols-2">
      {items.map((a) => (
        <button
          key={a.id}
          type="button"
          onClick={() => onSelect(a)}
          className="text-left"
        >
          <Card className="hover:shadow-lift transition-shadow">
            <CardContent className="p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3 min-w-0">
                  <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
                    <FileText className="size-4" />
                  </div>
                  <div className="min-w-0">
                    <p className="font-medium truncate">
                      Flat {flatLookup.nameOf(a.flatId)}
                    </p>
                    <p className="text-xs text-muted-foreground truncate">
                      {userLookup.nameOf(a.tenantId)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <StatusBadge status={a.status} />
                  <InlineDownloadDeedButton agreementId={a.id} />
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3 mt-4 text-xs">
                <div>
                  <p className="text-muted-foreground">Rent</p>
                  <p className="font-semibold mt-0.5">
                    {formatINR(a.rentAmount)}
                  </p>
                </div>
                <div>
                  <p className="text-muted-foreground">Starts</p>
                  <p className="font-semibold mt-0.5">
                    {formatDate(a.leaseStartDate)}
                  </p>
                </div>
                <div>
                  <p className="text-muted-foreground">Ends</p>
                  <p className="font-semibold mt-0.5">
                    {formatDate(a.leaseEndDate)}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </button>
      ))}
    </div>
  );
}

/**
 * Compact download trigger placed inside each agreement card. Stops both
 * propagation and default so clicking it doesn't open the card detail.
 * Backend renders the deed on-demand if it doesn't exist yet, so this
 * works for any agreement status.
 */
function InlineDownloadDeedButton({ agreementId }: { agreementId: string }) {
  const [pending, setPending] = useState(false);
  async function handleClick(e: MouseEvent<HTMLButtonElement>) {
    e.preventDefault();
    e.stopPropagation();
    setPending(true);
    try {
      const blob = await agreementsApi.downloadDocument(agreementId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lease-${agreementId.slice(0, 12)}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Couldn't download lease",
        description: extractErrorMessage(e),
      });
    } finally {
      setPending(false);
    }
  }
  return (
    <Button
      type="button"
      size="sm"
      variant="outline"
      onClick={handleClick}
      disabled={pending}
      title="Download lease as PDF"
    >
      {pending ? (
        <Loader2 className="size-3.5 animate-spin" />
      ) : (
        <Download className="size-3.5" />
      )}
      <span className="hidden sm:inline">PDF</span>
    </Button>
  );
}

function AgreementDetail({
  agreement,
  onBack,
}: {
  agreement: AgreementResponseDTO;
  onBack: () => void;
}) {
  // Resolve the single flat + tenant + owner for the detail header so
  // it reads "Lease for flat A-302 · Asha Rao" instead of two raw UUIDs.
  // The owner id is included so the Parties block can fall back to a
  // local lookup when the DTO's ownerName field is null (e.g. legacy
  // rows from before the resolution was added).
  const flatLookup = useFlatLookup([agreement.flatId]);
  const userLookup = useUserLookup([agreement.tenantId, agreement.ownerId]);

  return (
    <div className="animate-fade-in max-w-3xl">
      <Button variant="ghost" size="sm" className="mb-3" onClick={onBack}>
        <ArrowLeft /> Back to agreements
      </Button>
      <PageHeader
        title={`Lease for flat ${flatLookup.nameOf(agreement.flatId)}`}
        description={userLookup.nameOf(agreement.tenantId)}
      />

      <Card>
        <CardContent className="p-6 sm:p-8">
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-3">
              <div className="size-11 rounded-xl bg-primary/10 text-primary grid place-items-center">
                <FileText className="size-5" />
              </div>
              <div>
                <h2 className="font-display text-xl font-semibold">
                  Residential Lease Agreement
                </h2>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Created {formatDate(agreement.createdAt)}
                </p>
              </div>
            </div>
            <StatusBadge status={agreement.status} />
          </div>

          {/* Parties — Issue #5: prefer the DTO's resolved names
              (server-side via user-service KYC) and fall back to the
              local user-lookup batched fetch when missing. The raw id
              is still rendered as a fallback last so support / audits
              can still cross-reference. */}
          <div className="grid sm:grid-cols-2 gap-4 text-sm mb-4">
            <KV
              label="Owner"
              value={
                agreement.ownerName ||
                userLookup.nameOf(agreement.ownerId) ||
                agreement.ownerId
              }
            />
            <KV
              label="Tenant"
              value={
                agreement.tenantName ||
                userLookup.nameOf(agreement.tenantId) ||
                agreement.tenantId
              }
            />
          </div>

          <div className="grid sm:grid-cols-3 gap-4 text-sm mb-6">
            <KV label="Monthly rent" value={formatINR(agreement.rentAmount)} />
            <KV
              label="Lease starts"
              value={formatDate(agreement.leaseStartDate)}
            />
            <KV
              label="Lease ends"
              value={formatDate(agreement.leaseEndDate)}
            />
          </div>

          <Separator className="my-5" />

          <h3 className="font-display font-semibold text-sm uppercase tracking-wider text-muted-foreground mb-2">
            Terms &amp; Conditions
          </h3>
          <div className="rounded-xl border bg-secondary/30 p-5 max-h-72 overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed">
            {agreement.terms?.trim() ||
              "No additional terms recorded — the standard lease applies."}
          </div>

          {/* Always offer the deed download. Backend renders the PDF
              on-demand from the current terms when no rendered file
              exists yet, so owners can grab the lease at any stage. */}
          <div className="mt-4 flex justify-end">
            <DownloadDeedButton agreementId={agreement.id} />
          </div>

          {agreement.status === "SIGNED" && (
            <div className="mt-6 rounded-xl border bg-success/5 border-success/30 p-5">
              <div className="flex items-start gap-3">
                <CheckCircle2 className="size-5 text-success mt-0.5 shrink-0" />
                <div className="flex-1">
                  <p className="font-semibold">
                    Signed by tenant on {formatDate(agreement.signedAt)}
                  </p>
                  <p className="text-sm text-muted-foreground mt-0.5">
                    The signed deed is available as a PDF.
                  </p>
                </div>
              </div>
              <div className="mt-4 flex flex-wrap items-center gap-3">
                {agreement.signatureData && (
                  <div className="rounded-lg border bg-white p-2">
                    <img
                      src={agreement.signatureData}
                      alt="Tenant signature"
                      className="max-h-16"
                    />
                  </div>
                )}
              </div>
            </div>
          )}

          {agreement.status === "REJECTED" && (
            <div className="mt-6 rounded-xl border bg-destructive/5 border-destructive/30 p-5">
              <div className="flex items-start gap-3">
                <XCircle className="size-5 text-destructive mt-0.5 shrink-0" />
                <div className="flex-1">
                  <p className="font-semibold">
                    Tenant rejected this agreement on{" "}
                    {formatDate(agreement.rejectedAt)}
                  </p>
                  {agreement.rejectionReason && (
                    <>
                      <p className="text-xs uppercase tracking-wider text-muted-foreground mt-3 mb-1">
                        Reason
                      </p>
                      <p className="text-sm">{agreement.rejectionReason}</p>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}

          {agreement.status === "PENDING_SIGNATURE" && (
            <div className="mt-6 rounded-xl border bg-warning/5 border-warning/30 p-5 text-sm">
              <p className="font-medium">Awaiting tenant signature</p>
              <p className="text-muted-foreground mt-1">
                Your tenant will see this in their app and can sign or reject.
                Status updates will appear here automatically.
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function StatusBadge({ status }: { status: AgreementStatus }) {
  if (status === "SIGNED") return <Badge variant="success">Signed</Badge>;
  if (status === "REJECTED")
    return <Badge variant="destructive">Rejected</Badge>;
  return <Badge variant="warning">Awaiting signature</Badge>;
}

function KV({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium mt-0.5">{value}</p>
    </div>
  );
}

function DownloadDeedButton({ agreementId }: { agreementId: string }) {
  const [pending, setPending] = useState(false);
  return (
    <Button
      variant="outline"
      size="sm"
      disabled={pending}
      onClick={async () => {
        setPending(true);
        try {
          const blob = await agreementsApi.downloadDocument(agreementId);
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `lease-agreement-${agreementId}.pdf`;
          a.click();
          URL.revokeObjectURL(url);
        } catch (e) {
          toast({
            variant: "destructive",
            title: "Couldn't download PDF",
            description: extractErrorMessage(e),
          });
        } finally {
          setPending(false);
        }
      }}
    >
      {pending ? <Loader2 className="size-4 animate-spin" /> : <Download />}
      Download lease (PDF)
    </Button>
  );
}
