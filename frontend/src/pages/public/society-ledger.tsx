import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Building2 } from "lucide-react";
import { societyApi } from "@/lib/api/society";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Logo } from "@/components/layout/logo";
import { SocietyLedgerView } from "@/components/society/ledger-view";

/**
 * Public read-only society-ledger view. Reached via the shareable
 * link (https://anirudhhomes.in/society/view/{token}). No login —
 * the token in the URL is the only credential.
 *
 * <p>Thin wrapper around {@link SocietyLedgerView}. That component
 * owns the entire visual layout (filters, KPIs, chart, tables) and
 * is shared with the authenticated tenant Society "Details" tab so
 * both views stay pixel-identical and edits happen in one place.
 *
 * <p>This wrapper contributes:
 * <ol>
 *   <li>the token-based data source ({@code societyApi.publicLedger}),</li>
 *   <li>a fresh header with the Anirudh Homes logo (public visitors
 *       don't get the AppShell chrome),</li>
 *   <li>the society-display-name headline and its intro copy,</li>
 *   <li>the floating maintainer-contact widget in the bottom-left
 *       (tenants access their maintainer via the app so that widget
 *       stays public-only).</li>
 * </ol>
 */
export function PublicSocietyLedgerPage() {
  const { token } = useParams<{ token: string }>();

  // Single-shot fetch just for the headline (society name). The
  // <SocietyLedgerView> below runs its own multi-month queries — the
  // small extra call is fine (React Query dedupes when the key matches
  // one of the inner queries) and keeps the wrapper stateless.
  const headlineQ = useQuery({
    queryKey: ["public-ledger-headline", token],
    queryFn: () => societyApi.publicLedger(token!, undefined),
    enabled: !!token,
    retry: false,
    staleTime: 60_000,
  });

  const fetchLedger = useMemo(
    () => (month: string) => societyApi.publicLedger(token!, month),
    [token],
  );

  return (
    <div className="min-h-screen bg-secondary/30">
      <header className="border-b border-border/60 bg-background/85 backdrop-blur-xl">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <Logo />
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
        {headlineQ.isLoading ? (
          <Skeleton className="h-64 rounded-2xl" />
        ) : headlineQ.isError || !headlineQ.data ? (
          <EmptyState
            variant="info"
            icon={Building2}
            title="Couldn't load this ledger"
            description="The link may have been rotated by the owner or has never existed. Ask the maintainer for a fresh shareable link."
          />
        ) : (
          <>
            <h1 className="font-display font-bold text-2xl sm:text-3xl">
              {headlineQ.data.societyDisplayName ?? "Society ledger"}
            </h1>
            <p className="text-muted-foreground mt-1 text-sm mb-6">
              View society expenses, maintenance spending, and fund
              balances with complete transparency.
            </p>

            <SocietyLedgerView
              fetchLedger={fetchLedger}
              cacheKey={`public-ledger-${token}`}
              showMaintainerWidget
              bottomWidgetPad
            />

            <p className="text-xs text-muted-foreground mt-6 text-center">
              Powered by{" "}
              <span className="font-semibold">Anirudh Homes</span> · Public
              view for residents and stakeholders.
            </p>
          </>
        )}
      </main>
    </div>
  );
}
