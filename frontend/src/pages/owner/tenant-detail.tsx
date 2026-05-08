import { useMemo } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
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
} from "lucide-react";
import { paymentsApi } from "@/lib/api/payments";
import { maintenanceApi } from "@/lib/api/maintenance";
import { agreementsApi } from "@/lib/api/agreements";
import { documentsApi } from "@/lib/api/documents";
import { propertiesApi } from "@/lib/api/properties";
import { kycApi } from "@/lib/api/kyc";
import { useUserByAuth } from "@/hooks/use-user-by-auth";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Separator } from "@/components/ui/separator";
import { PageHeader } from "@/components/layout/page-header";
import { formatDate, formatINR, initials } from "@/lib/utils";
import type {
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

  const userQ = useUserByAuth(authUserId);
  const userServiceId = userQ.user ? String(userQ.user.id) : undefined;

  const flatsQ = useQuery({
    queryKey: ["tenant-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId),
    enabled: !!authUserId,
  });

  const paymentsQ = useQuery({
    queryKey: ["tenant-payments", authUserId],
    queryFn: () => paymentsApi.byTenant(authUserId),
    enabled: !!authUserId,
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

  const kycQ = useQuery({
    queryKey: ["tenant-kyc", userServiceId],
    queryFn: () => kycApi.status(userServiceId!),
    enabled: !!userServiceId,
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

      {/* Header — name, contact actions, KYC badge */}
      <Card className="mb-6">
        <CardContent className="p-6 sm:p-8">
          {userQ.isLoading ? (
            <Skeleton className="h-24" />
          ) : !userQ.user ? (
            <div className="text-sm text-muted-foreground">
              Couldn't load tenant profile.
            </div>
          ) : (
            <div className="flex items-start gap-5 flex-wrap">
              <Avatar className="size-20">
                {userQ.user.profilePictureUrl && (
                  <AvatarImage src={userQ.user.profilePictureUrl} />
                )}
                <AvatarFallback className="text-2xl">
                  {initials(userQ.fullName ?? "")}
                </AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <PageHeader
                  className="mb-2"
                  title={userQ.fullName || "Tenant"}
                  description={
                    flat
                      ? `${(flat as { buildingName?: string }).buildingName ?? "Flat"} · ${flat.flatNumber}`
                      : "No active lease"
                  }
                />
                <KycBadge status={kycQ.data?.verificationStatus} />
              </div>
              <div className="flex flex-wrap gap-2">
                {userQ.user.phone && (
                  <Button asChild variant="outline" size="sm">
                    <a href={`tel:${userQ.user.phone}`}>
                      <Phone /> Call
                    </a>
                  </Button>
                )}
                {userQ.user.email && (
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
                          {m.category}
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

        {/* Documents on file */}
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
                {documentsQ.data!.slice(0, 6).map((d) => (
                  <li
                    key={d.id}
                    className="rounded-lg border bg-secondary/30 p-3 flex items-center gap-3 text-sm"
                  >
                    <FileText className="size-4 text-muted-foreground shrink-0" />
                    <span className="font-medium truncate flex-1">
                      {d.originalFilename ?? d.id}
                    </span>
                    <Badge variant="secondary" className="text-[10px]">
                      {d.documentType}
                    </Badge>
                    {d.verifiedAt ? (
                      <Badge variant="success" className="text-[10px]">
                        <CheckCircle2 className="size-3" /> Verified
                      </Badge>
                    ) : (
                      <Badge variant="warning" className="text-[10px]">
                        Pending
                      </Badge>
                    )}
                  </li>
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
