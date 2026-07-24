import { useMemo, useState } from "react";
import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  Loader2,
  MessageSquareWarning,
  Send,
  Wrench,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi, maintenanceApi } from "@/lib/api/maintenance";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ContactPersonPopover } from "@/components/common/contact-person-popover";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { relativeFromNow, formatDate } from "@/lib/utils";
import type {
  MaintenancePriority,
  MaintenanceRequestResponse,
  MaintenanceStatus,
  TicketKind,
} from "@/types/api";

/**
 * Owner Complaints — unified queue.
 *
 * Merged view over the two backend Kinds (MAINTENANCE + COMPLAINT).
 * Same collection, same status machine — the split was UX-only and
 * routinely confused everyone ("is a leaky tap a complaint or a
 * maintenance ticket?"). This page pulls both endpoints, tags every
 * row with a Kind icon + label, and offers a Kind filter for owners
 * who want to triage one flavour at a time.
 *
 * OWNER_BEHAVIOR complaints are dropped — they route to admin and
 * the owner is deliberately excluded from that loop.
 */
export function OwnerComplaintsPage() {
  const { authUserId } = useAuthStore();

  // Two independent queries — parallel fetch, both cheap. Kept
  // separate so cache keys keep matching what the owner dashboard
  // widgets already use (owner-maintenance / owner-complaints).
  const [maintQ, complaintQ] = useQueries({
    queries: [
      {
        queryKey: ["owner-maintenance", authUserId],
        queryFn: () => maintenanceApi.byOwner(authUserId!),
        enabled: !!authUserId,
      },
      {
        queryKey: ["owner-complaints", authUserId],
        queryFn: () => complaintsApi.byOwner(authUserId!),
        enabled: !!authUserId,
      },
    ],
  });

  const [kindFilter, setKindFilter] = useState<"ALL" | TicketKind>("ALL");
  const [categoryFilter, setCategoryFilter] = useState<string>("ALL");
  const [priorityFilter, setPriorityFilter] = useState<
    "ALL" | MaintenancePriority
  >("ALL");

  const loading = maintQ.isLoading || complaintQ.isLoading;

  const all = useMemo(() => {
    const items: MaintenanceRequestResponse[] = [
      ...(maintQ.data ?? []),
      ...(complaintQ.data ?? []),
    ];
    return items
      // OWNER_BEHAVIOR is intentionally invisible to the owner — the
      // backend routes those to admin and skips the owner
      // notification.
      .filter((r) => r.complaintCategory !== "OWNER_BEHAVIOR")
      .filter((r) => (kindFilter === "ALL" ? true : r.kind === kindFilter))
      .filter((r) => {
        if (categoryFilter === "ALL") return true;
        return (
          r.category === categoryFilter ||
          r.complaintCategory === categoryFilter
        );
      })
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
  }, [maintQ.data, complaintQ.data, kindFilter, categoryFilter, priorityFilter]);

  const open = all.filter((r) => r.status === "OPEN");
  const inProgress = all.filter((r) => r.status === "IN_PROGRESS");
  const resolved = all.filter(
    (r) => r.status === "RESOLVED" || r.status === "CLOSED",
  );

  const grid = (items: MaintenanceRequestResponse[]) => {
    if (loading)
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
        description="Repairs, noise, safety — every ticket from every flat you own, one queue."
      />

      <Card className="mb-5">
        <CardContent className="p-4 flex flex-wrap items-center gap-3">
          <span className="text-xs font-medium text-muted-foreground">
            Filters:
          </span>
          <Select
            value={kindFilter}
            onValueChange={(v) => setKindFilter(v as "ALL" | TicketKind)}
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All types</SelectItem>
              <SelectItem value="MAINTENANCE">Repairs</SelectItem>
              <SelectItem value="COMPLAINT">Complaints</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={categoryFilter}
            onValueChange={(v) => setCategoryFilter(v)}
          >
            <SelectTrigger className="w-56">
              <SelectValue placeholder="Category" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All categories</SelectItem>
              <SelectGroup>
                <SelectLabel>Repairs</SelectLabel>
                <SelectItem value="PLUMBING">Plumbing</SelectItem>
                <SelectItem value="ELECTRICAL">Electrical</SelectItem>
                <SelectItem value="APPLIANCE">Appliance</SelectItem>
                <SelectItem value="PAINTING">Painting</SelectItem>
                <SelectItem value="CLEANING">Cleaning</SelectItem>
                <SelectItem value="PEST_CONTROL">Pest control</SelectItem>
                <SelectItem value="GENERAL">General repair</SelectItem>
              </SelectGroup>
              <SelectGroup>
                <SelectLabel>Complaints</SelectLabel>
                <SelectItem value="NOISE">Noise</SelectItem>
                <SelectItem value="NEIGHBOR_DISPUTE">
                  Neighbour dispute
                </SelectItem>
                <SelectItem value="SECURITY_CONCERN">
                  Security concern
                </SelectItem>
                <SelectItem value="BILLING_DISPUTE">Billing dispute</SelectItem>
                <SelectItem value="SAFETY_HAZARD">Safety hazard</SelectItem>
                <SelectItem value="COMMON_AREA">Common area</SelectItem>
                <SelectItem value="LEASE_VIOLATION">Lease violation</SelectItem>
                <SelectItem value="OTHER">Other</SelectItem>
              </SelectGroup>
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

/**
 * One inline-expandable ticket row. Picks the right API + cache key
 * off {@code request.kind} so a maintenance ticket updates the
 * maintenance cache and a complaint updates the complaints cache.
 * Everything else — expand, reply, status-change, contact-tenant —
 * is Kind-agnostic.
 */
function ComplaintCard({ request }: { request: MaintenanceRequestResponse }) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [reply, setReply] = useState("");

  const isMaintenance = request.kind === "MAINTENANCE";
  const api = isMaintenance ? maintenanceApi : complaintsApi;
  const cacheKey = isMaintenance ? "owner-maintenance" : "owner-complaints";
  const KindIcon = isMaintenance ? Wrench : MessageSquareWarning;

  const setStatus = useMutation({
    mutationFn: (next: MaintenanceStatus) =>
      api.setStatus(request.id, next, authUserId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [cacheKey] });
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
    mutationFn: () => api.comment(request.id, authUserId!, reply.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [cacheKey] });
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
  const category = request.category ?? request.complaintCategory ?? "OTHER";

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
                    : isMaintenance
                      ? "bg-amber-500/15 text-amber-600"
                      : "bg-rose-500/15 text-rose-600"
                }`}
              >
                {isCritical ? (
                  <AlertTriangle className="size-4" />
                ) : (
                  <KindIcon className="size-4" />
                )}
              </div>
              <div className="min-w-0">
                <p className="font-medium truncate">{request.title}</p>
                <p className="text-xs text-muted-foreground">
                  <span className="font-medium text-foreground/70">
                    {isMaintenance ? "Repair" : "Complaint"}
                  </span>{" "}
                  · {prettyCategory(category)} · Flat #{request.flatId} · Filed{" "}
                  {relativeFromNow(request.createdAt)}
                  {request.requestNumber && <> · {request.requestNumber}</>}
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
