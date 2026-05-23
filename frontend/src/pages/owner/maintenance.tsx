import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/stores/auth-store";
import { maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Wrench, AlertTriangle, MessageSquare, Clock } from "lucide-react";
import { relativeFromNow } from "@/lib/utils";
import { toast } from "@/hooks/use-toast";
import type {
  MaintenancePriority,
  MaintenanceRequestResponse,
  MaintenanceStatus,
} from "@/types/api";

export function OwnerMaintenancePage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["owner-maintenance", authUserId],
    queryFn: () => maintenanceApi.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: MaintenanceStatus }) =>
      maintenanceApi.setStatus(id, status, authUserId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-maintenance"] });
      toast({ title: "Status updated" });
    },
  });

  const all = q.data ?? [];
  const open = all.filter((r) => r.status === "OPEN");
  const inProgress = all.filter((r) => r.status === "IN_PROGRESS");
  const resolved = all.filter((r) => r.status === "RESOLVED" || r.status === "CLOSED");

  const grid = (items: MaintenanceRequestResponse[]) => {
    if (q.isLoading)
      return (
        <div className="grid gap-3 lg:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-2xl" />
          ))}
        </div>
      );
    if (items.length === 0)
      return (
        <Card className="p-12 text-center text-muted-foreground">
          Nothing here.
        </Card>
      );
    return (
      <div className="grid gap-3 lg:grid-cols-2">
        {items.map((r) => (
          <RequestCard
            key={r.id}
            request={r}
            onMark={(s) => setStatus.mutate({ id: r.id, status: s })}
          />
        ))}
      </div>
    );
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Maintenance queue"
        description="Tickets across every flat you own."
      />

      <Tabs defaultValue="open">
        <TabsList>
          <TabsTrigger value="open">Open ({open.length})</TabsTrigger>
          <TabsTrigger value="progress">In progress ({inProgress.length})</TabsTrigger>
          <TabsTrigger value="resolved">Resolved ({resolved.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="open">{grid(open)}</TabsContent>
        <TabsContent value="progress">{grid(inProgress)}</TabsContent>
        <TabsContent value="resolved">{grid(resolved)}</TabsContent>
      </Tabs>
    </div>
  );
}

function RequestCard({
  request,
  onMark,
}: {
  request: MaintenanceRequestResponse;
  onMark: (s: MaintenanceStatus) => void;
}) {
  /**
   * Local "View thread" dialog state. Previously the View thread button
   * had no onClick handler at all — the click was a no-op. The dialog
   * shows the full description, the comment thread, and the status
   * history, all from data already returned by /maintenance/owner.
   */
  const [threadOpen, setThreadOpen] = useState(false);

  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div
              className={`size-10 rounded-lg grid place-items-center shrink-0 ${
                request.priority === "CRITICAL"
                  ? "bg-destructive/10 text-destructive"
                  : "bg-primary/10 text-primary"
              }`}
            >
              {request.priority === "CRITICAL" ? (
                <AlertTriangle className="size-4" />
              ) : (
                <Wrench className="size-4" />
              )}
            </div>
            <div className="min-w-0">
              <p className="font-medium truncate">{request.title}</p>
              <p className="text-xs text-muted-foreground">
                Flat #{request.flatId} ·{" "}
                {request.category ?? request.complaintCategory ?? "—"} ·{" "}
                {relativeFromNow(request.createdAt)}
              </p>
            </div>
          </div>
          <PriorityBadge priority={request.priority} />
        </div>
        <p className="text-sm text-muted-foreground mt-3 line-clamp-2">
          {request.description}
        </p>
        <div className="mt-4 flex items-center gap-2">
          {request.status === "OPEN" && (
            <Button size="sm" onClick={() => onMark("IN_PROGRESS")}>
              Start
            </Button>
          )}
          {request.status === "IN_PROGRESS" && (
            <Button size="sm" onClick={() => onMark("RESOLVED")}>
              Mark resolved
            </Button>
          )}
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setThreadOpen(true)}
          >
            View thread
          </Button>
        </div>
      </CardContent>

      {/* Thread dialog — full description + comments + status history.
          Comments + history come from the same response payload so no
          extra API round-trip is needed when opening. */}
      <ThreadDialog
        open={threadOpen}
        onOpenChange={setThreadOpen}
        request={request}
      />
    </Card>
  );
}

