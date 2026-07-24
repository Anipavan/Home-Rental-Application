import { Link } from "react-router-dom";
import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query";
import {
  Bell,
  Loader2,
  MessageSquareWarning,
  Plus,
  ShieldAlert,
  Wrench,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi, maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { relativeFromNow } from "@/lib/utils";
import type { MaintenanceRequestResponse } from "@/types/api";

const REMINDER_PREFIX = "[REMINDER]";

/**
 * Tenant Complaints — unified list.
 *
 * Backend already stores maintenance requests + grievance complaints
 * in the same table (discriminated by {@code kind}). Old UX split them
 * into two menu entries which routinely confused tenants ("is a leaky
 * tap a complaint or a maintenance request?"). This page pulls both
 * streams and shows one chronological list. The category chip on each
 * row tells the tenant which flavour it is; the "Send reminder"
 * affordance carries across from the old maintenance page for open
 * items of either kind.
 */
export function ComplaintsPage() {
  const { authUserId } = useAuthStore();

  // Two independent queries — kept separate so React Query's cache
  // keys keep matching what /app/dashboard uses. Parallel fetch; the
  // combined list is derived from the two data blobs. Both endpoints
  // are cheap (~50 ms each) so the wall-clock latency is unchanged.
  const [maintQ, complaintQ] = useQueries({
    queries: [
      {
        queryKey: ["my-maintenance", authUserId],
        queryFn: () => maintenanceApi.byTenant(authUserId!),
        enabled: !!authUserId,
      },
      {
        queryKey: ["my-complaints", authUserId],
        queryFn: () => complaintsApi.byTenant(authUserId!),
        enabled: !!authUserId,
      },
    ],
  });

  const loading = maintQ.isLoading || complaintQ.isLoading;
  const all: MaintenanceRequestResponse[] = [
    ...(maintQ.data ?? []),
    ...(complaintQ.data ?? []),
  ];
  const sorted = [...all].sort((a, b) =>
    (b.createdAt ?? "").localeCompare(a.createdAt ?? ""),
  );
  const open = sorted.filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  );
  const closed = sorted.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Complaints"
        description="Repairs, noise, safety — anything that needs someone's attention. One place, one queue."
        actions={
          <Button asChild variant="gradient">
            <Link to="/app/complaints/new">
              <Plus /> New complaint
            </Link>
          </Button>
        }
      />

      <section className="mb-8">
        <h2 className="font-display font-semibold text-lg mb-3">
          Active ({open.length})
        </h2>
        {loading && <Skeleton className="h-32 rounded-2xl" />}
        {!loading && open.length === 0 && (
          <Card className="p-10 text-center">
            <ShieldAlert className="size-10 mx-auto text-muted-foreground" />
            <p className="font-display font-semibold text-lg mt-3">
              All quiet on the home front.
            </p>
            <p className="text-muted-foreground text-sm mt-1">
              Nothing open — long may it last.
            </p>
          </Card>
        )}
        <div className="space-y-3">
          {open.map((r) => (
            <Row key={r.id} request={r} />
          ))}
        </div>
      </section>

      {closed.length > 0 && (
        <section>
          <h2 className="font-display font-semibold text-lg mb-3">Closed</h2>
          <div className="space-y-2">
            {closed.map((r) => (
              <Row key={r.id} request={r} subtle />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

/**
 * One row — works for both kinds. Icon + query-key + reminder API
 * pick themselves off {@code request.kind}, so a maintenance row
 * still nudges the maintenanceApi cache and a complaint row nudges
 * the complaintsApi cache. Row body + reminder button + "assigned
 * to" chip is otherwise identical.
 */
function Row({
  request,
  subtle,
}: {
  request: MaintenanceRequestResponse;
  subtle?: boolean;
}) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();

  const isMaintenance = request.kind === "MAINTENANCE";
  const api = isMaintenance ? maintenanceApi : complaintsApi;
  const cacheKey = isMaintenance ? "my-maintenance" : "my-complaints";
  const Icon = isMaintenance ? Wrench : MessageSquareWarning;
  const iconTone = isMaintenance
    ? "bg-amber-500/15 text-amber-600"
    : "bg-rose-500/15 text-rose-600";

  const reminderCount = (request.comments ?? []).filter((c) =>
    c.comment.startsWith(REMINDER_PREFIX),
  ).length;
  const lastReminder = (request.comments ?? [])
    .filter((c) => c.comment.startsWith(REMINDER_PREFIX))
    .sort((a, b) => (b.timestamp ?? "").localeCompare(a.timestamp ?? ""))[0];

  const remindMutation = useMutation({
    mutationFn: () => {
      const next = reminderCount + 1;
      const comment = `${REMINDER_PREFIX} Tenant follow-up #${next} — please look into this.`;
      return api.comment(request.id, authUserId!, comment);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [cacheKey] });
      toast({
        title: "Reminder sent",
        description: "Your owner will see this on their next visit.",
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't send reminder",
        description: extractErrorMessage(e),
      }),
  });

  // 5-min mash-guard on the reminder button — otherwise a frustrated
  // tenant could fire dozens per minute and drown the owner's bell.
  const recently =
    lastReminder?.timestamp &&
    Date.now() - new Date(lastReminder.timestamp).getTime() < 5 * 60 * 1000;
  const isClosed = request.status === "RESOLVED" || request.status === "CLOSED";
  const categoryLabel = prettyCategory(
    request.category ?? request.complaintCategory ?? "OTHER",
  );

  return (
    <Card
      className={`transition-colors hover:border-primary/40 ${subtle ? "opacity-80" : ""}`}
    >
      <CardContent className="p-5 flex items-start gap-4">
        <div
          className={`size-10 rounded-lg grid place-items-center shrink-0 ${iconTone}`}
        >
          <Icon className="size-4" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-3 flex-wrap">
            <div className="min-w-0">
              <Link
                to={`/app/complaints/${request.id}`}
                className="font-medium hover:underline block truncate"
              >
                {request.title}
              </Link>
              <p className="text-xs text-muted-foreground mt-0.5">
                <span className="font-medium text-foreground/70">
                  {isMaintenance ? "Repair" : "Complaint"}
                </span>{" "}
                · {categoryLabel} · {request.priority} priority ·{" "}
                {relativeFromNow(request.createdAt)}
                {reminderCount > 0 && (
                  <>
                    {" · "}
                    <span className="text-warning">
                      Reminded {reminderCount}×
                    </span>
                  </>
                )}
              </p>
            </div>
            <StatusBadge status={request.status} />
          </div>
          <p className="text-sm text-muted-foreground mt-2 line-clamp-2">
            {request.description}
          </p>
          {request.requestNumber && (
            <p className="text-[11px] text-muted-foreground mt-2 tracking-wide">
              Reference: {request.requestNumber}
            </p>
          )}

          {!isClosed && (
            <div className="mt-3 flex items-center gap-3 flex-wrap">
              <Button
                size="sm"
                variant="outline"
                onClick={() => remindMutation.mutate()}
                disabled={remindMutation.isPending || Boolean(recently)}
                title={
                  recently
                    ? "You just reminded them — wait a bit before nudging again."
                    : undefined
                }
              >
                {remindMutation.isPending ? (
                  <Loader2 className="animate-spin" />
                ) : (
                  <Bell />
                )}
                {recently ? "Reminded just now" : "Send reminder"}
              </Button>
              <Button asChild size="sm" variant="ghost">
                <Link to={`/app/complaints/${request.id}`}>Open</Link>
              </Button>
              {lastReminder?.timestamp && (
                <span className="text-xs text-muted-foreground">
                  Last reminder {relativeFromNow(lastReminder.timestamp)}
                </span>
              )}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function StatusBadge({ status }: { status: string }) {
  if (status === "RESOLVED" || status === "CLOSED")
    return <Badge variant="success">{status}</Badge>;
  if (status === "IN_PROGRESS")
    return <Badge variant="warning">In progress</Badge>;
  return <Badge>Open</Badge>;
}

function prettyCategory(value: string): string {
  if (!value) return "—";
  return value
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
