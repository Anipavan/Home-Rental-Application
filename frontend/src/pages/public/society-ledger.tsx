import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Building2,
  Calendar,
  Droplets,
  Mail,
  Phone,
  User,
  Wallet,
  Wrench,
} from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Logo } from "@/components/layout/logo";
import { formatINR } from "@/lib/utils";
import type {
  ExpenseCategory,
  FlatChargeCategory,
  PublicFlatBill,
  SocietyLedger,
} from "@/types/api";

const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  UTILITY: "Utility",
  SALARY: "Staff salary",
  SUPPLIES: "Supplies",
  REPAIR_COMMON: "Common-area repair",
  INSURANCE: "Insurance",
  TAX: "Tax / govt fees",
  OTHER: "Other",
};

/** Per-flat charge categories shown on the public table. Same set
 *  as the maintainer dashboard — Gas/Electricity excluded because
 *  they aren't society-collected. */
const FLAT_CHARGE_COLUMNS: FlatChargeCategory[] = [
  "MAINTENANCE",
  "WATER_BILL",
  "COMMON_AREA_SHARE",
  "OTHER",
];

const FLAT_CHARGE_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

/** Bucket → colour for the overall-status badge on the per-flat row. */
const STATUS_BADGE_CLASS: Record<PublicFlatBill["overallStatus"], string> = {
  SETTLED: "bg-success/20 text-success",
  PARTIAL: "bg-warning/20 text-warning",
  PENDING: "bg-destructive/20 text-destructive",
  NONE: "bg-muted text-muted-foreground",
};