function PriorityBadge({ priority }: { priority: MaintenancePriority }) {
  if (priority === "CRITICAL")
    return <Badge variant="destructive">{priority}</Badge>;
  if (priority === "HIGH") return <Badge variant="warning">{priority}</Badge>;
  return <Badge variant="secondary">{priority}</Badge>;
}

function StatusBadge({ status }: { status: MaintenanceStatus }) {
  if (status === "RESOLVED" || status === "CLOSED")
    return <Badge variant="success">{status}</Badge>;
  if (status === "IN_PROGRESS") return <Badge variant="warning">{status}</Badge>;
  return <Badge variant="secondary">{status}</Badge>;
}

/**
 * Dialog showing the full thread for a maintenance request — the long
 * description, every comment, every status transition. Nothing here is
 * editable from the owner side: the owner acts on the ticket via the
 * Start / Mark resolved buttons on the parent card. The dialog is
 * read-only ("View thread", literally).
 */
function ThreadDialog({
  open,
  onOpenChange,
  request,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  request: MaintenanceRequestResponse;
}) {
  const comments = request.comments ?? [];
  const history = request.history ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Wrench className="size-4 text-primary" />
            {request.title}
          </DialogTitle>
          <DialogDescription>
            Flat #{request.flatId} ·{" "}
            {request.category ?? request.complaintCategory ?? "—"} · raised{" "}
            {relativeFromNow(request.createdAt)}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          {/* Status + priority pills */}
          <div className="flex items-center gap-2">
            <StatusBadge status={request.status} />
            <PriorityBadge priority={request.priority} />
            {request.requestNumber && (
              <span className="text-xs text-muted-foreground">
                #{request.requestNumber}
              </span>
            )}
          </div>

          {/* Description — full, no line-clamp */}
          <section>
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-1.5">
              Description
            </h4>
            <p className="text-sm whitespace-pre-wrap">{request.description}</p>
          </section>

          {/* Comments — collected from the embedded comments array.
              When the array is empty we render a friendly placeholder
              instead of a blank section. */}
          <section>
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-1.5 flex items-center gap-1">
              <MessageSquare className="size-3" />
              Comments ({comments.length})
            </h4>
            {comments.length === 0 ? (
              <p className="text-xs text-muted-foreground italic">
                No comments yet — start the ticket and add updates so the
                tenant can follow along.
              </p>
            ) : (
              <ul className="space-y-2">
                {comments.map((c, i) => (
                  <li
                    key={i}
                    className="rounded-lg border bg-secondary/30 p-2.5 text-sm"
                  >
                    <div className="flex items-center justify-between text-[11px] text-muted-foreground mb-1">
                      <span className="font-medium">
                        {c.userId ?? "Someone"}
                      </span>
                      <span>{relativeFromNow(c.timestamp)}</span>
                    </div>
                    <p className="whitespace-pre-wrap">{c.comment}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Status history — every transition from when the ticket was
              first opened. Useful audit trail for the owner. */}
          <section>
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-1.5 flex items-center gap-1">
              <Clock className="size-3" />
              History ({history.length})
            </h4>
            {history.length === 0 ? (
              <p className="text-xs text-muted-foreground italic">
                No status changes yet — this ticket is still on its
                opening status.
              </p>
            ) : (
              <ol className="space-y-1.5">
                {history.map((h, i) => (
                  <li
                    key={i}
                    className="text-xs flex items-center justify-between gap-2 border-l-2 border-primary/40 pl-2"
                  >
                    <span>
                      <span className="font-medium">{h.toStatus}</span>
                      {h.fromStatus && (
                        <span className="text-muted-foreground">
                          {" "}
                          ← {h.fromStatus}
                        </span>
                      )}
                    </span>
                    <span className="text-muted-foreground">
                      {relativeFromNow(h.timestamp)}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
