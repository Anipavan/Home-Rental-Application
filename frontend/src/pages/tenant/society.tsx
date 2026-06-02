import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Building2, Calendar, Droplets, Wallet, Wrench } from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import type { ExpenseCategory } from "@/types/api";

const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  UTILITY: "Utility",
  SALARY: "Staff salary",
  SUPPLIES: "Supplies",
  REPAIR_COMMON: "Common-area repair",
  INSURANCE: "Insurance",
  TAX: "Tax / govt fees",
  OTHER: "Other",
};

const currentMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

/**
 * Tenant-side read-only view of their building's society ledger.
 * Same data shape as the owner's view; nothing mutable. Powers the
 * transparency promise — every resident can see exactly where the
 * monthly maintenance money went.
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

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Society ledger"
        description="Transparent record of common-area expenses for your building."
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
            Viewing <span className="font-semibold text-foreground">
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
                            {CATEGORY_LABELS[e.category]}
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
