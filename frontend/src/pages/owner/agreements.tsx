import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  FileText,
  ScrollText,
  XCircle,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { agreementsApi } from "@/lib/api/agreements";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
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

  const all = q.data ?? [];
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
                      Flat #{a.flatId}
                    </p>
                    <p className="text-xs text-muted-foreground truncate">
                      Tenant {a.tenantId}
                    </p>
                  </div>
                </div>
                <StatusBadge status={a.status} />
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

function AgreementDetail({
  agreement,
  onBack,
}: {
  agreement: AgreementResponseDTO;
  onBack: () => void;
}) {
  return (
    <div className="animate-fade-in max-w-3xl">
      <Button variant="ghost" size="sm" className="mb-3" onClick={onBack}>
        <ArrowLeft /> Back to agreements
      </Button>
      <PageHeader
        title={`Lease for flat #${agreement.flatId}`}
        description={`Tenant ${agreement.tenantId}`}
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

          {agreement.status === "SIGNED" && agreement.signatureData && (
            <div className="mt-6 rounded-xl border bg-success/5 border-success/30 p-5">
              <div className="flex items-start gap-3">
                <CheckCircle2 className="size-5 text-success mt-0.5 shrink-0" />
                <div className="flex-1">
                  <p className="font-semibold">
                    Signed by tenant on {formatDate(agreement.signedAt)}
                  </p>
                </div>
              </div>
              <div className="mt-4">
                <p className="text-xs uppercase tracking-wider text-muted-foreground mb-1.5">
                  Tenant signature
                </p>
                <div className="rounded-lg border bg-white p-2 inline-block">
                  <img
                    src={agreement.signatureData}
                    alt="Tenant signature"
                    className="max-h-24"
                  />
                </div>
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
