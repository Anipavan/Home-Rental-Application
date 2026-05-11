import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  Loader2,
  MessageSquareWarning,
  Search,
  Send,
  ShieldAlert,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { ContactPersonPopover } from "@/components/common/contact-person-popover";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, relativeFromNow } from "@/lib/utils";
import type {
  MaintenancePriority,
  MaintenanceRequestResponse,
  MaintenanceStatus,
} from "@/types/api";

/**
 * Admin complaints dashboard — sees every complaint across the
 * platform, INCLUDING owner-behaviour grievances (which are admin-only
 * by design). Reuses the same complaintsApi the owner side does and
 * adds a "flagged" tab for owner-behaviour + safety-hazard tickets so
 * the support team can triage urgent items first.
 */
export function AdminComplaintsPage() {
  const [q, setQ] = useState("");
  const pageQ = useQuery({
    queryKey: ["admin", "complaints"],
    queryFn: () => complaintsApi.list(0, 200),
  });
  const all = pageQ.data?.content ?? [];
  const filtered = useMemo(() => {
    if (!q) return all;
    const n = q.toLowerCase();
    return all.filter(
      (r) =>
        r.title?.toLowerCase().includes(n) ||
        r.tenantId?.toLowerCase().includes(n) ||
        r.complaintCategory?.toLowerCase().includes(n) ||
        String(r.flatId).includes(q),
    );
  }, [all, q]);

  const open = filtered.filter((r) => r.status === "OPEN");
  const inProgress = filtered.filter((r) => r.status === "IN_PROGRESS");
  const resolved = filtered.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );
  const flagged = filtered.filter(
    (r) =>
      r.complaintCategory === "OWNER_BEHAVIOR" ||
      r.complaintCategory === "SAFETY_HAZARD" ||
      r.priority === "CRITICAL",
  );

  const grid = (items: MaintenanceRequestResponse[]) => {
    if (pageQ.isLoading)
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
          Nothing here.
        </Card>
      );
    return (
      <div className="space-y-3">
        {items.map((r) => (
          <AdminComplaintCard key={r.id} request={r} />
        ))}
      </div>
    );
  };

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Complaints"
        description="Every grievance filed across the platform — including owner-behaviour cases that bypass the owner."
      />

      <div className="flex items-center gap-2 mb-5 max-w-md">
        <Search className="size-4 text-muted-foreground" />
        <Input
          placeholder="Search by title, tenant id, category, flat…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      <Tabs defaultValue="open">
        <TabsList>
          <TabsTrigger value="open">Open ({open.length})</TabsTrigger>
          <TabsTrigger value="progress">
            In review ({inProgress.length})
          </TabsTrigger>
          <TabsTrigger value="resolved">
            Resolved ({resolved.length})
          </TabsTrigger>
          <TabsTrigger value="flagged">Flagged ({flagged.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="open">{grid(open)}</TabsContent>
        <TabsContent value="progress">{grid(inProgress)}</TabsContent>
        <TabsContent value="resolved">{grid(resolved)}</TabsContent>
        <TabsContent value="flagged">{grid(flagged)}</TabsContent>
      </Tabs>
    </div>
  );
}

function AdminComplaintCard({ request }: { request: MaintenanceRequestResponse }) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [reply, setReply] = useState("");

  const setStatus = useMutation({
    mutationFn: (next: MaintenanceStatus) =>
      complaintsApi.setStatus(request.id, next, authUserId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "complaints"] });
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
      qc.invalidateQueries({ queryKey: ["admin", "complaints"] });
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

  const isOwnerBehavior = request.complaintCategory === "OWNER_BEHAVIOR";
  const isCritical =
    request.priority === "CRITICAL" || request.complaintCategory === "SAFETY_HAZARD";
  const comments = [...(request.comments ?? [])].sort((a, b) =>
    (a.timestamp ?? "").localeCompare(b.timestamp ?? ""),
  );

  return (
    <Card className={isCritical ? "border-destructive/40" : ""}>
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
                    : isOwnerBehavior
                      ? "bg-warning/15 text-warning"
                      : "bg-primary/10 text-primary"
                }`}
              >
                {isCritical ? (
                  <AlertTriangle className="size-4" />
                ) : isOwnerBehavior ? (
                  <ShieldAlert className="size-4" />
                ) : (
                  <MessageSquareWarning className="size-4" />
                )}
              </div>
              <div className="min-w-0">
                <p className="font-medium truncate">{request.title}</p>
                <p className="text-xs text-muted-foreground">
                  {prettyCategory(request.complaintCategory ?? "OTHER")} · Tenant{" "}
                  {short(request.tenantId)} · Flat #{request.flatId} ·{" "}
                  {relativeFromNow(request.createdAt)}
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
              {request.ownerId && !isOwnerBehavior && (
                <ContactPersonPopover
                  authUserId={request.ownerId}
                  variant="button"
                  label="Contact owner"
                />
              )}
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
                  Start review
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
                  No replies yet.
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
                            {mine ? "Admin" : short(c.userId)} ·{" "}
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
                  placeholder="Reply…"
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

function short(id: string | null | undefined): string {
  if (!id) return "system";
  if (id.length <= 8) return id;
  return id.slice(0, 8) + "…";
}
