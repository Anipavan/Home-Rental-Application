import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CheckCircle2,
  Download,
  FileText,
  Loader2,
  ScrollText,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { agreementsApi } from "@/lib/api/agreements";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  SignaturePad,
  SignaturePadClearButton,
  type SignaturePadHandle,
} from "@/components/ui/signature-pad";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, formatINR } from "@/lib/utils";
import type {
  AgreementResponseDTO,
  AgreementStatus,
} from "@/types/api";

export function TenantLeasePage() {
  const { authUserId } = useAuthStore();
  const q = useQuery({
    queryKey: ["my-agreements", authUserId],
    queryFn: () => agreementsApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  // Dedup defensively: backend can leave stale PENDING_SIGNATURE rows when a
  // tenant is reassigned to the same flat. Keep the most recent agreement per
  // flatId, which fixes the "lease shown twice" bug (B6).
  const agreements = q.data ?? [];
  const dedupedByFlat = Object.values(
    agreements.reduce<Record<string, AgreementResponseDTO>>((acc, a) => {
      const existing = acc[a.flatId];
      if (
        !existing ||
        new Date(a.updatedAt ?? a.createdAt ?? 0).getTime() >
          new Date(existing.updatedAt ?? existing.createdAt ?? 0).getTime()
      ) {
        acc[a.flatId] = a;
      }
      return acc;
    }, {}),
  );
  // Surface the active one (PENDING_SIGNATURE > SIGNED > REJECTED) at the top.
  const ordered = [...dedupedByFlat].sort(
    (a, b) => statusRank(a.status) - statusRank(b.status),
  );

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Lease agreement"
        description="Read your lease, sign it digitally, or raise a concern."
      />

      {q.isLoading && <Skeleton className="h-64 rounded-2xl" />}

      {!q.isLoading && ordered.length === 0 && (
        <Card className="p-12 text-center">
          <ScrollText className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            No agreement yet
          </p>
          <p className="text-muted-foreground text-sm mt-1 max-w-sm mx-auto">
            Once your owner assigns you to a flat, the lease will appear here for
            you to review and sign.
          </p>
        </Card>
      )}

      <div className="space-y-6">
        {ordered.map((a) => (
          <AgreementCard key={a.id} agreement={a} />
        ))}
      </div>
    </div>
  );
}

function statusRank(s: AgreementStatus): number {
  if (s === "PENDING_SIGNATURE") return 0;
  if (s === "SIGNED") return 1;
  return 2;
}

function AgreementCard({ agreement }: { agreement: AgreementResponseDTO }) {
  const qc = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [signatureEmpty, setSignatureEmpty] = useState(true);
  const padRef = useRef<SignaturePadHandle>(null);

  const signMutation = useMutation({
    mutationFn: (signatureData: string) =>
      agreementsApi.sign(agreement.id, { signatureData }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-agreements"] });
      toast({
        title: "Signed",
        description: "Your lease is now active.",
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't submit signature",
        description: extractErrorMessage(e),
      }),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) =>
      agreementsApi.reject(agreement.id, { reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-agreements"] });
      toast({
        title: "Rejected",
        description: "We've notified your owner.",
      });
      setRejectOpen(false);
      setRejectReason("");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't reject",
        description: extractErrorMessage(e),
      }),
  });

  function onSign() {
    const data = padRef.current?.toDataUrl();
    if (!data || padRef.current?.isEmpty()) {
      toast({
        variant: "destructive",
        title: "Signature required",
        description: "Please sign in the box first.",
      });
      return;
    }
    signMutation.mutate(data);
  }

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <div className="flex items-start justify-between gap-3 mb-5">
          <div className="flex items-start gap-3">
            <div className="size-11 rounded-xl bg-primary/10 text-primary grid place-items-center shrink-0">
              <FileText className="size-5" />
            </div>
            <div>
              <h2 className="font-display text-xl font-semibold">
                Residential Lease Agreement
              </h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Flat #{agreement.flatId} · Created{" "}
                {formatDate(agreement.createdAt)}
              </p>
            </div>
          </div>
          <StatusBadge status={agreement.status} />
        </div>

        <div className="grid sm:grid-cols-3 gap-4 text-sm mb-6">
          <KV label="Monthly rent" value={formatINR(agreement.rentAmount)} />
          <KV label="Lease starts" value={formatDate(agreement.leaseStartDate)} />
          <KV label="Lease ends" value={formatDate(agreement.leaseEndDate)} />
        </div>

        <Separator className="my-5" />

        {/*
          Inline terms are only shown while the tenant has yet to sign — once
          SIGNED, the full deed is available as a PDF download (rendered by
          AgreementPdfGenerator). This both fixes the "shown twice"
          perception and steers users to the canonical signed copy.
        */}
        {agreement.status === "PENDING_SIGNATURE" && (
          <>
            <h3 className="font-display font-semibold text-sm uppercase tracking-wider text-muted-foreground mb-2">
              Terms &amp; Conditions
            </h3>
            <div className="rounded-xl border bg-secondary/30 p-5 max-h-72 overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed">
              {agreement.terms?.trim() || defaultTerms(agreement)}
            </div>
          </>
        )}

        {agreement.status === "PENDING_SIGNATURE" && (
          <div className="mt-6 space-y-4">
            <div>
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium">Your signature</p>
                <SignaturePadClearButton
                  onClick={() => padRef.current?.clear()}
                  disabled={signatureEmpty}
                />
              </div>
              <SignaturePad
                ref={padRef}
                onChange={(empty) => setSignatureEmpty(empty)}
              />
              <p className="text-xs text-muted-foreground mt-2 flex items-center gap-1.5">
                <ShieldCheck className="size-3.5" />
                Your signature is encrypted and bound to this agreement only.
              </p>
            </div>

            <div className="flex flex-col-reverse sm:flex-row gap-2 sm:justify-end pt-2">
              <Button
                variant="outline"
                onClick={() => setRejectOpen(true)}
                disabled={signMutation.isPending}
              >
                <XCircle /> Reject
              </Button>
              <Button
                variant="gradient"
                onClick={onSign}
                disabled={signMutation.isPending || signatureEmpty}
              >
                {signMutation.isPending && <Loader2 className="animate-spin" />}
                <CheckCircle2 /> Sign &amp; accept
              </Button>
            </div>
          </div>
        )}

        {agreement.status === "SIGNED" && (
          <div className="mt-6 rounded-xl border bg-success/5 border-success/30 p-5">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="size-5 text-success mt-0.5 shrink-0" />
              <div className="flex-1">
                <p className="font-semibold">
                  Signed on {formatDate(agreement.signedAt)}
                </p>
                <p className="text-sm text-muted-foreground mt-0.5">
                  Your lease is active. The signed deed is available as a PDF.
                </p>
              </div>
            </div>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              {agreement.hasDocument ? (
                <DownloadDeedButton agreementId={agreement.id} />
              ) : (
                <p className="text-xs text-muted-foreground italic">
                  PDF is being prepared — refresh in a moment.
                </p>
              )}
              {agreement.signatureData && (
                <div className="rounded-lg border bg-white p-2">
                  <img
                    src={agreement.signatureData}
                    alt="Your signature"
                    className="max-h-12"
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
                  Rejected on {formatDate(agreement.rejectedAt)}
                </p>
                {agreement.rejectionReason && (
                  <>
                    <p className="text-xs uppercase tracking-wider text-muted-foreground mt-3 mb-1">
                      Reason
                    </p>
                    <p className="text-sm">{agreement.rejectionReason}</p>
                  </>
                )}
                <p className="text-sm text-muted-foreground mt-3">
                  Your owner will reach out to discuss the changes.
                </p>
              </div>
            </div>
          </div>
        )}
      </CardContent>

      <Dialog
        open={rejectOpen}
        onOpenChange={(o) => {
          if (!o) {
            setRejectOpen(false);
            setRejectReason("");
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject this agreement</DialogTitle>
            <DialogDescription>
              Tell your owner what's blocking you from signing — they'll see this
              note and can revise the terms.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="e.g. The notice period clause needs to change to 1 month, not 3."
            rows={4}
            maxLength={500}
          />
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => {
                setRejectOpen(false);
                setRejectReason("");
              }}
              disabled={rejectMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => rejectMutation.mutate(rejectReason.trim())}
              disabled={
                rejectMutation.isPending || rejectReason.trim().length === 0
              }
            >
              {rejectMutation.isPending && (
                <Loader2 className="animate-spin" />
              )}
              Reject agreement
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function StatusBadge({ status }: { status: AgreementStatus }) {
  if (status === "SIGNED") return <Badge variant="success">Signed</Badge>;
  if (status === "REJECTED") return <Badge variant="destructive">Rejected</Badge>;
  return <Badge variant="warning">Awaiting signature</Badge>;
}

/**
 * Triggers the GET /properties/agreements/{id}/document blob endpoint and
 * pushes the PDF to a hidden anchor for download. The browser handles the
 * filename via the Content-Disposition header set by the backend.
 */
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

function KV({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium mt-0.5">{value}</p>
    </div>
  );
}

function defaultTerms(a: AgreementResponseDTO): string {
  return `1. PARTIES
   This Residential Lease Agreement is entered into between the Owner (id ${a.ownerId}) and the Tenant (id ${a.tenantId}) for the residential premises identified as flat ${a.flatId}.

2. TERM
   The lease begins on ${a.leaseStartDate} and ends on ${a.leaseEndDate}, unless renewed or terminated as per the terms below.

3. RENT
   Tenant agrees to pay ${a.rentAmount ? `INR ${a.rentAmount}` : "the rent amount specified"} per month, due on the 5th of each month.

4. SECURITY DEPOSIT
   Tenant has paid a refundable deposit equal to two months of rent, returnable within 30 days of vacating the premises after deduction for damages, if any.

5. UTILITIES
   Tenant is responsible for electricity, water, gas, internet, and other consumption-based utilities, unless otherwise agreed in writing.

6. MAINTENANCE
   Routine wear-and-tear repairs shall be the Owner's responsibility. Damage caused by negligence shall be borne by the Tenant.

7. SUB-LETTING
   The premises may not be sub-let, in whole or in part, without the Owner's prior written consent.

8. TERMINATION
   Either party may terminate this agreement with one (1) month's written notice. Termination by the Owner without cause within the first six months requires a refund of one month's rent.

9. GOVERNING LAW
   This agreement is governed by the laws of India and any disputes shall be settled in the courts of the jurisdiction where the premises are located.`;
}
