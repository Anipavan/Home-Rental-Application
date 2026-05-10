import { useQuery } from "@tanstack/react-query";
import {
  Inbox,
  HelpCircle,
  Calendar,
  ArrowUpRight,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import {
  supportTicketsApi,
  visitRequestsApi,
  type SupportTicket,
  type VisitRequest,
} from "@/lib/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * "My Requests" — a unified inbox of every request the tenant has sent
 * to the platform / owner. Shown on the My Home page so it's the first
 * thing the tenant sees alongside their flat.
 *
 * <p>Aggregates two data sources:
 * <ul>
 *   <li>{@code supportTicketsApi.byUser(authUserId)} — every contact-
 *       owner enquiry, every "Contact support" form submission. Status
 *       transitions: OPEN → IN_PROGRESS → RESOLVED → CLOSED.</li>
 *   <li>{@code visitRequestsApi.byUser(authUserId)} — every "Schedule a
 *       visit" booking from the public property pages. Status
 *       transitions: PENDING → CONFIRMED → COMPLETED, or CANCELLED.</li>
 * </ul>
 *
 * <p>The two streams are merged into a single chronological list (latest
 * first) and the top 5 are rendered. If the user has nothing yet we
 * show a friendly empty state pointing them at the public listings.
 */
export function MyRequestsCard() {
  const { authUserId } = useAuthStore();

  const ticketsQ = useQuery({
    queryKey: ["my-support-tickets", authUserId],
    queryFn: () => supportTicketsApi.byUser(authUserId!, 0, 20),
    enabled: !!authUserId,
    staleTime: 30_000,
  });

  const visitsQ = useQuery({
    queryKey: ["my-visit-requests", authUserId],
    queryFn: () => visitRequestsApi.byUser(authUserId!, 0, 20),
    enabled: !!authUserId,
    staleTime: 30_000,
  });

  const loading = ticketsQ.isLoading || visitsQ.isLoading;

  const items = mergeAndRank(
    ticketsQ.data?.content ?? [],
    visitsQ.data?.content ?? [],
  ).slice(0, 5);

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <div className="flex items-center justify-between mb-1">
          <h3 className="font-display font-semibold text-lg flex items-center gap-2">
            <Inbox className="size-4 text-primary" /> My requests
          </h3>
          {!loading && items.length > 0 && (
            <Badge variant="secondary" className="text-[10px]">
              {items.length} recent
            </Badge>
          )}
        </div>
        <p className="text-sm text-muted-foreground">
          Enquiries, visit bookings, and support tickets you've sent — with
          their current status from our team or the owner.
        </p>

        <div className="mt-5 space-y-2">
          {loading && (
            <>
              <Skeleton className="h-16 rounded-xl" />
              <Skeleton className="h-16 rounded-xl" />
            </>
          )}

          {!loading && items.length === 0 && (
            <div className="rounded-xl border border-dashed p-6 text-center">
              <p className="font-medium text-sm">No requests yet</p>
              <p className="text-xs text-muted-foreground mt-1">
                Browse a property and tap "Contact owner" or "Schedule a visit"
                — the request will land here once you submit it.
              </p>
            </div>
          )}

          {items.map((it) => (
            <RequestRow key={`${it.kind}-${it.id}`} item={it} />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

/* ───────────────── inner types & helpers ───────────────── */

type Unified =
  | { kind: "support"; id: string; title: string; status: SupportTicket["status"]; when: string; contextUrl?: string }
  | { kind: "visit";   id: string; title: string; status: VisitRequest["status"];  when: string; contextUrl?: string; preferredAt?: string | null };

function mergeAndRank(
  tickets: SupportTicket[],
  visits: VisitRequest[],
): Unified[] {
  const t: Unified[] = tickets.map((x) => ({
    kind: "support",
    id: x.id,
    title: x.subject,
    status: x.status,
    when: x.createdAt ?? "",
    contextUrl: x.contextUrl,
  }));
  const v: Unified[] = visits.map((x) => ({
    kind: "visit",
    id: x.id,
    title: x.propertyLabel ?? `Flat ${x.flatId}`,
    status: x.status,
    when: x.createdAt ?? "",
    contextUrl: x.contextUrl,
    preferredAt: x.preferredAt,
  }));
  return [...t, ...v].sort(
    (a, b) => new Date(b.when).getTime() - new Date(a.when).getTime(),
  );
}

function RequestRow({ item }: { item: Unified }) {
  return (
    <div className="rounded-xl border bg-secondary/30 p-4 flex items-start gap-3">
      <div className="size-10 rounded-lg bg-background grid place-items-center border shrink-0">
        {item.kind === "visit" ? (
          <Calendar className="size-4 text-primary" />
        ) : (
          <HelpCircle className="size-4 text-primary" />
        )}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <p className="font-medium text-sm truncate">{item.title}</p>
          <KindBadge kind={item.kind} />
          <StatusBadge item={item} />
        </div>
        <p className="text-[11px] text-muted-foreground mt-0.5">
          {item.kind === "visit" && item.preferredAt && (
            <>
              For {new Date(item.preferredAt).toLocaleString()} ·{" "}
            </>
          )}
          Submitted {item.when ? new Date(item.when).toLocaleString() : "—"}
        </p>
        {item.contextUrl && (
          <p className="text-[11px] text-muted-foreground mt-1 inline-flex items-center gap-1">
            <ArrowUpRight className="size-3" />
            from <code>{item.contextUrl}</code>
          </p>
        )}
      </div>
    </div>
  );
}

function KindBadge({ kind }: { kind: Unified["kind"] }) {
  if (kind === "visit") {
    return (
      <Badge variant="default" className="text-[10px]">Visit</Badge>
    );
  }
  return (
    <Badge variant="secondary" className="text-[10px]">Enquiry</Badge>
  );
}

function StatusBadge({ item }: { item: Unified }) {
  if (item.kind === "visit") {
    switch (item.status) {
      case "PENDING":
        return <Badge variant="warning" className="text-[10px]">Pending</Badge>;
      case "CONFIRMED":
        return <Badge variant="default" className="text-[10px]">Confirmed</Badge>;
      case "COMPLETED":
        return <Badge variant="success" className="text-[10px]">Completed</Badge>;
      case "CANCELLED":
        return <Badge variant="destructive" className="text-[10px]">Cancelled</Badge>;
    }
  }
  switch (item.status) {
    case "OPEN":
      return <Badge variant="warning" className="text-[10px]">Open</Badge>;
    case "IN_PROGRESS":
      return <Badge variant="secondary" className="text-[10px]">In progress</Badge>;
    case "RESOLVED":
      return <Badge variant="success" className="text-[10px]">Resolved</Badge>;
    case "CLOSED":
      return <Badge variant="secondary" className="text-[10px]">Closed</Badge>;
  }
}
