import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { MessageSquareWarning, Plus, ShieldAlert } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { relativeFromNow } from "@/lib/utils";
import type { MaintenanceRequestResponse } from "@/types/api";

/**
 * Tenant Complaints — list view.
 *
 * Reuses the maintenance ticket pipeline under the hood (same state
 * machine: OPEN → IN_PROGRESS → RESOLVED → CLOSED, same comments
 * thread, same Kafka fan-out). The only thing different is the
 * category taxonomy (ComplaintCategory) and the copy. See
 * {@link complaintsApi} for the API wrapper that filters to
 * {@code kind=COMPLAINT}.
 */
export function ComplaintsPage() {
  const { authUserId } = useAuthStore();
  const q = useQuery({
    queryKey: ["my-complaints", authUserId],
    queryFn: () => complaintsApi.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  const items = q.data ?? [];
  // Sort most recent first — the maintenance API returns insertion
  // order which is roughly chronological but isn't guaranteed.
  const sorted = [...items].sort((a, b) =>
    (b.createdAt ?? "").localeCompare(a.createdAt ?? ""),
  );
  const open = sorted.filter(
    (r) => r.status === "OPEN" || r.status === "IN_PROGRESS",
  );
  const resolved = sorted.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Complaints"
        description="File a grievance about your stay — we'll route it to the right person."
        actions={
          <Button asChild variant="gradient">
            <Link to="/app/complaints/new">
              <Plus /> File a complaint
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
            <ShieldAlert className="size-10 mx-auto text-muted-foreground" />
            <p className="font-display font-semibold text-lg mt-3">
              All quiet on the home front.
            </p>
            <p className="text-muted-foreground text-sm mt-1">
              No active complaints — long may it last.
            </p>
          </Card>
        )}
        <div className="space-y-3">
          {open.map((r) => (
            <Row key={r.id} request={r} />
          ))}
        </div>
      </section>

      {resolved.length > 0 && (
        <section>
          <h2 className="font-display font-semibold text-lg mb-3">Closed</h2>
          <div className="space-y-2">
            {resolved.map((r) => (
              <Row key={r.id} request={r} subtle />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function Row({
  request,
  subtle,
}: {
  request: MaintenanceRequestResponse;
  subtle?: boolean;
}) {
  const commentCount = request.comments?.length ?? 0;
  return (
    <Link to={`/app/complaints/${request.id}`} className="block">
      <Card
        className={`transition-colors hover:border-primary/40 ${subtle ? "opacity-80" : ""}`}
      >
        <CardContent className="p-5 flex items-start gap-4">
          <div className="size-10 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
            <MessageSquareWarning className="size-4" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-start justify-between gap-3 flex-wrap">
              <div className="min-w-0">
                <p className="font-medium truncate">{request.title}</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {prettyCategory(request.complaintCategory ?? "OTHER")} ·{" "}
                  {request.priority} priority ·{" "}
                  {relativeFromNow(request.createdAt)}
                  {commentCount > 0 && (
                    <>
                      {" · "}
                      <span>
                        {commentCount} message{commentCount === 1 ? "" : "s"}
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
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}

function StatusBadge({ status }: { status: string }) {
  if (status === "RESOLVED" || status === "CLOSED")
    return <Badge variant="success">{status}</Badge>;
  if (status === "IN_PROGRESS")
    return <Badge variant="warning">In review</Badge>;
  return <Badge>Open</Badge>;
}

/**
 * Turn {@code "NEIGHBOR_DISPUTE"} → {@code "Neighbor dispute"} for
 * display. The enum names live in the backend; we don't want to
 * mirror a translation map.
 */
function prettyCategory(value: string): string {
  if (!value) return "—";
  return value
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
