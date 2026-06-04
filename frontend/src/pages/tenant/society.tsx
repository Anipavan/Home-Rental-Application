import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  Calendar,
  CheckCircle2,
  Droplets,
  Receipt,
  Wallet,
  Wrench,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import type {
  ExpenseCategory,
  FlatChargeCategory,
  FlatMaintenanceRow,
  SocietyConfig,
} from "@/types/api";

const EXPENSE_LABELS: Record<ExpenseCategory, string> = {
  UTILITY: "Utility",
  SALARY: "Staff salary",
  SUPPLIES: "Supplies",
  REPAIR_COMMON: "Common-area repair",
  INSURANCE: "Insurance",
  TAX: "Tax / govt fees",
  OTHER: "Other",
};

const CHARGE_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

const STATUS_TONES: Record<string, string> = {
  DUE: "bg-warning/20 text-warning",
  OVERDUE: "bg-destructive/20 text-destructive",
  PAID: "bg-success/20 text-success",
  WAIVED: "bg-secondary text-secondary-foreground",
};

const currentMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

/**
 * Tenant-side view of their building's society. Two stacked sections:
 *
 * <ol>
 *   <li><b>My charges</b> — per-line bills the maintainer has recorded
 *       against this tenant's flat for the selected month, with a
 *       Pay-Now button per row that opens a UPI QR modal.</li>
 *   <li><b>Where the money went</b> — read-only common-area expense
 *       ledger that every resident shares.</li>
 * </ol>
 *
 * <p>The Pay button stays hidden when the society hasn't configured
 * a UPI ID — without somewhere to send the money, the QR is useless.
 */
