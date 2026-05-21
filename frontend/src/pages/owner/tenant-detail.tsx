import { Fragment, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Mail,
  Phone,
  Calendar,
  ScrollText,
  Wrench,
  FileText,
  Receipt,
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  Building2,
  Check,
  X,
  Loader2,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { paymentsApi } from "@/lib/api/payments";
import { maintenanceApi } from "@/lib/api/maintenance";
import { agreementsApi } from "@/lib/api/agreements";
import { documentsApi } from "@/lib/api/documents";
import { propertiesApi } from "@/lib/api/properties";
import { kycApi } from "@/lib/api/kyc";
import { isKycDisabled } from "@/lib/feature-flags";
import { useUserByAuth } from "@/hooks/use-user-by-auth";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Separator } from "@/components/ui/separator";
import { PageHeader } from "@/components/layout/page-header";
import { formatDate, formatINR, initials, normalizeDocUrl } from "@/lib/utils";
import type {
  DocumentResponse,
  KycStatus,
  PaymentResponse,
  PaymentStatus,
} from "@/types/api";

/**
 * Owner-side tenant deep-dive at /owner/tenants/:tenantId.
 *
 * <p>The route param is the tenant's authUserId — same id stored on
 * Flat.tenantId. Five data sources are pulled in parallel:
 *  - User Service: profile (name, email, phone, KYC status fields)
 *  - Property Service: tenant's flat(s) for current-lease summary
 *  - Payment Service: payment history (paid / due / overdue)
 *  - Maintenance Service: ticket history
 *  - Document Service: documents on file
 *  - KYC Service: latest verification status
 *
 * <p>Loading is non-blocking - each section renders skeletons until its
 * own query resolves, so the user sees something useful within 200 ms.
 */
