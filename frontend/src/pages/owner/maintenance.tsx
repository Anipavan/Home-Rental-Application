import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/stores/auth-store";
import { maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Wrench, AlertTriangle } from "lucide-react";
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
          <Button size="sm" variant="ghost">
            View thread
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function PriorityBadge({ priority }: { priority: MaintenancePriority }) {
  if (priority === "CRITICAL")
    return <Badge variant="destructive">{priority}</Badge>;
  if (priority === "HIGH") return <Badge variant="warning">{priority}</Badge>;
  return <Badge variant="secondary">{priority}</Badge>;
}
