import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Wrench, AlertTriangle } from "lucide-react";
import { maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { relativeFromNow } from "@/lib/utils";
import type {
  MaintenancePriority,
  MaintenanceRequestResponse,
  MaintenanceStatus,
} from "@/types/api";

export function AdminMaintenancePage() {
  const [q, setQ] = useState("");

  const pageQ = useQuery({
    queryKey: ["admin", "maintenance"],
    queryFn: () => maintenanceApi.list(0, 200),
  });

  const all = pageQ.data?.content ?? [];
  const filtered = useMemo(() => {
    if (!q) return all;
    const n = q.toLowerCase();
    return all.filter(
      (r) =>
        r.title?.toLowerCase().includes(n) ||
        r.tenantId?.toLowerCase().includes(n) ||
        String(r.flatId).includes(q),
    );
  }, [all, q]);

  const open = filtered.filter((r) => r.status === "OPEN");
  const inProgress = filtered.filter((r) => r.status === "IN_PROGRESS");
  const resolved = filtered.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );
  const critical = filtered.filter((r) => r.priority === "CRITICAL");

  const grid = (items: MaintenanceRequestResponse[]) => {
    if (pageQ.isLoading)
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
          <RequestCard key={r.id} request={r} />
        ))}
      </div>
    );
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Maintenance — platform-wide"
        description="Every ticket from every tenant."
      />

      <Card className="p-3 mb-5">
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            placeholder="Search by title, tenant or flat number"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-10"
          />
        </div>
      </Card>

      <Tabs defaultValue="open">
        <TabsList>
          <TabsTrigger value="open">Open ({open.length})</TabsTrigger>
          <TabsTrigger value="progress">
            In progress ({inProgress.length})
          </TabsTrigger>
          <TabsTrigger value="critical">Critical ({critical.length})</TabsTrigger>
          <TabsTrigger value="resolved">Resolved ({resolved.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="open">{grid(open)}</TabsContent>
        <TabsContent value="progress">{grid(inProgress)}</TabsContent>
        <TabsContent value="critical">{grid(critical)}</TabsContent>
        <TabsContent value="resolved">{grid(resolved)}</TabsContent>
      </Tabs>
    </div>
  );
}

function RequestCard({ request }: { request: MaintenanceRequestResponse }) {
  const isCritical = request.priority === "CRITICAL";
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div
              className={`size-10 rounded-lg grid place-items-center shrink-0 ${
                isCritical
                  ? "bg-destructive/10 text-destructive"
                  : "bg-primary/10 text-primary"
              }`}
            >
              {isCritical ? (
                <AlertTriangle className="size-4" />
              ) : (
                <Wrench className="size-4" />
              )}
            </div>
            <div className="min-w-0">
              <p className="font-medium truncate">{request.title}</p>
              <p className="text-xs text-muted-foreground truncate">
                Flat #{request.flatId} · {request.category} · Tenant{" "}
                {request.tenantId} · {relativeFromNow(request.createdAt)}
              </p>
            </div>
          </div>
          <div className="flex flex-col items-end gap-1.5">
            <PriorityBadge priority={request.priority} />
            <StatusBadge status={request.status} />
          </div>
        </div>
        <p className="text-sm text-muted-foreground mt-3 line-clamp-2">
          {request.description}
        </p>
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

function StatusBadge({ status }: { status: MaintenanceStatus }) {
  if (status === "RESOLVED" || status === "CLOSED")
    return <Badge variant="success">{status}</Badge>;
  if (status === "IN_PROGRESS")
    return <Badge variant="warning">In progress</Badge>;
  return <Badge>Open</Badge>;
}
