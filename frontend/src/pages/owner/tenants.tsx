import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ChevronRight, Users, Wallet } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import { useUserByAuth } from "@/hooks/use-user-by-auth";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/layout/page-header";
import { ContactPersonPopover } from "@/components/common/contact-person-popover";
import { formatDate, formatINR, initials, normalizeDocUrl } from "@/lib/utils";
import type { FlatResponseDTO, PaymentResponse } from "@/types/api";

export function TenantsPage() {
  const { authUserId } = useAuthStore();

  const buildingsQ = useQuery({
    queryKey: ["my-buildings", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const flatsQ = useQuery({
    queryKey: ["owner-all-flats", buildingsQ.data?.map((b) => b.buildingId).join(",")],
    queryFn: async () => {
      const buildings = buildingsQ.data ?? [];
      const all = await Promise.all(
        buildings.map((b) =>
          propertiesApi.flats
            .byBuilding(b.buildingId)
            .then((flats) =>
              flats.map((f) => ({ ...f, _buildingName: b.buildingName })),
            ),
        ),
      );
      return all.flat();
    },
    enabled: !!buildingsQ.data,
  });

  // ALL payments across every tenant the owner has — fetched ONCE for
  // the page. Each TenantCard slices its own rows out of this list
  // (filter by p.tenantId === flat.tenantId) instead of issuing N
  // separate /payments/tenant/{id} calls, which:
  //  (a) is wasteful (1 call vs N), and
  //  (b) doesn't work — /payments/tenant/{id} is gated to
  //      self-or-admin, so an owner calling it gets a 403 and the
  //      query returns no data (the bug that made every card show
  //      Outstanding=0). /payments/owner/{id} accepts the owner's
  //      authUserId via the same requireSelfOrAdmin guard, so this
  //      query succeeds and the data we need is already in the
  //      response — we just filter client-side.
  const paymentsQ = useQuery({
    queryKey: ["payments", "by-owner", authUserId],
    queryFn: () => paymentsApi.byOwner(authUserId!),
    enabled: !!authUserId,
    staleTime: 60_000,
  });

  const tenantedFlats = (flatsQ.data ?? []).filter((f) => f.tenantId);
  const allPayments = paymentsQ.data ?? [];

  // Per-tenant outstanding rollup, used for both the page-wide
  // summary banner AND the sort order of the cards. Keying by
  // tenantId rather than flatId so a tenant who moved between flats
  // still aggregates into one number.
  const outstandingByTenant = useMemo(() => {
    const map = new Map<string, number>();
    for (const p of allPayments) {
      if (p.status !== "PENDING" && p.status !== "OVERDUE") continue;
      const amt = Number(p.totalAmount ?? p.amount ?? 0);
      map.set(p.tenantId, (map.get(p.tenantId) ?? 0) + amt);
    }
    return map;
  }, [allPayments]);

  // Sort tenants with the largest outstanding first so the owner
  // sees who owes money at a glance. Tenants with no dues fall to
  // the bottom in their natural order.
  const sortedFlats = useMemo(() => {
    return [...tenantedFlats].sort((a, b) => {
      const aDue = outstandingByTenant.get(a.tenantId ?? "") ?? 0;
      const bDue = outstandingByTenant.get(b.tenantId ?? "") ?? 0;
      return bDue - aDue;
    });
  }, [tenantedFlats, outstandingByTenant]);

  // Portfolio-wide totals for the summary banner.
  const totalOutstanding = Array.from(outstandingByTenant.values()).reduce(
    (s, v) => s + v,
    0,
  );
  const tenantsWithDues = Array.from(outstandingByTenant.values()).filter(
    (v) => v > 0,
  ).length;

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Tenants"
        description="People living in your homes. Click any card to see their full activity."
      />

      {/* Portfolio-wide outstanding rollup. Surfaces the answer to
          "how much rent is owed across all my tenants right now?"
          at the very top of the page — same data each card carries,
          aggregated. The banner is muted when there are no dues so
          it doesn't draw attention when there's nothing to chase. */}
      {tenantedFlats.length > 0 && (
        <Card
          className={
            "mb-6 " +
            (totalOutstanding > 0
              ? "border-amber-500/40 bg-amber-50/40 dark:bg-amber-500/5"
              : "border-emerald-500/30 bg-emerald-50/30 dark:bg-emerald-500/5")
          }
        >
          <CardContent className="p-4 sm:p-5 flex items-center gap-3 flex-wrap">
            {totalOutstanding > 0 ? (
              <AlertTriangle className="size-5 text-amber-600 shrink-0" />
            ) : (
              <Wallet className="size-5 text-emerald-600 shrink-0" />
            )}
            <div className="flex-1 min-w-0">
              <p className="font-display font-semibold">
                {paymentsQ.isLoading
                  ? "Tallying outstanding rent…"
                  : totalOutstanding > 0
                    ? `${formatINR(totalOutstanding)} outstanding across ${tenantsWithDues} tenant${tenantsWithDues === 1 ? "" : "s"}`
                    : "All tenants are paid up."}
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Tenants with the largest unpaid balance appear first below.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {flatsQ.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-44 rounded-2xl" />
          ))}
        </div>
      )}

      {!flatsQ.isLoading && tenantedFlats.length === 0 && (
        <Card className="p-12 text-center">
          <Users className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            No tenants yet.
          </p>
          <p className="text-muted-foreground text-sm mt-1">
            Once you assign tenants to flats, they'll show up here.
          </p>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {sortedFlats.map((f) => (
          <TenantCard
            key={f.id}
            flat={f as FlatResponseDTO & { _buildingName?: string }}
            allPayments={allPayments}
            paymentsLoading={paymentsQ.isLoading}
          />
        ))}
      </div>
    </div>
  );
}

/**
 * One tenant tile. The whole card is a link to /owner/tenants/:tenantId
 * EXCEPT the contact-icon row at the bottom, which sits outside the link
 * so opening the popover doesn't trigger navigation.
 *
 * <p>Payments are NOT fetched here — the parent fans out a single
 * /payments/owner/{ownerId} call and passes the result down. See the
 * comment on the parent's paymentsQ for why this matters (the previous
 * per-card /payments/tenant/{id} call returned 403 for owners, so every
 * card silently rendered Outstanding=0).
 */
function TenantCard({
  flat,
  allPayments,
  paymentsLoading,
}: {
  flat: FlatResponseDTO & { _buildingName?: string };
  allPayments: PaymentResponse[];
  paymentsLoading: boolean;
}) {
  const tenantId = flat.tenantId!;
  const { user, fullName, isLoading } = useUserByAuth(tenantId);

  // Slice the owner's full payment list down to just this tenant's
  // rows. Filtering by tenantId alone (not also flatId) on purpose:
  // if a tenant moved between flats under the same owner, every
  // collected rupee from them should still roll up here.
  const tenantPayments = allPayments.filter((p) => p.tenantId === tenantId);
  const paid = tenantPayments
    .filter((p) => p.status === "PAID")
    .reduce((acc, p) => acc + Number(p.totalAmount ?? p.amount ?? 0), 0);
  const pending = tenantPayments
    .filter((p) => p.status === "PENDING" || p.status === "OVERDUE")
    .reduce((acc, p) => acc + Number(p.totalAmount ?? p.amount ?? 0), 0);

  return (
    <Card
      className={
        "overflow-hidden " +
        // Tenants with outstanding rent get a coloured top border so
        // they're spottable in the grid without reading each amount.
        (pending > 0
          ? "border-amber-500/50 ring-1 ring-amber-500/20"
          : "")
      }
    >
      {/* Clickable header / stats area */}
      <Link
        to={`/owner/tenants/${tenantId}`}
        className="block p-5 hover:bg-secondary/40 transition-colors"
      >
        <div className="flex items-center gap-3">
          <Avatar className="size-12">
            {user?.profilePictureUrl && (
              <AvatarImage src={normalizeDocUrl(user.profilePictureUrl)} />
            )}
            <AvatarFallback>
              {initials(fullName ?? tenantId.slice(0, 2))}
            </AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <p className="font-semibold truncate">
              {isLoading ? (
                <span className="inline-block h-4 w-28 rounded bg-secondary animate-pulse align-middle" />
              ) : (
                fullName ?? `Tenant ${tenantId.slice(0, 8)}…`
              )}
            </p>
            <p className="text-xs text-muted-foreground truncate">
              {flat._buildingName ?? "Building"} · {flat.flatNumber}
            </p>
          </div>
          <ChevronRight className="size-4 text-muted-foreground shrink-0" />
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
          <div>
            <p className="text-muted-foreground">Monthly rent</p>
            <p className="font-semibold mt-0.5">
              {formatINR(flat.rentAmount)}
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">Lease ends</p>
            <p className="font-semibold mt-0.5">
              {formatDate(flat.leaseEndDate) ?? "—"}
            </p>
          </div>
          {/* Bug "owner sees rent collected per tenant" — totals
              roll up every PAID payment for this tenant. Pending +
              overdue surface so the owner sees what's still due. */}
          <div>
            <p className="text-muted-foreground">Collected total</p>
            <p className="font-semibold mt-0.5 text-emerald-600">
              {paymentsLoading ? "…" : formatINR(paid)}
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">Outstanding</p>
            <p
              className={
                "font-semibold mt-0.5 " +
                (pending > 0 ? "text-amber-600" : "text-muted-foreground")
              }
            >
              {paymentsLoading ? "…" : formatINR(pending)}
            </p>
          </div>
        </div>
      </Link>

      {/* Action bar — outside the Link so dropdowns don't navigate.
          When the tenant has outstanding rent, surface a destructive
          pill BEFORE the Active badge so the owner sees "₹X due"
          even if they're scrolling past without reading the stats
          grid above. */}
      <CardContent className="px-5 pb-5 pt-0 flex items-center gap-2">
        <ContactPersonPopover authUserId={tenantId} variant="icon-mail" />
        <ContactPersonPopover authUserId={tenantId} variant="icon-phone" />
        {pending > 0 && (
          <Badge variant="destructive" className="ml-auto gap-1">
            <AlertTriangle className="size-3" /> {formatINR(pending)} due
          </Badge>
        )}
        <Badge variant="success" className={pending > 0 ? "" : "ml-auto"}>
          Active
        </Badge>
      </CardContent>
    </Card>
  );
}
