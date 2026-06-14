import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  Calendar,
  CheckCircle2,
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
import { CollapsibleSection } from "@/components/ui/collapsible-section";
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
 *   <li><b>Expenses</b> — read-only common-area expense ledger that
 *       every resident shares.</li>
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
              className="w-48"
            />
          </div>

          {/* Banner when society has no UPI configured. The Pay
            *  button still routes to the dedicated Pay page (which
            *  handles the empty-state itself), but a banner here
            *  surfaces the "ask the maintainer to set it up" hint
            *  before the tenant even clicks. */}
          {!configQ.data.upiId && (
            <Card className="mb-4 border-warning/40 bg-warning/5">
              <CardContent className="p-4 flex items-start gap-3">
                <Wallet className="size-5 text-warning shrink-0 mt-0.5" />
                <div className="text-sm">
                  <p className="font-semibold">
                    Online payment not set up yet
                  </p>
                  <p className="text-muted-foreground mt-0.5">
                    The maintainer hasn't added the society's UPI ID to
                    the collection account. Once they do, your Pay
                    button below will generate a UPI QR you can scan
                    from any app. Until then, please pay them directly
                    and ask them to mark the charge as paid.
                  </p>
                </div>
              </CardContent>
            </Card>
          )}

          {/* ── My charges (collapsible) ─────────────────────────── */}
          <CollapsibleSection
            className="mb-4"
            title={`My charges — ${month}`}
            icon={Receipt}
            summary={
              myBillsQ.data?.length
                ? totalDue > 0
                  ? `Total Dues ${formatINR(totalDue)}`
                  : `${myBillsQ.data.length} charge${myBillsQ.data.length === 1 ? "" : "s"}`
                : "No bills"
            }
          >
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
              <div className="overflow-x-auto rounded-lg border border-border/60">
                <table className="w-full text-sm border-collapse">
                  <thead>
                    <tr className="bg-secondary/40 border-b border-border/60">
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Category
                      </th>
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground">
                        Description
                      </th>
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Status
                      </th>
                      <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Amount
                      </th>
                      <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        {/* Action column header intentionally blank —
                          * the column holds Pay buttons / "paid" check
                          * marks, which speak for themselves. */}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {myBillsQ.data.map((row) => (
                      <ChargeRow
                        key={row.collectionId ?? row.category ?? "row"}
                        row={row}
                        config={configQ.data!}
                      />
                    ))}
                  </tbody>
                  {/* Total Due footer — only shown when there's
                    * actually something due (skipping it on a fully-
                    * paid month keeps the table from looking like a
                    * "you owe ₹0" nag). The Pay all button routes
                    * to a bulk pay flow that opens Razorpay for the
                    * combined total — see /app/society/pay-all/...
                    * The link reuses the building + month from the
                    * page query state, so the destination page can
                    * re-fetch DUE rows and fan out one Razorpay order
                    * covering all of them. */}
                  {totalDue > 0 && (
                    <tfoot>
                      <tr className="bg-primary/5 border-t-2 border-border/60 font-semibold">
                        <td
                          colSpan={3}
                          className="px-3 py-3 text-right text-sm uppercase tracking-wider text-muted-foreground"
                        >
                          Total Due
                        </td>
                        <td className="px-3 py-3 text-right whitespace-nowrap">
                          <span className="font-display text-base">
                            {formatINR(totalDue)}
                          </span>
                        </td>
                        <td className="px-3 py-3 text-right whitespace-nowrap">
                          <Button asChild variant="gradient" size="sm">
                            <Link
                              to={`/app/society/pay-all/${configQ.data!.buildingId}/${month}`}
                            >
                              Pay all · {formatINR(totalDue)}
                            </Link>
                          </Button>
                        </td>
                      </tr>
                    </tfoot>
                  )}
                </table>
              </div>
            )}
          </CollapsibleSection>

          {/* ── Common-area Expenses (collapsible + tabular) ──────── */}
          <CollapsibleSection
            className="mb-4"
            title={`Expenses — ${month}`}
            icon={Wrench}
            summary={
              ledgerQ.data?.expenses?.length
                ? `${ledgerQ.data.expenses.length} entr${ledgerQ.data.expenses.length === 1 ? "y" : "ies"} · ${formatINR(ledgerQ.data.expensesThisMonth ?? 0)}`
                : "No entries"
            }
          >
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
              <div className="overflow-x-auto rounded-lg border border-border/60">
                <table className="w-full text-sm border-collapse">
                  <thead>
                    <tr className="bg-secondary/40 border-b border-border/60">
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Category
                      </th>
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground">
                        Description
                      </th>
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Vendor
                      </th>
                      <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Paid on
                      </th>
                      <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                        Amount
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {ledgerQ.data.expenses.map((e) => (
                      <tr
                        key={e.id}
                        className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20"
                      >
                        <td className="px-3 py-2 align-top whitespace-nowrap">
                          <Badge variant="secondary" className="text-[10px]">
                            {EXPENSE_LABELS[e.category]}
                          </Badge>
                        </td>
                        <td className="px-3 py-2 align-top">
                          <p className="font-medium text-sm">
                            {e.subcategory ?? e.vendorName ?? "—"}
                          </p>
                          {e.notes && (
                            <p className="text-[11px] text-muted-foreground italic mt-0.5 line-clamp-2">
                              {e.notes}
                            </p>
                          )}
                        </td>
                        <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap">
                          {e.vendorName ?? "—"}
                        </td>
                        <td className="px-3 py-2 align-top text-sm text-muted-foreground whitespace-nowrap">
                          {e.paidOnDate}
                        </td>
                        <td className="px-3 py-2 align-top text-right">
                          <span className="font-semibold font-display whitespace-nowrap">
                            {formatINR(e.amount)}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CollapsibleSection>
        </>
      )}
    </div>
  );
}

