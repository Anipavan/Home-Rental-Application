import { useMemo } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Info, Receipt } from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import type { FlatChargeCategory, FlatMaintenanceRow } from "@/types/api";

const CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

/**
 * Bulk-pay landing page for all DUE charges in a month.
 *
 * <p>Reached from the "Pay all" button on the tenant's society page
 * footer when the Total Due row is rendered. The destination route
 * is intentionally separate from the per-charge {@link SocietyPayPage}
 * because a Razorpay order is currency-amount + descriptor scoped to
 * a single payable — bulk pay needs its own gateway-order creation
 * (one Razorpay charge that, on success, marks ALL these collection
 * rows PAID atomically).
 *
 * <p><b>Current state (placeholder):</b> the Razorpay bridge for
 * society charges isn't wired yet (tracked separately). For now this
 * page lists every DUE charge for the month with individual Pay
 * buttons so the tenant can settle them one-by-one via the existing
 * UPI-QR flow. The "Pay all via Razorpay" CTA is disabled with an
 * inline note so the tenant knows it's coming. When the backend
 * bridge ships, this page becomes the launchpad for the multi-method
 * picker (same UI as /app/payments/:id/pay).
 */
export function SocietyPayAllPage() {
  const { buildingId, month } = useParams<{
    buildingId: string;
    month: string;
  }>();

  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });

  const billsQ = useQuery({
    queryKey: ["tenant-society-bills", buildingId, month],
    queryFn: () => societyApi.myBills(buildingId!, month!),
    enabled: !!buildingId && !!month,
    staleTime: 15_000,
  });

  // Only charges in DUE / OVERDUE need paying. PAID and WAIVED rows
  // are filtered out — they shouldn't show up on a "Pay all" screen
  // and counting them in the total would mislead the tenant.
  const dueRows: FlatMaintenanceRow[] = useMemo(
    () =>
      (billsQ.data ?? []).filter(
        (r) => r.status === "DUE" || r.status === "OVERDUE",
      ),
    [billsQ.data],
  );

  const total = useMemo(
    () => dueRows.reduce((s, r) => s + r.monthAmount, 0),
    [dueRows],
  );

  if (!buildingId || !month) {
    return (
      <EmptyState
        variant="info"
        icon={Receipt}
        title="Invalid link"
        description="No building or month specified. Go back to the society page and pick Pay all from the My charges footer."
      />
    );
  }

  if (configQ.isLoading || billsQ.isLoading) {
    return (
      <div className="max-w-3xl space-y-4">
        <Skeleton className="h-12 rounded-lg" />
        <Skeleton className="h-72 rounded-2xl" />
      </div>
    );
  }

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title={`Pay all · ${month}`}
        description={`Settle every outstanding charge against your flat in ${month}.`}
        actions={
          <Button asChild variant="ghost" size="sm">
            <Link to="/app/society">
              <ArrowLeft className="size-4" /> Back to society
            </Link>
          </Button>
        }
      />

      {/* Heads-up about the bulk-pay button until the Razorpay bridge
        * ships. The individual Pay buttons below still work via the
        * existing UPI-QR flow, so the tenant isn't blocked from
        * actually clearing their dues — they just can't yet do it in
        * one click. */}
      <Card className="mb-4 border-primary/30 bg-primary/5">
        <CardContent className="p-4 flex items-start gap-3">
          <Info className="size-5 text-primary shrink-0 mt-0.5" />
          <div className="text-sm">
            <p className="font-semibold">One-click bulk pay coming soon</p>
            <p className="text-muted-foreground mt-0.5">
              We're wiring up Razorpay to let you settle every charge in
              one transaction with UPI, Card, or Net Banking — the same
              picker you see on the rent-pay page. Until then, use the
              individual Pay buttons below; each opens the society's
              UPI QR for that charge.
            </p>
          </div>
        </CardContent>
      </Card>

      {!dueRows.length ? (
        <EmptyState
          variant="info"
          icon={Receipt}
          title="Nothing to pay this month"
          description="You're fully paid up for this month. The Pay all button only shows when there are DUE / OVERDUE charges to settle."
          action={
            <Button asChild variant="outline">
              <Link to="/app/society">Back to society</Link>
            </Button>
          }
        />
      ) : (
        <>
          {/* Totals card. Mirrors the "Order summary" style from the
            * rent-pay page so the tenant sees the same visual language
            * across both flows. */}
          <Card className="mb-4">
            <CardContent className="p-5">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs uppercase tracking-wider text-muted-foreground">
                    Total outstanding
                  </p>
                  <p className="font-display font-bold text-3xl mt-1">
                    {formatINR(total)}
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {dueRows.length} charge{dueRows.length === 1 ? "" : "s"}{" "}
                    · {month}
                  </p>
                </div>
                {/* Disabled placeholder — flip to a real Razorpay launch
                  * button once the backend bridge endpoint exists. The
                  * disabled state + helper text make the upcoming
                  * behaviour discoverable without misleading the user. */}
                <Button
                  variant="gradient"
                  size="lg"
                  disabled
                  title="Razorpay bulk pay launching shortly — pay each charge below for now."
                >
                  Pay all via Razorpay
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* Per-charge list — same row shape as the My charges table
            * on the society page but laid out as cards because this
            * page is single-purpose (pay these things). Each card
            * routes to the existing /app/society/pay/:b/:c page. */}
          <div className="space-y-2">
            {dueRows.map((row) => {
              const label = row.category
                ? CATEGORY_LABELS[row.category]
                : "Other";
              return (
                <Card
                  key={row.collectionId ?? `${row.category}-${row.flatId}`}
                  className="hover:border-primary/40 transition-colors"
                >
                  <CardContent className="p-4 flex items-center gap-3">
                    <Badge variant="secondary" className="text-[10px] shrink-0">
                      {label}
                    </Badge>
                    <div className="flex-1 min-w-0">
                      {row.notes ? (
                        <p className="text-xs text-muted-foreground italic line-clamp-2">
                          {row.notes}
                        </p>
                      ) : (
                        <p className="text-xs text-muted-foreground">
                          Flat {row.flatNumber} · {row.forMonth}
                        </p>
                      )}
                    </div>
                    <p className="font-display font-semibold whitespace-nowrap">
                      {formatINR(row.monthAmount)}
                    </p>
                    <Button asChild variant="gradient" size="sm">
                      <Link
                        to={`/app/society/pay/${buildingId}/${row.collectionId}`}
                      >
                        Pay
                      </Link>
                    </Button>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
