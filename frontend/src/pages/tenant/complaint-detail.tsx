import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2, Send, ShieldAlert } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { complaintsApi } from "@/lib/api/maintenance";
import { propertiesApi } from "@/lib/api/properties";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { PageHeader } from "@/components/layout/page-header";
import { ContactPersonPopover } from "@/components/common/contact-person-popover";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, relativeFromNow } from "@/lib/utils";
import type { MaintenanceStatus } from "@/types/api";

/**
 * Tenant complaint detail.
 *
 * Renders:
 *   • header (title, status, category, urgency, reference id)
 *   • description + evidence photos
 *   • inline timeline of status transitions
 *   • messages thread (re-uses MaintenanceRequest.comments)
 *   • a Reply composer for the tenant
 *   • a "Contact owner" CTA — the user explicitly asked to be able
 *     to contact the right person about a complaint; ContactPersonPopover
 *     resolves owner phone/email and surfaces tel:/mailto: links.
 *
 * Both sides (tenant + owner+admin) post into the same comments thread
 * — the backend writes them as MaintenanceComment with the poster's
 * authUserId and a server timestamp.
 */
export function ComplaintDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [reply, setReply] = useState("");

  const q = useQuery({
    queryKey: ["complaint", id],
    queryFn: () => complaintsApi.get(id!),
    enabled: !!id,
    // Light polling — keeps the thread fresh while the user is reading.
    // Bell push will also flip the cache when a comment arrives via SSE,
    // but the polling is a belt-and-suspenders fallback.
    refetchInterval: 15_000,
  });

  const request = q.data;

  const buildingQ = useQuery({
    queryKey: ["building", request?.flatId, "for-complaint"],
    queryFn: async () => {
      const flat = await propertiesApi.flats.get(request!.flatId);
      return propertiesApi.buildings.get(flat.buildingId);
    },
    enabled: !!request?.flatId,
  });

  const sendReply = useMutation({
    mutationFn: () => complaintsApi.comment(id!, authUserId!, reply.trim()),
    onSuccess: (data) => {
      qc.setQueryData(["complaint", id], data);
      qc.invalidateQueries({ queryKey: ["my-complaints"] });
      setReply("");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't send",
        description: extractErrorMessage(e),
      }),
  });

  const reopenCloseMutation = useMutation({
    mutationFn: (next: MaintenanceStatus) =>
      complaintsApi.setStatus(id!, next, authUserId!),
    onSuccess: (data) => {
      qc.setQueryData(["complaint", id], data);
      qc.invalidateQueries({ queryKey: ["my-complaints"] });
      toast({ title: "Status updated" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't update status",
        description: extractErrorMessage(e),
      }),
  });

  if (q.isLoading) {
    return (
      <div className="space-y-4 max-w-3xl">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-60 rounded-2xl" />
        <Skeleton className="h-40 rounded-2xl" />
      </div>
    );
  }

  if (!request) {
    return (
      <div className="max-w-2xl">
        <Card className="p-10 text-center">
          <ShieldAlert className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            Complaint not found.
          </p>
          <p className="text-muted-foreground text-sm mt-1">
            It may have been removed.
          </p>
          <Button asChild variant="gradient" className="mt-5">
            <Link to="/app/complaints">Back to complaints</Link>
          </Button>
        </Card>
      </div>
    );
  }

  const isClosed =
    request.status === "RESOLVED" || request.status === "CLOSED";
  const ownerId = buildingQ.data?.ownerId;
  const isOwnerBehavior = request.complaintCategory === "OWNER_BEHAVIOR";
  // Comments include status-change "system" entries that we want to
  // show inline with the message thread. Sort ascending so the
  // conversation reads top-down.
  const comments = [...(request.comments ?? [])].sort((a, b) =>
    (a.timestamp ?? "").localeCompare(b.timestamp ?? ""),
  );

  return (
    <div className="animate-fade-in max-w-3xl space-y-5">
      <Button asChild variant="ghost" size="sm">
        <Link to="/app/complaints">
          <ArrowLeft /> All complaints
        </Link>
      </Button>

      <PageHeader
        title={request.title}
        description={
          request.requestNumber
            ? `Reference ${request.requestNumber} · Filed ${relativeFromNow(request.createdAt)}`
            : `Filed ${relativeFromNow(request.createdAt)}`
        }
        actions={<StatusBadge status={request.status} />}
      />

      <Card>
        <CardContent className="p-6 space-y-4">
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <Badge variant="secondary">
              {prettyCategory(request.complaintCategory ?? "OTHER")}
            </Badge>
            <Badge variant="outline">{request.priority} priority</Badge>
            {request.assignedTo && (
              <Badge variant="outline">
                Assigned to {short(request.assignedTo)}
              </Badge>
            )}
          </div>
          <p className="text-sm whitespace-pre-wrap leading-relaxed">
            {request.description}
          </p>
          {(request.images?.length ?? 0) > 0 && (
            <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 pt-2">
              {request.images!.map((src, i) => (
                <div
                  key={i}
                  className="aspect-square rounded-lg bg-secondary/40 grid place-items-center text-xs text-muted-foreground"
                  title={src}
                >
                  Evidence {i + 1}
                </div>
              ))}
            </div>
          )}

          <div className="flex flex-wrap gap-2 pt-2">
            {ownerId && !isOwnerBehavior && (
              <ContactPersonPopover
                authUserId={ownerId}
                variant="button"
                label="Contact owner"
              />
            )}
            {isOwnerBehavior && (
              <div className="text-xs text-muted-foreground bg-warning/5 border border-warning/30 rounded-lg p-3">
                This complaint is being handled by admin. Replies in this
                thread go to the support team, not the owner.
              </div>
            )}
            {isClosed && (
              <Button
                variant="outline"
                size="sm"
                disabled={reopenCloseMutation.isPending}
                onClick={() => reopenCloseMutation.mutate("OPEN")}
              >
                {reopenCloseMutation.isPending && (
                  <Loader2 className="animate-spin" />
                )}
                Re-open complaint
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-6">
          <h3 className="font-display font-semibold mb-4">Conversation</h3>
          {comments.length === 0 && (
            <p className="text-sm text-muted-foreground">
              No replies yet. Use the box below to add details or follow-up.
            </p>
          )}
          <ul className="space-y-3">
            {comments.map((c, i) => {
              const mine = c.userId === authUserId;
              return (
                <li
                  key={i}
                  className={`flex ${mine ? "justify-end" : "justify-start"}`}
                >
                  <div
                    className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm ${
                      mine
                        ? "bg-primary text-primary-foreground rounded-br-sm"
                        : "bg-secondary text-foreground rounded-bl-sm"
                    }`}
                  >
                    <p className="whitespace-pre-wrap">{c.comment}</p>
                    <p
                      className={`text-[10px] mt-1 ${mine ? "text-primary-foreground/70" : "text-muted-foreground"}`}
                    >
                      {mine ? "You" : short(c.userId)} ·{" "}
                      {c.timestamp ? formatDate(c.timestamp) : ""}
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>

          {!isClosed && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!reply.trim() || sendReply.isPending) return;
                sendReply.mutate();
              }}
              className="mt-4 flex items-end gap-2"
            >
              <Textarea
                rows={2}
                placeholder="Type a reply…"
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
        </CardContent>
      </Card>

      {(request.history?.length ?? 0) > 0 && (
        <Card>
          <CardContent className="p-6">
            <h3 className="font-display font-semibold mb-4">Timeline</h3>
            <ol className="space-y-3 text-sm">
              {request.history!.map((h, i) => (
                <li
                  key={i}
                  className="flex items-start gap-3 text-muted-foreground"
                >
                  <div className="size-2 rounded-full bg-primary mt-1.5 shrink-0" />
                  <div className="flex-1">
                    <p>
                      <span className="text-foreground font-medium">
                        {h.fromStatus
                          ? `${h.fromStatus} → ${h.toStatus}`
                          : `Filed as ${h.toStatus}`}
                      </span>{" "}
                      by {short(h.changedBy)}
                    </p>
                    <p className="text-xs">{formatDate(h.timestamp)}</p>
                  </div>
                </li>
              ))}
            </ol>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  if (status === "RESOLVED" || status === "CLOSED")
    return <Badge variant="success">{status}</Badge>;
  if (status === "IN_PROGRESS")
    return <Badge variant="warning">In review</Badge>;
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

/**
 * Owner / admin authUserIds are UUIDs — display the first 8 chars
 * inline so we don't leak the full id but still distinguish posters.
 */
function short(id: string | null | undefined): string {
  if (!id) return "system";
  if (id.length <= 8) return id;
  return id.slice(0, 8) + "…";
}
