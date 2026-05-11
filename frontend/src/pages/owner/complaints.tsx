import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  Loader2,
  MessageSquareWarning,
  Send,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ContactPersonPopover } from "@/components/common/contact-person-popover";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { relativeFromNow, formatDate } from "@/lib/utils";
import type {
  ComplaintCategory,
  MaintenancePriority,
  MaintenanceRequestResponse,
  MaintenanceStatus,
} from "@/types/api";

/**
 * Owner complaints inbox.
 *
 * Lists every complaint filed against any flat the owner has. Mirrors
 * the maintenance queue UX (open / in-progress / resolved tabs) and
 * adds:
 *   - category + priority filters (so a busy owner can triage)
 *   - inline expand + reply (no separate detail page round-trip)
 *   - status-change buttons that move through OPEN → IN_PROGRESS → RESOLVED
 *
 * Owner-behaviour complaints are filtered out — those are routed to
 * admin and the owner is deliberately excluded from the loop. Backend
 * notification skips the owner ping for that category; we exclude
 * them from this list for the same reason.
 */
export function OwnerComplaintsPage() {
  const { authUserId } = useAuthStore();
  const q = useQuery({
    queryKey: ["owner-complaints", authUserId],
    queryFn: () => complaintsApi.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const [categoryFilter, setCategoryFilter] = useState<"ALL" | ComplaintCategory>(
    "ALL",
  );
  const [priorityFilter, setPriorityFilter] = useState<
    "ALL" | MaintenancePriority
  >("ALL");

  const all = useMemo(() => {
    const items = q.data ?? [];
    return items
      .filter((r) => r.complaintCategory !== "OWNER_BEHAVIOR")
      .filter((r) =>
        categoryFilter === "ALL"
          ? true
          : r.complaintCategory === categoryFilter,
      )
      .filter((r) =>
        priorityFilter === "ALL" ? true : r.priority === priorityFilter,
      )
      // Most urgent first: CRITICAL > HIGH > MEDIUM > LOW, then newest.
      .sort((a, b) => {
        const score = (p: MaintenancePriority) =>
          ({ CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 })[p] ?? 0;
        const d = score(b.priority) - score(a.priority);
        if (d !== 0) return d;
        return (b.createdAt ?? "").localeCompare(a.createdAt ?? "");
      });
  }, [q.data, categoryFilter, priorityFilter]);

  const open = all.filter((r) => r.status === "OPEN");
  const inProgress = all.filter((r) => r.status === "IN_PROGRESS");
  const resolved = all.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );

  const grid = (items: MaintenanceRequestResponse[]) => {
    if (q.isLoading)
      return (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-2xl" />
          ))}
        </div>
      );
    if (items.length === 0)
      return (
        <Card className="p-12 text-center text-muted-foreground">
          Nothing matches the current filter.
        </Card>
      );
    return (
      <div className="space-y-3">
        {items.map((r) => (
          <ComplaintCard key={r.id} request={r} />
        ))}
      </div>
    );
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Complaints"
        description="Grievances from your tenants. Triage, reply, and close them out."
      />

      <Card className="mb-5">
        <CardContent className="p-4 flex flex-wrap items-center gap-3">
          <span className="text-xs font-medium text-muted-foreground">
            Filters:
          </span>
          <Select
            value={categoryFilter}
            onValueChange={(v) =>
              setCategoryFilter(v as "ALL" | ComplaintCategory)
            }
          >
            <SelectTrigger className="w-56">
              <SelectValue placeholder="Category" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All categories</SelectItem>
              <SelectItem value="NOISE">Noise</SelectItem>
              <SelectItem value="NEIGHBOR_DISPUTE">Neighbour dispute</SelectItem>
              <SelectItem value="SECURITY_CONCERN">Security concern</SelectItem>
              <SelectItem value="BILLING_DISPUTE">Billing dispute</SelectItem>
              <SelectItem value="SAFETY_HAZARD">Safety hazard</SelectItem>
              <SelectItem value="COMMON_AREA">Common area</SelectItem>
              <SelectItem value="LEASE_VIOLATION">Lease violation</SelectItem>
              <SelectItem value="OTHER">Other</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={priorityFilter}
            onValueChange={(v) =>
              setPriorityFilter(v as "ALL" | MaintenancePriority)
            }
          >
            <SelectTrigger className="w-44">
              <SelectValue placeholder="Priority" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All priorities</SelectItem>
              <SelectItem value="CRITICAL">Critical</SelectItem>
              <SelectItem value="HIGH">High</SelectItem>
              <SelectItem value="MEDIUM">Medium</SelectItem>
              <SelectItem value="LOW">Low</SelectItem>
            </SelectContent>
          </Select>
          <div className="ml-auto text-xs text-muted-foreground">
            {all.length} shown · {open.length + inProgress.length} active
          </div>
        </CardContent>
      </Card>

      <Tabs defaultValue="open">
        <TabsList>
          <TabsTrigger value="open">Open ({open.length})</TabsTrigger>
          <TabsTrigger value="progress">
            In review ({inProgress.length})
          </TabsTrigger>
          <TabsTrigger value="resolved">
            Resolved ({resolved.length})
          </TabsTrigger>
        </TabsList>
        <TabsContent value="open">{grid(open)}</TabsContent>
        <TabsContent value="progress">{grid(inProgress)}</TabsContent>
        <TabsContent value="resolved">{grid(resolved)}</TabsContent>
      </Tabs>
    </div>
  );
}