/**
 * One charge row in the "My charges" table. Renders as a {@code <tr>}
 * with columns:
 *   Category badge | Description (notes) | Status pill | Amount | Action
 *
 * <p>The Action column shows a Pay button on DUE / OVERDUE rows
 * (regardless of whether the society has UPI configured — the
 * destination /app/society/pay page handles the "no UPI yet" case
 * with an empty state). PAID rows show a green checkmark and the
 * paid-on date. WAIVED / other statuses render an empty action cell.
 */
function ChargeRow({
  row,
  config,
}: {
  row: FlatMaintenanceRow;
  config: SocietyConfig;
}) {
  const label = row.category ? CHARGE_LABELS[row.category] : "Other";
  const tone = STATUS_TONES[row.status] ?? "bg-muted text-muted-foreground";
  const isPaid = row.status === "PAID";
  const canPay =
    (row.status === "DUE" || row.status === "OVERDUE") && row.collectionId;

  return (
    <tr className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20">
      <td className="px-3 py-2 align-top whitespace-nowrap">
        <Badge variant="secondary" className="text-[10px]">
          {label}
        </Badge>
      </td>
      <td className="px-3 py-2 align-top">
        {row.notes ? (
          <p className="text-xs text-muted-foreground italic line-clamp-2">
            {row.notes}
          </p>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
      <td className="px-3 py-2 align-top whitespace-nowrap">
        <span
          className={`rounded-full text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 ${tone}`}
        >
          {row.status}
        </span>
        {isPaid && row.paidOn && (
          <span className="block text-[10px] text-muted-foreground mt-0.5">
            on {row.paidOn}
          </span>
        )}
      </td>
      <td className="px-3 py-2 align-top text-right whitespace-nowrap">
        <span className="font-semibold font-display">
          {formatINR(row.monthAmount)}
        </span>
      </td>
      <td className="px-3 py-2 align-top text-right whitespace-nowrap">
        {canPay && (
          <Button asChild variant="gradient" size="sm">
            <Link
              to={`/app/society/pay/${config.buildingId}/${row.collectionId}`}
            >
              Pay {formatINR(row.monthAmount)}
            </Link>
          </Button>
        )}
        {isPaid && (
          <CheckCircle2
            className="size-5 text-success inline-block"
            aria-label="Paid"
          />
        )}
      </td>
    </tr>
  );
}

