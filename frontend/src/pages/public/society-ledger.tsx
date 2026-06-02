import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Building2, Calendar, Droplets, Wallet, Wrench } from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Logo } from "@/components/layout/logo";
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
 * Public read-only ledger view. Reached via the shareable link
 * (e.g. https://anirudhhomes.in/society/view/{token}). No login
 * required — the token in the URL is the only credential.
 *
 * <p>Rendered without the AppShell so it's a clean standalone page
 * the residents can open on phones, share screenshots from, etc.
 */
export function PublicSocietyLedgerPage() {
  const { token } = useParams<{ token: string }>();
  const [month, setMonth] = useState(currentMonth());

  const ledgerQ = useQuery({
    queryKey: ["public-ledger", token, month],
    queryFn: () => societyApi.publicLedger(token!, month),
    enabled: !!token,
    retry: false,
  });

  return (
    <div className="min-h-screen bg-secondary/30">
      <header className="border-b border-border/60 bg-background/85 backdrop-blur-xl">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <Logo />
          <Badge variant="secondary">Public ledger</Badge>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        {ledgerQ.isLoading ? (
          <Skeleton className="h-64 rounded-2xl" />
        ) : ledgerQ.isError ? (
          <EmptyState
            variant="info"
            icon={Building2}
            title="Couldn't load this ledger"
            description="The link may have been rotated by the owner or has never existed. Ask the maintainer for a fresh shareable link."
          />
        ) : (
          <>
            <h1 className="font-display font-bold text-3xl">
              {ledgerQ.data!.societyDisplayName ?? "Society ledger"}
            </h1>
            <p className="text-muted-foreground mt-1 text-sm">
              A transparent view of common-area expenses. Read-only — for
              residents of this building.
            </p>

            <div className="flex items-center gap-3 mt-6 mb-4">
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
                value={formatINR(ledgerQ.data!.expensesThisMonth)}
                tone="destructive"
              />
              <Kpi
                icon={Wallet}
                label="Lifetime balance"
                value={formatINR(ledgerQ.data!.balanceLifetime)}
                tone={
                  ledgerQ.data!.balanceLifetime >= 0
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

                {!ledgerQ.data!.expenses.length ? (
                  <EmptyState
                    variant="info"
                    icon={Wrench}
                    title="No expenses recorded for this month"
                    description="The maintainer hasn't added any bills yet. Check back later."
                  />
                ) : (
                  <div className="space-y-2">
                    {ledgerQ.data!.expenses.map((e) => (
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

            <p className="text-xs text-muted-foreground mt-6 text-center">
              Powered by{" "}
              <span className="font-semibold">Anirudh Homes</span> · this is a
              public read-only link. Rotate it from the dashboard if it leaks.
            </p>
          </>
        )}
      </main>
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
