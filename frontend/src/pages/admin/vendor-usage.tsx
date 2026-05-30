import { useQuery } from "@tanstack/react-query";
import {
  AlertCircle,
  CheckCircle2,
  CloudOff,
  CreditCard,
  Server,
  Users,
} from "lucide-react";
import { kycApi } from "@/lib/api/kyc";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatDate, cn } from "@/lib/utils";

/**
 * Admin-only dashboard for third-party vendor API usage. Surfaces:
 *
 *  • Per-vendor call counts (today + last 30 days)
 *  • Success / user-error / billing-alert / outage breakdown
 *  • Last 10 billing alerts across all vendors with vendor name + time
 *    + raw error message, so an operator can tell "Sandbox is out of
 *    credits" from "Razorpay is rate-limiting us" without log diving.
 *
 * <p>Backend endpoint enforces the ADMIN role — non-admin callers get
 * a 403 here. The page renders an inline "Forbidden" state for that
 * case rather than crashing the entire route, so admins demoting
 * themselves don't break the SPA.
 */
export function AdminVendorUsagePage() {
  const usageQ = useQuery({
    queryKey: ["admin", "vendor-usage"],
    queryFn: () => kycApi.adminVendorUsage(),
    // Auto-refresh every minute — billing alerts are time-sensitive
    // and an admin on this page wants near-live data without a
    // manual refresh.
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
    retry: false,
  });

  const data = usageQ.data;
  const vendors = data?.vendors ?? [];
  const alerts = data?.recentBillingAlerts ?? [];

  // Total billing alerts across the 30-day window — what the "health"
  // banner shows. 0 = green check, anything > 0 = needs attention.
  const totalBillingAlerts = vendors.reduce(
    (sum, v) => sum + (v.billingAlertsMonth ?? 0),
    0,
  );
  const totalOutages = vendors.reduce(
    (sum, v) => sum + (v.outagesMonth ?? 0),
    0,
  );

  return (
    <div className="animate-fade-in max-w-6xl">
      <PageHeader
        title="Vendor usage"
        description="Third-party API consumption, billing alerts, and outages — admin only."
      />

      {usageQ.isError ? (
        <EmptyState
          variant="info"
          icon={CloudOff}
          title="Couldn't load vendor usage"
          description="If this is a 403 you may not have admin role. Otherwise the kyc-service might be down — check the deploy logs."
        />
      ) : (
        <>
          {/* Top-level health summary banner */}
          <Card
            className={cn(
              "mb-6 border",
              totalBillingAlerts > 0
                ? "border-destructive/30 bg-destructive/5"
                : totalOutages > 0
                  ? "border-warning/30 bg-warning/5"
                  : "border-success/30 bg-success/5",
            )}
          >
            <CardContent className="p-5 flex items-start gap-4">
              <div
                className={cn(
                  "size-11 rounded-xl grid place-items-center shrink-0",
                  totalBillingAlerts > 0
                    ? "bg-destructive/15 text-destructive"
                    : totalOutages > 0
                      ? "bg-warning/15 text-warning"
                      : "bg-success/15 text-success",
                )}
              >
                {totalBillingAlerts > 0 ? (
                  <CreditCard className="size-5" />
                ) : totalOutages > 0 ? (
                  <AlertCircle className="size-5" />
                ) : (
                  <CheckCircle2 className="size-5" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-display font-semibold text-base">
                  {totalBillingAlerts > 0
                    ? `${totalBillingAlerts} billing alert${
                        totalBillingAlerts === 1 ? "" : "s"
                      } in the last 30 days`
                    : totalOutages > 0
                      ? `${totalOutages} vendor outage${
                          totalOutages === 1 ? "" : "s"
                        } in the last 30 days`
                      : "All vendors healthy"}
                </p>
                <p className="text-sm text-muted-foreground mt-0.5">
                  {totalBillingAlerts > 0
                    ? "One or more vendor accounts are out of credits or rate-limited. Top up to unblock users."
                    : totalOutages > 0
                      ? "Transient vendor downtime detected — likely auto-recovered, no action needed."
                      : "No billing alerts or outages logged in the last 30 days."}
                </p>
              </div>
            </CardContent>
          </Card>

          {/* Per-vendor breakdown */}
          {usageQ.isLoading ? (
            <div className="grid gap-4 sm:grid-cols-2">
              {[1, 2].map((i) => (
                <Skeleton key={i} className="h-44 rounded-2xl" />
              ))}
            </div>
          ) : vendors.length === 0 ? (
            <EmptyState
              variant="info"
              icon={Server}
              title="No vendor calls recorded yet"
              description="Once any user triggers a KYC / payment / notification that hits an external API, this dashboard fills in. Try a PAN verification to see it light up."
            />
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {vendors.map((v) => (
                <Card key={v.vendorName} className="p-5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-mono text-xs uppercase tracking-wider text-muted-foreground">
                        {v.vendorName}
                      </p>
                      <p className="font-display font-bold text-3xl mt-1">
                        {v.callsToday}
                        <span className="text-sm font-normal text-muted-foreground ml-2">
                          today
                        </span>
                      </p>
                    </div>
                    {v.billingAlertsMonth > 0 ? (
                      <Badge variant="destructive">Billing alert</Badge>
                    ) : v.outagesMonth > 0 ? (
                      <Badge variant="warning">Outage detected</Badge>
                    ) : (
                      <Badge variant="success">Healthy</Badge>
                    )}
                  </div>
                  <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
                    <Cell
                      label="Successful today"
                      value={String(v.successToday)}
                      tone="success"
                    />
                    <Cell
                      label="User errors today"
                      value={String(v.userErrorsToday)}
                      tone="muted"
                    />
                    <Cell
                      label="Calls (30d)"
                      value={String(v.callsMonth)}
                      tone="muted"
                    />
                    <Cell
                      label="Billing alerts (30d)"
                      value={String(v.billingAlertsMonth)}
                      tone={v.billingAlertsMonth > 0 ? "destructive" : "muted"}
                    />
                  </div>
                </Card>
              ))}
            </div>
          )}

          {/* Recent billing alerts list */}
          <Card className="mt-6">
            <CardContent className="p-6">
              <h3 className="font-display font-semibold text-lg flex items-center gap-2">
                <CreditCard className="size-4 text-destructive" />
                Recent billing alerts
              </h3>
              <p className="text-sm text-muted-foreground mt-1">
                Most recent vendor rejections caused by OUR account hitting
                a credit / quota limit. Top up the vendor to clear.
              </p>
              {alerts.length === 0 ? (
                <p className="text-sm text-muted-foreground mt-4 text-center py-6">
                  No billing alerts in recent history.
                </p>
              ) : (
                <div className="mt-4 space-y-2">
                  {alerts.map((a, i) => (
                    <div
                      key={i}
                      className="rounded-xl border border-destructive/20 bg-destructive/5 p-3 flex items-start gap-3"
                    >
                      <div className="size-9 rounded-lg bg-destructive/15 text-destructive grid place-items-center shrink-0">
                        <CreditCard className="size-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <Badge variant="destructive" className="text-[10px]">
                            {a.vendorName}
                          </Badge>
                          <span className="text-xs text-muted-foreground">
                            {formatDate(a.occurredAt)} ·{" "}
                            {new Date(a.occurredAt).toLocaleTimeString()}
                          </span>
                        </div>
                        <p className="text-sm mt-1 break-words">
                          {a.errorMessage ?? "(no message)"}
                        </p>
                        {a.vendorEndpoint && (
                          <p className="text-[11px] text-muted-foreground font-mono mt-0.5 truncate">
                            {a.errorCode ? `HTTP ${a.errorCode} · ` : ""}
                            {a.vendorEndpoint}
                          </p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {data && (
            <p className="text-xs text-muted-foreground mt-4 text-right">
              <Users className="size-3 inline mr-1" />
              Last refreshed{" "}
              {new Date(data.generatedAt).toLocaleTimeString()} · auto-refreshes every minute
            </p>
          )}
        </>
      )}
    </div>
  );
}

function Cell({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: "success" | "destructive" | "muted";
}) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p
        className={cn(
          "font-display font-bold text-xl mt-0.5",
          tone === "success" && "text-success",
          tone === "destructive" && "text-destructive",
        )}
      >
        {value}
      </p>
    </div>
  );
}
