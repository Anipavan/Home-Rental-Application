import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  FileText,
  Download,
  Sparkles,
  ShieldCheck,
  Trash2,
  AlertCircle,
  ScrollText,
  Receipt,
  Loader2,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { documentsApi } from "@/lib/api/documents";
import { usersApi } from "@/lib/api/users";
import { leaseApi } from "@/lib/api/lease";
import { paymentsApi } from "@/lib/api/payments";
import { formatDate, formatINR } from "@/lib/utils";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileUpload } from "@/components/ui/file-upload";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { DocumentResponse, DocumentType, OcrStatus } from "@/types/api";

const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
  { value: "AADHAAR", label: "Aadhaar card" },
  { value: "PAN", label: "PAN card" },
  { value: "AGREEMENT", label: "Rental agreement" },
  { value: "PHOTO", label: "Profile photo" },
  { value: "OTHER", label: "Other" },
];

export function DocumentsPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [docType, setDocType] = useState<DocumentType>("AADHAAR");

  const meQ = useQuery({
    queryKey: ["me", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId,
  });
  const userId = meQ.data ? String(meQ.data.id) : undefined;

  const listQ = useQuery({
    queryKey: ["documents", userId],
    queryFn: () => documentsApi.byUser(userId!),
    enabled: !!userId,
  });

  // Vault expansion — pull lease docs + payment receipts so this page
  // becomes the single place a tenant looks for "where's my lease /
  // where's last month's receipt", instead of jumping between three
  // screens. Both query on the auth-user-id (which matches what the
  // payment-service stores as tenantId) so a single id keyed every
  // surface that the tenant has touched.
  const leasesQ = useQuery({
    queryKey: ["my-leases", authUserId],
    queryFn: () => leaseApi.byTenant(authUserId!),
    enabled: !!authUserId,
    staleTime: 60_000,
    retry: false,
  });
  const paymentsQ = useQuery({
    queryKey: ["my-payments", authUserId],
    queryFn: () => paymentsApi.byTenant(authUserId!),
    enabled: !!authUserId,
    staleTime: 60_000,
    retry: false,
  });

  // Compute which document types the user has ALREADY uploaded with a
  // non-rejected status. Those types disappear from the upload
  // dropdown until the owner rejects them (the dropdown shouldn't
  // offer "Aadhaar" again when the existing Aadhaar is still pending
  // review or already approved). When a doc gets rejected, that
  // type's slot opens up again — the tenant can re-upload a
  // corrected copy.
  //
  // OTHER is intentionally exempt — it's a catch-all for non-standard
  // documents (driving licence, utility bill, rental agreement
  // amendments, …) so multiple OTHER uploads make sense.
  //
  // "Used" check excludes REJECTED docs so the slot opens again on
  // rejection. Soft-deleted docs are filtered server-side by
  // /documents/user/{id}, so the response we get is already
  // delete-free — we don't need to re-check the flag here.
  const lockedTypes = useMemo(() => {
    const set = new Set<DocumentType>();
    for (const d of listQ.data ?? []) {
      if (d.verificationStatus === "REJECTED") continue;
      if (d.documentType === "OTHER") continue;
      set.add(d.documentType);
    }
    return set;
  }, [listQ.data]);

  // Filter the dropdown options to omit already-uploaded types.
  // We always keep the current selection visible — otherwise the
  // Radix Select would render with no value and look broken.
  const availableTypes = DOCUMENT_TYPES.filter(
    (t) => !lockedTypes.has(t.value) || t.value === docType,
  );

  // If the current selection just got locked by an upload completing,
  // bump the dropdown to the first remaining available type so the
  // user doesn't see a stale "Aadhaar" pre-selected when Aadhaar is
  // no longer in the menu.
  useEffect(() => {
    if (!lockedTypes.has(docType)) return;
    const next = DOCUMENT_TYPES.find((t) => !lockedTypes.has(t.value));
    if (next && next.value !== docType) {
      setDocType(next.value);
    }
  }, [lockedTypes, docType]);

  const uploadM = useMutation({
    mutationFn: (file: File) =>
      documentsApi.upload(userId!, docType, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["documents", userId] });
      toast({ title: "Document uploaded" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Upload failed",
        description: extractErrorMessage(e),
      }),
  });

  const extractM = useMutation({
    mutationFn: (id: string) => documentsApi.extract(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["documents", userId] });
      toast({ title: "OCR extraction complete" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Extraction failed",
        description: extractErrorMessage(e),
      }),
  });

  const removeM = useMutation({
    mutationFn: (id: string) => documentsApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["documents", userId] });
      toast({ title: "Document deleted" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Delete failed",
        description: extractErrorMessage(e),
      }),
  });

  async function onDownload(d: DocumentResponse) {
    try {
      const url = await documentsApi.getDownloadUrl(d.id);
      window.open(url.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Download failed",
        description: extractErrorMessage(e),
      });
    }
  }

  // Document Service uploads are keyed by the user-service primary id.
  // When `byAuthId` returns nothing we don't have a userId to attach to,
  // so the upload box was previously rendered as a silently-disabled
  // <FileUpload>. Users couldn't tell why nothing happened — they just
  // saw the upload not work. Surface an explicit banner instead.
  const profileMissing = !!authUserId && !meQ.isLoading && !meQ.data;

  return (
    <div className="animate-fade-in max-w-4xl">
      <PageHeader
        title="My documents"
        description="Aadhaar, PAN, rental agreements — all encrypted at rest, only you decide who sees what."
      />

      {profileMissing && (
        <Card className="mb-6 border-warning/40 bg-warning/5">
          <CardContent className="p-5 flex items-start gap-3">
            <AlertCircle className="size-5 text-warning shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-semibold text-sm">
                Finish setting up your profile first
              </p>
              <p className="text-sm text-muted-foreground mt-0.5">
                Documents are linked to your profile. Complete the short
                profile form to enable uploads.
              </p>
              <Button
                asChild
                size="sm"
                variant="gradient"
                className="mt-3"
              >
                <Link to="/app/profile">Go to profile</Link>
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      <Card className="mb-6">
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg flex items-center gap-2">
            <ShieldCheck className="size-4 text-primary" /> Upload a document
          </h3>
          <p className="text-sm text-muted-foreground mt-1">
            PDF, PNG, or JPEG — max 10 MB.
          </p>
          <div className="mt-5 grid sm:grid-cols-[200px_1fr] gap-4 items-start">
            <div>
              <label className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Document type
              </label>
              <Select value={docType} onValueChange={(v) => setDocType(v as DocumentType)}>
                <SelectTrigger className="mt-1.5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {availableTypes.map((t) => (
                    <SelectItem key={t.value} value={t.value}>
                      {t.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {/* Helper line — tells the tenant why a previously-
                  available option disappeared. The dropdown silently
                  hiding an option would be confusing without this
                  one-line explanation. */}
              {lockedTypes.size > 0 && (
                <p className="text-[11px] text-muted-foreground mt-1">
                  Already uploaded types are hidden. Rejected ones
                  reappear so you can re-upload.
                </p>
              )}
            </div>
            <FileUpload
              accept="application/pdf,image/png,image/jpeg,image/jpg"
              maxSizeMB={10}
              loading={uploadM.isPending}
              onFiles={async (files) => {
                if (files[0]) await uploadM.mutateAsync(files[0]);
              }}
              hint={
                profileMissing
                  ? "Complete your profile first"
                  : `Uploading as ${docType}`
              }
              disabled={!userId}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg">
            Your documents{listQ.data ? ` (${listQ.data.length})` : ""}
          </h3>

          {listQ.isLoading ? (
            <div className="mt-4 space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-20 rounded-xl" />
              ))}
            </div>
          ) : !listQ.data || listQ.data.length === 0 ? (
            <p className="mt-6 text-sm text-muted-foreground">
              No documents yet. Upload your Aadhaar to get started.
            </p>
          ) : (
            <div className="mt-4 space-y-3">
              {listQ.data.map((d) => (
                <DocumentRow
                  key={d.id}
                  doc={d}
                  onExtract={() => extractM.mutate(d.id)}
                  onDownload={() => onDownload(d)}
                  onDelete={() => removeM.mutate(d.id)}
                  busy={extractM.isPending && extractM.variables === d.id}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* ──── Lease agreements ──── */}
      <LeaseVaultSection leases={leasesQ.data ?? []} loading={leasesQ.isLoading} />

      {/* ──── Payment receipts (PAID only) ──── */}
      <ReceiptsVaultSection
        payments={paymentsQ.data ?? []}
        loading={paymentsQ.isLoading}
      />
    </div>
  );
}

/* ──────────────────────── Lease vault section ──────────────────────── */

/**
 * Lease agreements panel — shows every lease this tenant has been
 * party to, with a download button per lease. The lease-service PDF
 * carries the rendered terms + RERA stamp + e-signatures, so a
 * download is exactly what an audit (or a future landlord) would
 * want to see.
 *
 * <p>Hidden entirely when the tenant has no leases — a brand-new
 * user shouldn't see "0 leases" hollow space.
 */
function LeaseVaultSection({
  leases,
  loading,
}: {
  leases: Array<{
    id: string;
    flatId: string;
    leaseStartDate?: string;
    leaseEndDate?: string;
    monthlyRent?: number;
    status?: string;
  }>;
  loading: boolean;
}) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  async function handleDownload(leaseId: string) {
    setDownloadingId(leaseId);
    try {
      const blob = await leaseApi.downloadDocument(leaseId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lease-${leaseId.slice(0, 8)}.pdf`;
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
      setDownloadingId(null);
    }
  }

  if (!loading && leases.length === 0) return null;

  return (
    <Card className="mt-6">
      <CardContent className="p-6 sm:p-8">
        <h3 className="font-display font-semibold text-lg flex items-center gap-2">
          <ScrollText className="size-4 text-primary" />
          Lease agreements {leases.length > 0 && `(${leases.length})`}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          Every lease you've signed, with the original PDF on file.
        </p>

        {loading ? (
          <div className="mt-4 space-y-3">
            {[1, 2].map((i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
            ))}
          </div>
        ) : (
          <div className="mt-4 space-y-3">
            {leases.map((l) => (
              <div
                key={l.id}
                className="rounded-xl border bg-secondary/30 p-4 flex flex-wrap items-center gap-4"
              >
                <div className="size-12 rounded-lg bg-background grid place-items-center border shrink-0">
                  <ScrollText className="size-5 text-primary" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="font-medium text-sm">
                      Lease {l.id.slice(0, 8)}…
                    </p>
                    {l.status && (
                      <Badge
                        variant={
                          l.status === "ACTIVE"
                            ? "success"
                            : l.status === "TERMINATED" || l.status === "EXPIRED"
                              ? "secondary"
                              : "warning"
                        }
                        className="text-[10px]"
                      >
                        {l.status}
                      </Badge>
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {l.leaseStartDate
                      ? `From ${formatDate(l.leaseStartDate)}`
                      : "Start date not set"}
                    {l.leaseEndDate
                      ? ` · until ${formatDate(l.leaseEndDate)}`
                      : ""}
                    {l.monthlyRent
                      ? ` · ${formatINR(l.monthlyRent)}/mo`
                      : ""}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => handleDownload(l.id)}
                  disabled={downloadingId === l.id}
                >
                  {downloadingId === l.id ? (
                    <Loader2 className="animate-spin" />
                  ) : (
                    <Download />
                  )}
                  Download
                </Button>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/* ──────────────────────── Receipts vault section ──────────────────────── */

/**
 * Payment receipts panel — every PAID payment becomes a downloadable
 * receipt PDF. Carries the platform's GST-compliant invoice format
 * with payment date, transaction id, amount, late fee breakdown.
 *
 * <p>Hidden when no PAID payments yet (new tenants pre-first-rent).
 */
function ReceiptsVaultSection({
  payments,
  loading,
}: {
  payments: Array<{
    id: string;
    paymentDate?: string;
    dueDate: string;
    amount: number;
    totalAmount?: number;
    status: string;
    transactionId?: string;
  }>;
  loading: boolean;
}) {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  // Only PAID payments have a receipt PDF — the invoice exists earlier
  // (at issuance) but the receipt is generated on settlement.
  const paid = payments
    .filter((p) => p.status === "PAID")
    .sort((a, b) => {
      const aD = a.paymentDate ?? a.dueDate ?? "";
      const bD = b.paymentDate ?? b.dueDate ?? "";
      return bD.localeCompare(aD);
    });

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

  if (!loading && paid.length === 0) return null;

  return (
    <Card className="mt-6">
      <CardContent className="p-6 sm:p-8">
        <h3 className="font-display font-semibold text-lg flex items-center gap-2">
          <Receipt className="size-4 text-primary" />
          Payment receipts {paid.length > 0 && `(${paid.length})`}
        </h3>
        <p className="text-sm text-muted-foreground mt-1">
          GST-compliant receipts for every rent payment that's settled.
        </p>

        {loading ? (
          <div className="mt-4 space-y-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 rounded-xl" />
            ))}
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
                    <span className="text-xs text-muted-foreground">
                      paid {formatDate(p.paymentDate ?? p.dueDate)}
                    </span>
                  </div>
                  {p.transactionId && (
                    <p className="text-[11px] text-muted-foreground font-mono mt-0.5 truncate">
                      Txn {p.transactionId}
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

function DocumentRow({
  doc,
  onExtract,
  onDownload,
  onDelete,
  busy,
}: {
  doc: DocumentResponse;
  onExtract: () => void;
  onDownload: () => void;
  onDelete: () => void;
  busy: boolean;
}) {
  return (
    <div className="rounded-xl border bg-secondary/30 p-4 flex flex-wrap items-center gap-4">
      <div className="size-12 rounded-lg bg-background grid place-items-center border shrink-0">
        <FileText className="size-5 text-muted-foreground" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <p className="font-medium text-sm">
            {doc.originalFilename ?? doc.id}
          </p>
          <Badge variant="secondary" className="text-[10px]">
            {doc.documentType}
          </Badge>
          <OcrBadge status={doc.ocrStatus} />
          {/* Issue #3 — owner approval status is independent of OCR
              status. Render it alongside so the tenant sees both
              "OCR done" (system extracted text) and "Approved" /
              "Rejected" / "Pending review" (owner decision). */}
          <VerificationBadge status={doc.verificationStatus} />
          {doc.fraudFlag && (
            <Badge variant="destructive" className="text-[10px]">
              Flagged
            </Badge>
          )}
        </div>
        {/* When rejected, surface the owner's reason directly under
            the title — tenant needs to know what to fix before
            re-uploading. */}
        {doc.verificationStatus === "REJECTED" && doc.rejectionReason && (
          <p className="text-xs text-destructive mt-1">
            <span className="font-semibold">Owner's note:</span>{" "}
            {doc.rejectionReason}
          </p>
        )}
        <p className="text-xs text-muted-foreground mt-0.5">
          {doc.contentType ?? "—"}
          {doc.fileSizeBytes ? ` · ${formatSize(doc.fileSizeBytes)}` : ""}
          {doc.uploadedAt
            ? ` · uploaded ${new Date(doc.uploadedAt).toLocaleDateString()}`
            : ""}
        </p>
        {doc.extractedData && Object.keys(doc.extractedData).length > 0 && (
          <ExtractedFields data={doc.extractedData} />
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="ghost" onClick={onDownload}>
          <Download /> Download
        </Button>
        {doc.ocrStatus !== "DONE" && doc.ocrStatus !== "PROCESSING" && (
          <Button size="sm" variant="outline" onClick={onExtract} disabled={busy}>
            <Sparkles /> {busy ? "Extracting…" : "Run OCR"}
          </Button>
        )}
        <Button size="sm" variant="ghost" onClick={onDelete}>
          <Trash2 className="text-destructive" />
        </Button>
      </div>
    </div>
  );
}

function ExtractedFields({ data }: { data: Record<string, string> }) {
  const entries = Object.entries(data).slice(0, 4);
  if (entries.length === 0) return null;
  return (
    <div className="mt-2 grid sm:grid-cols-2 gap-x-4 gap-y-1 text-xs">
      {entries.map(([k, v]) => (
        <div key={k}>
          <span className="text-muted-foreground">{k}: </span>
          <span className="font-medium">{v}</span>
        </div>
      ))}
    </div>
  );
}

function OcrBadge({ status }: { status: OcrStatus }) {
  switch (status) {
    case "DONE":
      return <Badge variant="success" className="text-[10px]">OCR done</Badge>;
    case "PROCESSING":
      return <Badge variant="warning" className="text-[10px]">OCR running</Badge>;
    case "FAILED":
      return <Badge variant="destructive" className="text-[10px]">OCR failed</Badge>;
    default:
      return <Badge variant="secondary" className="text-[10px]">OCR queued</Badge>;
  }
}

/**
 * Issue #3 — owner approval status for a tenant-uploaded document.
 * Independent of OCR status (the existing OcrBadge above). PENDING
 * means the owner hasn't made a decision yet; APPROVED / REJECTED
 * reflect the decision the owner made on their tenant-detail page.
 */
function VerificationBadge({
  status,
}: {
  status?: "PENDING" | "APPROVED" | "REJECTED";
}) {
  if (status === "APPROVED") {
    return (
      <Badge variant="success" className="text-[10px]">
        Approved
      </Badge>
    );
  }
  if (status === "REJECTED") {
    return (
      <Badge variant="destructive" className="text-[10px]">
        Rejected
      </Badge>
    );
  }
  // Default + PENDING → "Pending review" (the documents page used to
  // render plain "Pending" only from OcrBadge's default branch, which
  // conflated OCR-not-started with owner-not-decided).
  return (
    <Badge variant="warning" className="text-[10px]">
      Pending review
    </Badge>
  );
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
