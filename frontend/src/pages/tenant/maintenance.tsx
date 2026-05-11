import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, Loader2, Plus, Wrench } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { maintenanceApi } from "@/lib/api/maintenance";
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

export function MaintenancePage() {
  const { authUserId } = useAuthStore();
  const q = useQuery({
    queryKey: ["my-maintenance", authUserId],
    queryFn: () => maintenanceApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  const items = q.data ?? [];
  const open = items.filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  );
  const closed = items.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Maintenance"
        description="Tell us what's wrong — we'll get a technician on it."
        actions={
          <Button asChild variant="gradient">
            <Link to="/app/maintenance/new">
              <Plus /> Raise request
            </Link>
          </Button>
        }
      />

      <section className="mb-8">
        <h2 className="font-display font-semibold text-lg mb-3">
          Active ({open.length})
        </h2>
        {q.isLoading && <Skeleton className="h-32 rounded-2xl" />}
        {!q.isLoading && open.length === 0 && (
          <Card className="p-10 text-center">
            <Wrench className="size-10 mx-auto text-muted-foreground" />
            <p className="font-display font-semibold text-lg mt-3">
              Nothing to fix.
            </p>
            <p className="text-muted-foreground text-sm mt-1">
              Your home is in good shape.
            </p>
          </Card>
        )}
        <div className="space-y-3">
          {open.map((r) => (
            <RequestRow key={r.id} request={r} />
          ))}
        </div>
      </section>

      {closed.length > 0 && (
        <section>
          <h2 className="font-display font-semibold text-lg mb-3">Resolved</h2>
          <div className="space-y-2">
            {closed.map((r) => (
              <RequestRow key={r.id} request={r} subtle />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function RequestRow({
  request,
  subtle,
}: {
  request: MaintenanceRequestResponse;
  subtle?: boolean;
}) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();

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
      return maintenanceApi.comment(request.id, authUserId!, comment);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-maintenance"] });
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

  // Soft cooldown: don't let the user mash the button (last reminder < 5 min ago)
  const recently =
    lastReminder?.timestamp &&
    Date.now() - new Date(lastReminder.timestamp).getTime() < 5 * 60 * 1000;

  const isClosed = request.status === "RESOLVED" || request.status === "CLOSED";

  return (
    <Card className={subtle ? "opacity-80" : ""}>
      <CardContent className="p-5 flex items-start gap-4">
        <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
          <Wrench className="size-4" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-3 flex-wrap">
            <div className="min-w-0">
              <p className="font-medium truncate">{request.title}</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                {request.category ?? request.complaintCategory ?? "—"} ·{" "}
                {request.priority} priority ·{" "}
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

          {!isClosed && (
            <div className="mt-3 flex items-center gap-3">
              <Button
                size="sm"
                variant="outline"
                onClick={() => remindMutation.mutate()}
                disabled={remindMutation.isPending || Boolean(recently)}
                title={
                  recently
                    ? "You just reminded the owner — wait a bit before reminding again."
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