const STATUS_BADGE_LABEL: Record<PublicFlatBill["overallStatus"], string> = {
  SETTLED: "Settled",
  PARTIAL: "Partial",
  PENDING: "Pending",
  NONE: "Not billed",
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

            {/* Maintainer contact */}
            <MaintainerContactCard ledger={ledgerQ.data!} />

            {/* Per-flat bills table */}
            <FlatBillsCard ledger={ledgerQ.data!} month={month} />

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

/**
 * Maintainer contact card on the public ledger.
 *
 * <p>Surfaces the person responsible for the books so residents can
 * reach out without needing an Anirudh Homes account. Renders an
 * empty state if user-service was unreachable when the backend built
 * the response (maintainerName=null) so the page never breaks.
 *
 * <p>Phone + email render as click-to-call / click-to-mail. We
 * intentionally don't render any other person's contact info here —
 * just the maintainer.
 */
function MaintainerContactCard({ ledger }: { ledger: SocietyLedger }) {
  const hasAny =
    ledger.maintainerName || ledger.maintainerPhone || ledger.maintainerEmail;

  return (
    <Card className="mb-6">
      <CardContent className="p-5">
        <div className="flex items-start gap-3">
          <div className="rounded-full bg-primary/10 p-2 shrink-0">
            <User className="size-5 text-primary" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs uppercase tracking-wider text-muted-foreground font-mono">
              Society maintainer
            </p>
            {hasAny ? (
              <>
                {ledger.maintainerName && (
                  <p className="font-semibold text-base mt-0.5">
                    {ledger.maintainerName}
                  </p>
                )}
                <div className="mt-2 space-y-1 text-sm">
                  {ledger.maintainerPhone && (
                    <div className="flex items-center gap-2">
                      <Phone className="size-3.5 text-muted-foreground" />
                      <a
                        href={`tel:${ledger.maintainerPhone}`}
                        className="text-primary underline-offset-2 hover:underline"
                      >
                        {ledger.maintainerPhone}
                      </a>
                    </div>
                  )}
                  {ledger.maintainerEmail && (
                    <div className="flex items-center gap-2">
                      <Mail className="size-3.5 text-muted-foreground" />
                      <a
                        href={`mailto:${ledger.maintainerEmail}`}
                        className="text-primary underline-offset-2 hover:underline break-all"
                      >
                        {ledger.maintainerEmail}
                      </a>
                    </div>
                  )}
                </div>
                <p className="text-[11px] text-muted-foreground mt-2">
                  Contact the maintainer for any clarification on a
                  charge or the ledger entries below.
                </p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground mt-0.5">
                Contact information will appear here once available.
              </p>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * Per-flat bills table on the public ledger.
 *
 * <p>Rows = flats (by flat number — NO tenant name or any other
 * identifying field), columns = each FlatChargeCategory + a Total
 * Due + overall status. Same column set as the maintainer dashboard
 * minus Gas/Electricity. The "no individual data" promise is
 * preserved: a stranger with the link can see that Flat 002 still
 * owes ₹2,000 but can't see WHO lives in Flat 002.
 */
function FlatBillsCard({
  ledger,
  month,
}: {
  ledger: SocietyLedger;
  month: string;
}) {
  const bills = ledger.flatBills ?? [];
  return (
    <Card className="mb-6">
      <CardContent className="p-6">
        <h3 className="font-display font-semibold text-lg mb-4">
          Per-flat bills — {month}
        </h3>

        {bills.length === 0 ? (
          <EmptyState
            variant="info"
            icon={Building2}
            title="No flats in this building yet"
            description="Once the owner adds flats and the maintainer records charges, they'll appear here."
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border/60">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="bg-secondary/40 border-b border-border/60">
                  <th className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground">
                    Flat
                  </th>
                  {FLAT_CHARGE_COLUMNS.map((c) => (
                    <th
                      key={c}
                      className="text-left px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap"
                    >
                      {FLAT_CHARGE_LABELS[c]}
                    </th>
                  ))}
                  <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    Total Due
                  </th>
                  <th className="text-right px-3 py-2 font-semibold text-[11px] uppercase tracking-wider text-muted-foreground whitespace-nowrap">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody>
                {bills.map((bill) => (
                  <FlatBillRow key={bill.flatNumber} bill={bill} />
                ))}
              </tbody>
            </table>
          </div>
        )}

        <p className="text-[11px] text-muted-foreground mt-3">
          Flat numbers only — no tenant identifying data shown on this
          public view.
        </p>
      </CardContent>
    </Card>
  );
}

/** One row of the public per-flat bills table. */
function FlatBillRow({ bill }: { bill: PublicFlatBill }) {
  // Index this flat's charges by category for O(1) cell lookup.
  const byCategory = new Map<FlatChargeCategory, PublicFlatBill["charges"][number]>();
  for (const c of bill.charges) {
    byCategory.set(c.category, c);
  }
  return (
    <tr className="border-b border-border/60 last:border-b-0 hover:bg-secondary/20">
      <td className="px-3 py-2 align-top">
        <Badge variant="outline" className="font-mono text-[11px]">
          {bill.flatNumber}
        </Badge>
      </td>
      {FLAT_CHARGE_COLUMNS.map((c) => {
        const charge = byCategory.get(c);
        return (
          <td key={c} className="px-3 py-2 align-top">
            {charge ? (
              <div>
                <p className="font-semibold font-display text-sm">
                  {formatINR(charge.amount)}
                </p>
                <span
                  className={`block mt-0.5 rounded-full text-[9px] font-semibold uppercase tracking-wide px-1.5 py-0 w-fit ${
                    charge.status === "PAID"
                      ? "bg-success/20 text-success"
                      : charge.status === "OVERDUE"
                        ? "bg-destructive/20 text-destructive"
                        : charge.status === "WAIVED"
                          ? "bg-muted text-muted-foreground"
                          : "bg-warning/20 text-warning"
                  }`}
                >
                  {charge.status}
                </span>
              </div>
            ) : (
              <span className="text-xs text-muted-foreground">—</span>
            )}
          </td>
        );
      })}
      <td className="px-3 py-2 align-top text-right">
        {bill.totalDue > 0 ? (
          <span className="font-semibold text-destructive whitespace-nowrap">
            {formatINR(bill.totalDue)}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )}
      </td>
      <td className="px-3 py-2 align-top text-right">
        <span
          className={`inline-block rounded-full text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 ${STATUS_BADGE_CLASS[bill.overallStatus]}`}
        >
          {STATUS_BADGE_LABEL[bill.overallStatus]}
        </span>
      </td>
    </tr>
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