export function TenantDetailPage() {
  const { tenantId } = useParams<{ tenantId: string }>();
  const authUserId = tenantId ?? "";

  // Signed-in OWNER's authUserId — distinct from `authUserId` above
  // (which is the tenant being viewed). We need both: the route param
  // identifies whose detail to render, the auth store identifies who
  // the API call is FROM.
  const { authUserId: viewerAuthUserId } = useAuthStore();

  const userQ = useUserByAuth(authUserId);
  const userServiceId = userQ.user ? String(userQ.user.id) : undefined;

  const flatsQ = useQuery({
    queryKey: ["tenant-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId),
    enabled: !!authUserId,
  });

  // /payments/tenant/{tenantId} is gated to self-or-admin server-side
  // — calling it as the OWNER gets a 403, so the page silently
  // rendered Paid/Overdue/Upcoming = 0 for every tenant. Switch to
  // /payments/owner/{ownerId} (which the signed-in owner CAN call)
  // and filter to this tenant on the client. That endpoint returns
  // every payment across the owner's portfolio so the slice we need
  // is guaranteed to be present.
  //
  // We share the cache with Dashboard + Tenants + Payments by using
  // the same ["owner-payments", viewerAuthUserId] key. The
  // per-tenant slice is computed in a useMemo below from the full
  // list — so when any of the other pages invalidates the cache
  // (e.g. owner marks rent as paid), this tenant card refreshes too.
  const paymentsQ = useQuery({
    queryKey: ["owner-payments", viewerAuthUserId],
    queryFn: () => paymentsApi.byOwner(viewerAuthUserId!),
    enabled: !!viewerAuthUserId,
    staleTime: 15_000,
    refetchOnWindowFocus: true,
    select: (all) => all.filter((p) => p.tenantId === authUserId),
  });

  const maintenanceQ = useQuery({
    queryKey: ["tenant-maintenance", authUserId],
    queryFn: () => maintenanceApi.byTenant(authUserId),
    enabled: !!authUserId,
  });

  const agreementsQ = useQuery({
    queryKey: ["tenant-agreements", authUserId],
    queryFn: () => agreementsApi.byTenant(authUserId),
    enabled: !!authUserId,
  });

  const documentsQ = useQuery({
    queryKey: ["tenant-documents", userServiceId],
    queryFn: () => documentsApi.byUser(userServiceId!),
    enabled: !!userServiceId,
  });

  // Skip the KYC lookup entirely when the feature is paused platform-
  // wide. Otherwise the owner page hammers a service we've nominally
  // turned off and renders a "loading" badge forever (or worse, stale
  // data from before the pause).
  const kycPaused = isKycDisabled();
  const kycQ = useQuery({
    // KYC service keys records on authUserId (matches the tenant-side KYC
    // page), not on the user-service profile id. Querying with the route
    // param directly is the canonical lookup.
    queryKey: ["tenant-kyc", authUserId],
    queryFn: () => kycApi.status(authUserId),
    enabled: !!authUserId && !kycPaused,
    retry: false,
  });

  const flat = flatsQ.data?.[0];
  const activeAgreement = useMemo(
    () =>
      agreementsQ.data?.find((a) => a.status === "SIGNED") ??
      agreementsQ.data?.[0],
    [agreementsQ.data],
  );

  const paymentTotals = useMemo(() => totals(paymentsQ.data ?? []), [paymentsQ.data]);

  return (
    <div className="animate-fade-in max-w-5xl">
      <Button asChild variant="ghost" size="sm" className="mb-3">
        <Link to="/owner/tenants">
          <ArrowLeft /> All tenants
        </Link>
      </Button>

      {/* Header — name, contact actions, KYC badge.
          The hook returns at least the auth-tier fallback (userName+email)
          when User Service has no profile row, so userQ.user is almost
          never undefined. Even when it IS, we render the fallback header
          (id slice + flat info) instead of an unhelpful error message —
          the rest of the page (lease, payments, maintenance) is keyed
          off authUserId and still loads. */}
      <Card className="mb-6">
        <CardContent className="p-6 sm:p-8">
          {userQ.isLoading ? (
            <Skeleton className="h-24" />
          ) : (
            <div className="flex items-start gap-5 flex-wrap">
              <Avatar className="size-20">
                {userQ.user?.profilePictureUrl && (
                  <AvatarImage src={normalizeDocUrl(userQ.user.profilePictureUrl)} />
                )}
                <AvatarFallback className="text-2xl">
                  {initials(
                    userQ.fullName ?? `T${authUserId.slice(0, 2)}`,
                  )}
                </AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <PageHeader
                  className="mb-2"
                  title={
                    userQ.fullName ||
                    `Tenant ${authUserId.slice(0, 8)}…`
                  }
                  description={
                    flat
                      ? `${(flat as { buildingName?: string }).buildingName ?? "Flat"} · ${flat.flatNumber}`
                      : "No active lease"
                  }
                />
                {!kycPaused && (
                  <KycBadge status={kycQ.data?.verificationStatus} />
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {userQ.user?.phone && (
                  <Button asChild variant="outline" size="sm">
                    <a href={`tel:${userQ.user.phone}`}>
                      <Phone /> Call
                    </a>
                  </Button>
                )}
                {userQ.user?.email && (
                  <Button asChild variant="outline" size="sm">
                    <a href={`mailto:${userQ.user.email}`}>
                      <Mail /> Email
                    </a>
                  </Button>
                )}
              </div>
            </div>
          )}

          {userQ.user && (
            <div className="grid sm:grid-cols-3 gap-4 mt-5 text-sm">
              <KV
                label="Phone"
                value={userQ.user.phone ?? "—"}
                Icon={Phone}
              />
              <KV
                label="Email"
                value={userQ.user.email ?? "—"}
                Icon={Mail}
              />
              <KV
                label="Address"
                value={userQ.user.address ?? "—"}
                Icon={Building2}
              />
            </div>
          )}
          {!userQ.isLoading && !userQ.user && (
            <p className="text-xs text-muted-foreground mt-4">
              This tenant hasn't completed their profile yet — name, phone,
              and address will appear here once they do.
            </p>
          )}
        </CardContent>
      </Card>

      <div className="grid lg:grid-cols-2 gap-6">
        {/* Active lease */}
        <Card>
          <CardContent className="p-6">
            <Section title="Active lease" Icon={ScrollText} />
            {agreementsQ.isLoading ? (
              <Skeleton className="h-20 mt-3" />
            ) : !activeAgreement ? (
              <p className="text-sm text-muted-foreground mt-3">
                No agreement on file yet.
              </p>
            ) : (
              <div className="mt-3 space-y-3 text-sm">
                <div className="flex items-center gap-2 flex-wrap">
                  <Badge
                    variant={
                      activeAgreement.status === "SIGNED"
                        ? "success"
                        : activeAgreement.status === "REJECTED"
                          ? "destructive"
                          : "warning"
                    }
                  >
                    {activeAgreement.status.replace("_", " ")}
                  </Badge>
                  <p className="text-xs text-muted-foreground">
                    Created {formatDate(activeAgreement.createdAt)}
                  </p>
                </div>
                <div className="grid grid-cols-3 gap-3">
                  <KVCompact
                    label="Rent"
                    value={formatINR(activeAgreement.rentAmount)}
                  />
                  <KVCompact
                    label="Starts"
                    value={formatDate(activeAgreement.leaseStartDate) ?? "—"}
                  />
                  <KVCompact
                    label="Ends"
                    value={formatDate(activeAgreement.leaseEndDate) ?? "—"}
                  />
                </div>
                <Button asChild variant="outline" size="sm">
                  <Link to="/owner/agreements">
                    <FileText /> View agreement
                  </Link>
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Payment summary */}
        <Card>
          <CardContent className="p-6">
            <Section title="Payments" Icon={Receipt} />
            {paymentsQ.isLoading ? (
              <Skeleton className="h-20 mt-3" />
            ) : (
              <>
                <div className="grid grid-cols-3 gap-3 mt-3">
                  <Stat
                    label="Paid"
                    value={formatINR(paymentTotals.paid)}
                    accent="success"
                  />
                  <Stat
                    label="Overdue"
                    value={formatINR(paymentTotals.overdue)}
                    accent={paymentTotals.overdue > 0 ? "destructive" : "muted"}
                  />
                  <Stat
                    label="Upcoming"
                    value={formatINR(paymentTotals.upcoming)}
                    accent="muted"
                  />
                </div>
                {(paymentsQ.data?.length ?? 0) > 0 && (
                  <PaymentsTable payments={paymentsQ.data!} />
                )}
              </>
            )}
          </CardContent>
        </Card>

        {/* Maintenance tickets */}
        <Card>
          <CardContent className="p-6">
            <Section title="Maintenance tickets" Icon={Wrench} />
            {maintenanceQ.isLoading ? (
              <Skeleton className="h-20 mt-3" />
            ) : (maintenanceQ.data?.length ?? 0) === 0 ? (
              <p className="text-sm text-muted-foreground mt-3">
                No maintenance requests from this tenant.
              </p>
            ) : (
              <ul className="mt-3 space-y-2">
                {maintenanceQ.data!.slice(0, 5).map((m) => (
                  <li
                    key={m.id}
                    className="rounded-lg border bg-secondary/30 p-3 flex items-start gap-3"
                  >
                    <div
                      className={`size-2 rounded-full mt-2 shrink-0 ${
                        m.status === "RESOLVED" || m.status === "CLOSED"
                          ? "bg-emerald-500"
                          : m.priority === "CRITICAL" || m.priority === "HIGH"
                            ? "bg-destructive"
                            : "bg-amber-500"
                      }`}
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{m.title}</p>
                      <div className="flex items-center gap-2 text-[11px] text-muted-foreground mt-0.5 flex-wrap">
                        <Badge variant="secondary" className="text-[10px]">
                          {m.category ?? m.complaintCategory ?? "—"}
                        </Badge>
                        <span>· {m.priority}</span>
                        <span>·</span>
                        <span>{m.status}</span>
                        {m.createdAt && (
                          <span className="ml-auto">
                            {formatDate(m.createdAt)}
                          </span>
                        )}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {/* Documents on file — owner approval workflow (Issue #9).
            Each row shows the doc + a Approve/Reject pair while
            PENDING, and a status badge once decided. Reject opens a
            small dialog to capture the reason (required, max 500 chars). */}
        <Card>
          <CardContent className="p-6">
            <Section title="Documents on file" Icon={FileText} />
            {!userServiceId ? (
              <p className="text-sm text-muted-foreground mt-3">
                Tenant hasn't completed their profile yet.
              </p>
            ) : documentsQ.isLoading ? (
              <Skeleton className="h-20 mt-3" />
            ) : (documentsQ.data?.length ?? 0) === 0 ? (
              <p className="text-sm text-muted-foreground mt-3">
                No documents uploaded.
              </p>
            ) : (
              <ul className="mt-3 space-y-2">
                {documentsQ.data!.slice(0, 8).map((d) => (
                  <DocumentRow key={d.id} doc={d} />
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

/* ─────────────────────── helpers + sub-components ─────────────────────── */

function totals(payments: PaymentResponse[]) {
  let paid = 0;
  let overdue = 0;
  let upcoming = 0;
  for (const p of payments) {
    const amt = Number(p.totalAmount ?? p.amount ?? 0);
    switch (p.status as PaymentStatus) {
      case "PAID":
        paid += amt;
        break;
      case "OVERDUE":
      case "FAILED":
        overdue += amt;
        break;
      case "PENDING":
      case "PROCESSING":
        upcoming += amt;
        break;
      default:
        break;
    }
  }
  return { paid, overdue, upcoming };
}

function PaymentsTable({ payments }: { payments: PaymentResponse[] }) {
  return (
    <>
      <Separator className="my-4" />
      <p className="text-xs uppercase tracking-wider text-muted-foreground mb-2">
        Recent activity
      </p>
      <ul className="space-y-1.5 text-sm">
        {payments.slice(0, 5).map((p) => (
          <li
            key={p.id}
            className="flex items-center gap-3 py-1"
          >
            <Calendar className="size-3.5 text-muted-foreground shrink-0" />
            <span className="text-xs text-muted-foreground w-20 shrink-0">
              {formatDate(p.dueDate)}
            </span>
            <span className="font-medium flex-1 truncate">
              {formatINR(Number(p.totalAmount ?? p.amount))}
            </span>
            <PaymentStatusBadge status={p.status as PaymentStatus} />
          </li>
        ))}
      </ul>
    </>
  );
}

function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  switch (status) {
    case "PAID":
      return <Badge variant="success" className="text-[10px]">Paid</Badge>;
    case "OVERDUE":
      return <Badge variant="destructive" className="text-[10px]">Overdue</Badge>;
    case "PROCESSING":
      return <Badge variant="warning" className="text-[10px]">Processing</Badge>;
    case "FAILED":
      return <Badge variant="destructive" className="text-[10px]">Failed</Badge>;
    case "REFUNDED":
      return <Badge variant="secondary" className="text-[10px]">Refunded</Badge>;
    case "CANCELLED":
      return <Badge variant="secondary" className="text-[10px]">Cancelled</Badge>;
    default:
      return <Badge variant="secondary" className="text-[10px]">Pending</Badge>;
  }
}

function KycBadge({ status }: { status?: KycStatus }) {
  if (status === "VERIFIED")
    return (
      <Badge variant="success" className="text-[10px]">
        <ShieldCheck className="size-3" /> KYC verified
      </Badge>
    );
  if (status === "FAILED")
    return (
      <Badge variant="destructive" className="text-[10px]">
        <AlertTriangle className="size-3" /> KYC failed
      </Badge>
    );
  if (status === "INITIATED")
    return (
      <Badge variant="warning" className="text-[10px]">
        KYC in progress
      </Badge>
    );
  return (
    <Badge variant="secondary" className="text-[10px]">
      KYC pending
    </Badge>
  );
}

function Section({
  title,
  Icon,
}: {
  title: string;
  Icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <h3 className="font-display font-semibold text-base flex items-center gap-2">
      <Icon className="size-4 text-primary" />
      {title}
    </h3>
  );
}

function KV({
  label,
  value,
  Icon,
}: {
  label: string;
  value: string;
  Icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="flex items-start gap-2">
      <Icon className="size-3.5 text-muted-foreground mt-0.5 shrink-0" />
      <div className="min-w-0">
        <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
          {label}
        </p>
        <p className="font-medium truncate">{value}</p>
      </div>
    </div>
  );
}

function KVCompact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p className="font-medium mt-0.5">{value}</p>
    </div>
  );
}

function Stat({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent: "success" | "destructive" | "muted";
}) {
  const cls =
    accent === "success"
      ? "text-emerald-600 dark:text-emerald-400"
      : accent === "destructive"
        ? "text-destructive"
        : "text-foreground";
  return (
    <div className="rounded-lg border bg-secondary/30 p-3">
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p className={`font-display text-lg font-semibold mt-1 ${cls}`}>
        {value}
      </p>
    </div>
  );
}

/**
 * Document row with owner approve/reject buttons (Issue #9). PENDING
 * docs surface the two action buttons; APPROVED / REJECTED docs show
 * a status badge and (for rejected) the reason in a small footer
 * underneath so the owner remembers what they asked for.
 */
function DocumentRow({ doc }: { doc: DocumentResponse }) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState("");

  const status = doc.verificationStatus ?? "PENDING";

  const approveM = useMutation({
    mutationFn: () => documentsApi.approve(doc.id, authUserId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tenant-documents", doc.userId] });
      toast({
        title: "Document approved",
        description: `${doc.documentType} marked as approved. The tenant has been notified.`,
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't approve document",
        description: extractErrorMessage(e),
      }),
  });

  const rejectM = useMutation({
    mutationFn: () => documentsApi.reject(doc.id, authUserId!, reason.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tenant-documents", doc.userId] });
      toast({
        title: "Document rejected",
        description: "The tenant has been notified and can re-upload.",
      });
      setRejectOpen(false);
      setReason("");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't reject document",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <li className="rounded-lg border bg-secondary/30 p-3 text-sm">
      <div className="flex items-center gap-3 flex-wrap">
        <FileText className="size-4 text-muted-foreground shrink-0" />
        <span className="font-medium truncate flex-1 min-w-0">
          {doc.originalFilename ?? doc.id}
        </span>
        <Badge variant="secondary" className="text-[10px]">
          {doc.documentType}
        </Badge>
        {status === "APPROVED" && (
          <Badge variant="success" className="text-[10px]">
            <CheckCircle2 className="size-3" /> Approved
          </Badge>
        )}
        {status === "REJECTED" && (
          <Badge variant="destructive" className="text-[10px]">
            <X className="size-3" /> Rejected
          </Badge>
        )}
        {status === "PENDING" && (
          <>
            <Badge variant="warning" className="text-[10px]">
              Pending review
            </Badge>
            <Button
              size="sm"
              variant="outline"
              className="h-7"
              onClick={() => approveM.mutate()}
              disabled={approveM.isPending || !authUserId}
            >
              {approveM.isPending ? (
                <Loader2 className="size-3 animate-spin" />
              ) : (
                <Check className="size-3" />
              )}
              Approve
            </Button>
            <Button
              size="sm"
              variant="destructive"
              className="h-7"
              onClick={() => setRejectOpen(true)}
              disabled={!authUserId}
            >
              <X className="size-3" /> Reject
            </Button>
          </>
        )}
      </div>
      {/* Rejected doc footer — small note with the reason so the owner
          can remember what they told the tenant. */}
      {status === "REJECTED" && doc.rejectionReason && (
        <p className="text-xs text-muted-foreground mt-2 pl-7">
          <span className="font-semibold">Reason:</span> {doc.rejectionReason}
        </p>
      )}

      {/* OCR evidence panel — populated by Sandbox.co.in's PAN / Aadhaar
          OCR APIs (or any other configured OcrEngine). Helps the owner
          decide approve vs. reject without manually squinting at the
          uploaded image. Hidden for PENDING / failed OCR. */}
      <OcrEvidence doc={doc} />

      {/* Reject-reason dialog */}
      <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Reject {doc.documentType}?</DialogTitle>
            <DialogDescription>
              Tell the tenant what to fix so they can re-upload. They'll see
              this reason in their notification + on their documents tab.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor={`reject-reason-${doc.id}`}>Reason</Label>
            <Textarea
              id={`reject-reason-${doc.id}`}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. The photo is too blurry — please upload a clearer copy of the front side."
              maxLength={500}
              rows={4}
            />
            <p className="text-[11px] text-muted-foreground">
              {reason.length} / 500 characters
            </p>
          </div>
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => setRejectOpen(false)}
              disabled={rejectM.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => rejectM.mutate()}
              disabled={!reason.trim() || rejectM.isPending}
            >
              {rejectM.isPending && (
                <Loader2 className="size-4 animate-spin" />
              )}
              Confirm rejection
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </li>
  );
}

/**
 * Inline OCR evidence panel under each document row.
 *
 * <p>Pulls the OCR-extracted fields off the {@link DocumentResponse}
 * (populated server-side by {@code SandboxOcrEngine.extract}) and
 * surfaces them as a small key→value grid the owner can glance at
 * before approving / rejecting. When Sandbox's PAN-verify cross-check
 * flags a forgery the panel switches to a red warning band.
 *
 * <p>Visual states:
 * <ul>
 *   <li><b>PROCESSING</b> — "Verifying with NSDL…" spinner.</li>
 *   <li><b>DONE</b> + fraudFlag=false — green "Verified by Sandbox" header
 *       with the extracted fields below.</li>
 *   <li><b>DONE</b> + fraudFlag=true — red warning band with the
 *       failureReason; the owner should reject this document.</li>
 *   <li><b>FAILED</b> — small muted "OCR unavailable — verify manually"
 *       note. Doesn't block approval.</li>
 *   <li><b>PENDING</b> — hidden (queued, hasn't run yet).</li>
 * </ul>
 */
function OcrEvidence({ doc }: { doc: DocumentResponse }) {
  const status = doc.ocrStatus;
  const fields = doc.extractedData ?? {};
  const hasFields = Object.keys(fields).length > 0;

  if (status === "PENDING") return null;

  if (status === "PROCESSING") {
    return (
      <div className="mt-2 pl-7 flex items-center gap-2 text-xs text-muted-foreground">
        <Loader2 className="size-3 animate-spin" />
        Verifying with NSDL via Sandbox.co.in…
      </div>
    );
  }

  if (status === "FAILED" || !hasFields) {
    return (
      <p className="mt-2 pl-7 text-xs text-muted-foreground italic">
        Auto-verification unavailable — please review the image manually.
      </p>
    );
  }

  // status === "DONE" with extracted fields. Branch on fraudFlag.
  if (doc.fraudFlag) {
    return (
      <div className="mt-2 ml-7 rounded-lg border border-destructive/40 bg-destructive/10 p-2.5 text-xs">
        <div className="flex items-start gap-1.5">
          <AlertTriangle className="size-3.5 text-destructive mt-0.5 shrink-0" />
          <div>
            <p className="font-semibold text-destructive">
              ⚠ Cross-check failed — possible forgery
            </p>
            <p className="text-muted-foreground mt-0.5">
              We OCR'd this document and ran it against NSDL. The result
              doesn't match a valid registration. Reject and ask the
              tenant for a clearer / genuine copy.
            </p>
            <OcrFieldGrid fields={fields} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mt-2 ml-7 rounded-lg border border-emerald-500/30 bg-emerald-500/5 p-2.5 text-xs">
      <div className="flex items-center gap-1.5 font-semibold text-emerald-700 dark:text-emerald-400">
        <ShieldCheck className="size-3.5" />
        Verified by Sandbox · NSDL
        {typeof doc.confidenceScore === "number" && (
          <span className="font-normal text-muted-foreground ml-auto">
            confidence {Math.round(doc.confidenceScore * 100)}%
          </span>
        )}
      </div>
      <OcrFieldGrid fields={fields} />
    </div>
  );
}

/** Tiny 2-column grid that renders {key → value} pairs from the OCR map. */
function OcrFieldGrid({ fields }: { fields: Record<string, string> }) {
  // Pretty-print the field keys: panNumber → "PAN", idNumberLast4 → "Aadhaar ····N"
  const rows = Object.entries(fields).map(([k, v]) => ({
    label: prettyLabel(k),
    value: prettyValue(k, v),
  }));
  return (
    <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-2 gap-y-0.5">
      {rows.map((r) => (
        <Fragment key={r.label}>
          <dt className="text-muted-foreground">{r.label}</dt>
          <dd className="font-mono">{r.value}</dd>
        </Fragment>
      ))}
    </dl>
  );
}

function prettyLabel(key: string): string {
  switch (key) {
    case "panNumber":
      return "PAN";
    case "nameOnNsdl":
      return "NSDL name";
    case "idNumberLast4":
      return "Aadhaar";
    case "dob":
      return "DOB";
    case "fatherName":
      return "Father's name";
    default:
      // camelCase → "Camel case"
      return key.replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase());
  }
}

function prettyValue(key: string, val: string): string {
  if (key === "idNumberLast4") return `····${val}`;
  if (key === "panNumber" && val.length === 10) {
    return val.substring(0, 4) + "****" + val.substring(8);
  }
  return val;
}