function ComplaintCard({ request }: { request: MaintenanceRequestResponse }) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [reply, setReply] = useState("");

  const setStatus = useMutation({
    mutationFn: (next: MaintenanceStatus) =>
      complaintsApi.setStatus(request.id, next, authUserId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-complaints"] });
      toast({ title: "Status updated" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't update",
        description: extractErrorMessage(e),
      }),
  });

  const sendReply = useMutation({
    mutationFn: () =>
      complaintsApi.comment(request.id, authUserId!, reply.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-complaints"] });
      setReply("");
      toast({ title: "Reply sent" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't send",
        description: extractErrorMessage(e),
      }),
  });

  const isCritical =
    request.priority === "CRITICAL" || request.priority === "HIGH";
  const comments = [...(request.comments ?? [])].sort((a, b) =>
    (a.timestamp ?? "").localeCompare(b.timestamp ?? ""),
  );

  return (
    <Card>
      <CardContent className="p-5">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="w-full text-left"
        >
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
                  <MessageSquareWarning className="size-4" />
                )}
              </div>
              <div className="min-w-0">
                <p className="font-medium truncate">{request.title}</p>
                <p className="text-xs text-muted-foreground">
                  {prettyCategory(request.complaintCategory ?? "OTHER")} · Flat #
                  {request.flatId} · Filed{" "}
                  {relativeFromNow(request.createdAt)}
                  {request.requestNumber && (
                    <> · {request.requestNumber}</>
                  )}
                </p>
              </div>
            </div>
            <div className="flex flex-col items-end gap-1.5 shrink-0">
              <PriorityBadge priority={request.priority} />
              <StatusBadge status={request.status} />
            </div>
          </div>
        </button>

        <p
          className={`text-sm text-muted-foreground mt-3 ${
            expanded ? "whitespace-pre-wrap" : "line-clamp-2"
          }`}
        >
          {request.description}
        </p>

        {expanded && (
          <div className="mt-4 space-y-4 border-t pt-4">
            <div className="flex flex-wrap items-center gap-2">
              <ContactPersonPopover
                authUserId={request.tenantId}
                variant="button"
                label="Contact tenant"
              />
              {request.status === "OPEN" && (
                <Button
                  size="sm"
                  variant="gradient"
                  onClick={() => setStatus.mutate("IN_PROGRESS")}
                  disabled={setStatus.isPending}
                >
                  {setStatus.isPending && (
                    <Loader2 className="animate-spin" />
                  )}
                  Acknowledge — start review
                </Button>
              )}
              {request.status === "IN_PROGRESS" && (
                <Button
                  size="sm"
                  variant="gradient"
                  onClick={() => setStatus.mutate("RESOLVED")}
                  disabled={setStatus.isPending}
                >
                  Mark resolved
                </Button>
              )}
              {request.status === "RESOLVED" && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setStatus.mutate("CLOSED")}
                  disabled={setStatus.isPending}
                >
                  Close
                </Button>
              )}
            </div>

            <div>
              <p className="text-xs font-medium text-muted-foreground mb-2">
                Conversation
              </p>
              {comments.length === 0 ? (
                <p className="text-xs text-muted-foreground italic">
                  No replies yet. Be the first to acknowledge.
                </p>
              ) : (
                <ul className="space-y-2 max-h-72 overflow-auto pr-2">
                  {comments.map((c, i) => {
                    const mine = c.userId === authUserId;
                    return (
                      <li
                        key={i}
                        className={`flex ${mine ? "justify-end" : "justify-start"}`}
                      >
                        <div
                          className={`max-w-[80%] rounded-2xl px-3.5 py-2 text-sm ${
                            mine
                              ? "bg-primary text-primary-foreground rounded-br-sm"
                              : "bg-secondary text-foreground rounded-bl-sm"
                          }`}
                        >
                          <p className="whitespace-pre-wrap">{c.comment}</p>
                          <p
                            className={`text-[10px] mt-1 ${mine ? "text-primary-foreground/70" : "text-muted-foreground"}`}
                          >
                            {mine ? "You" : "Tenant"} ·{" "}
                            {c.timestamp ? formatDate(c.timestamp) : ""}
                          </p>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            {request.status !== "CLOSED" && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!reply.trim() || sendReply.isPending) return;
                  sendReply.mutate();
                }}
                className="flex items-end gap-2"
              >
                <Textarea
                  rows={2}
                  placeholder="Reply to the tenant…"
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  className="resize-none"
                />
                <Button
                  type="submit"
                  variant="gradient"
                  disabled={!reply.trim() || sendReply.isPending}
                >
                  {sendReply.isPending ? (
                    <Loader2 className="animate-spin" />
                  ) : (
                    <Send />
                  )}
                  Send
                </Button>
              </form>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function StatusBadge({ status }: { status: string }) {
  if (status === "RESOLVED" || status === "CLOSED")
    return <Badge variant="success">{status}</Badge>;
  if (status === "IN_PROGRESS")
    return <Badge variant="warning">In review</Badge>;
  return <Badge>Open</Badge>;
}

function PriorityBadge({ priority }: { priority: MaintenancePriority }) {
  if (priority === "CRITICAL")
    return <Badge variant="destructive">{priority}</Badge>;
  if (priority === "HIGH") return <Badge variant="warning">{priority}</Badge>;
  return <Badge variant="secondary">{priority}</Badge>;
}

function prettyCategory(value: string): string {
  if (!value) return "—";
  return value
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