export function TenantSocietyPage() {
  const [month, setMonth] = useState(currentMonth());

  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });

  const ledgerQ = useQuery({
    queryKey: ["tenant-society-ledger", configQ.data?.buildingId, month],
    queryFn: () => societyApi.ledger(configQ.data!.buildingId, month),
    enabled: !!configQ.data?.buildingId,
    staleTime: 30_000,
  });

  const myBillsQ = useQuery({
    queryKey: ["tenant-society-bills", configQ.data?.buildingId, month],
    queryFn: () => societyApi.myBills(configQ.data!.buildingId, month),
    enabled: !!configQ.data?.buildingId,
    staleTime: 15_000,
  });

  // Aggregate outstanding for the headline tile.
  const totalDue = useMemo(() => {
    const rows = myBillsQ.data ?? [];
    return rows
      .filter((r) => r.status === "DUE" || r.status === "OVERDUE")
      .reduce((s, r) => s + r.monthAmount, 0);
  }, [myBillsQ.data]);

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Society ledger"
        description="Your monthly bills + a transparent record of common-area expenses."
      />

      {configQ.isLoading ? (
        <Skeleton className="h-64 rounded-2xl" />
      ) : !configQ.data ? (
        <EmptyState
          variant="info"
          icon={Building2}
          title="No society set up yet"
          description="The owner hasn't enabled common-area maintenance tracking for your building. Ask them to set it up to see a transparent expense ledger here."
        />
      ) : (
        <>
          <p className="text-sm text-muted-foreground mb-4">
            Viewing{" "}
            <span className="font-semibold text-foreground">
              {configQ.data.societyDisplayName ?? "your society"}
            </span>{" "}
            · dues ₹{configQ.data.defaultPerFlatAmount}/flat by day{" "}
            {configQ.data.monthlyDueDay} each month.
          </p>

          <div className="flex items-center gap-3 mb-4">
            <Calendar className="size-4 text-muted-foreground" />
            <Input
              type="month"
              value={month}
              onChange={(e) => setMonth(e.target.value || currentMonth())}
              className="w-40"
            />
          </div>

          {/* ── My charges (the new bit) ─────────────────────────── */}
          <Card className="mb-6">
            <CardContent className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-display font-semibold text-lg">
                  My charges — {month}
                </h3>
                {totalDue > 0 && (
                  <span className="text-sm">
                    <span className="text-muted-foreground">Outstanding: </span>
                    <span className="font-semibold text-destructive">
                      {formatINR(totalDue)}
                    </span>
                  </span>
                )}
              </div>

              {myBillsQ.isLoading ? (
                <Skeleton className="h-24 rounded-xl" />
              ) : !myBillsQ.data?.length ? (
                <EmptyState
                  variant="info"
                  icon={Receipt}
                  title="No bills posted for you this month yet"
                  description="The maintainer hasn't entered any charges against your flat for this month. Check back later or message them if you think this is wrong."
                />
              ) : (
                <div className="space-y-2">
                  {myBillsQ.data.map((row) => (
                    <ChargeRow
                      key={row.collectionId ?? row.category ?? "row"}
                      row={row}
                      config={configQ.data!}
                    />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* ── Common-area ledger (existing) ─────────────────────── */}
          <div className="grid gap-4 sm:grid-cols-2 mb-6">
            <Kpi
              icon={Droplets}
              label="Expenses this month"
              value={formatINR(ledgerQ.data?.expensesThisMonth ?? 0)}
              tone="destructive"
            />
            <Kpi
              icon={Wallet}
              label="Lifetime balance"
              value={formatINR(ledgerQ.data?.balanceLifetime ?? 0)}
              tone={
                (ledgerQ.data?.balanceLifetime ?? 0) >= 0
                  ? "success"
                  : "destructive"
              }
            />
          </div>

          <Card>
            <CardContent className="p-6">
              <h3 className="font-display font-semibold text-lg mb-4">
                Where the money went — {month}
              </h3>

              {ledgerQ.isLoading ? (
                <Skeleton className="h-24 rounded-xl" />
              ) : !ledgerQ.data?.expenses?.length ? (
                <EmptyState
                  variant="info"
                  icon={Wrench}
                  title="No expenses recorded for this month yet"
                  description="The maintainer hasn't added any bills for this month. Check back later."
                />
              ) : (
                <div className="space-y-2">
                  {ledgerQ.data.expenses.map((e) => (
                    <div
                      key={e.id}
                      className="flex items-start gap-3 p-3 rounded-xl border border-border/60 bg-secondary/30"
                    >
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <Badge variant="secondary" className="text-[10px]">
                            {EXPENSE_LABELS[e.category]}
                          </Badge>
                          <span className="font-medium text-sm">
                            {e.subcategory ?? e.vendorName ?? "—"}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground mt-0.5">
                          {e.paidOnDate}
                          {e.vendorName && e.subcategory
                            ? ` · paid to ${e.vendorName}`
                            : ""}
                        </p>
                        {e.notes && (
                          <p className="text-xs text-muted-foreground mt-1 italic">
                            {e.notes}
                          </p>
                        )}
                      </div>
                      <p className="font-semibold font-display">
                        {formatINR(e.amount)}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

/** One charge row in the "My charges" section. Renders the category +
 *  status pill + amount + Pay button (when status=DUE/OVERDUE and the
 *  society has a UPI ID configured). */
function ChargeRow({
  row,
  config,
}: {
  row: FlatMaintenanceRow;
  config: SocietyConfig;
}) {
  const label = row.category ? CHARGE_LABELS[row.category] : "Other";
  const tone = STATUS_TONES[row.status] ?? "bg-muted text-muted-foreground";
  const canPay =
    (row.status === "DUE" || row.status === "OVERDUE") && !!config.upiId;
  const isPaid = row.status === "PAID";

  return (
    <div className="flex items-center gap-3 p-3 rounded-xl border border-border/60 bg-secondary/30">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <Badge variant="secondary" className="text-[10px]">
            {label}
          </Badge>
          <span
            className={`rounded-full text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 ${tone}`}
          >
            {row.status}
          </span>
          {isPaid && row.paidOn && (
            <span className="text-[11px] text-muted-foreground">
              on {row.paidOn}
            </span>
          )}
        </div>
        {row.notes && (
          <p className="text-xs text-muted-foreground mt-1 italic">
            {row.notes}
          </p>
        )}
      </div>
      <p className="font-semibold font-display">{formatINR(row.monthAmount)}</p>
      {canPay && row.collectionId && (
        <Button asChild variant="gradient" size="sm">
          <Link
            to={`/app/society/pay/${config.buildingId}/${row.collectionId}`}
          >
            Pay {formatINR(row.monthAmount)}
          </Link>
        </Button>
      )}
      {isPaid && (
        <CheckCircle2 className="size-5 text-success" aria-label="Paid" />
      )}
    </div>
  );
}

function Kpi({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  tone: "success" | "destructive" | "muted";
}) {
  const toneClass =
    tone === "success"
      ? "text-success"
      : tone === "destructive"
        ? "text-destructive"
        : "text-foreground";
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-muted-foreground">
          <Icon className="size-4" />
          {label}
        </div>
        <p className={`font-display font-bold text-2xl mt-2 ${toneClass}`}>
          {value}
        </p>
      </CardContent>
    </Card>
  );
}
