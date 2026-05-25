import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Inbox,
  CheckCircle2,
  Clock,
  ArrowUpRight,
  Loader2,
} from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { supportTicketsApi, type SupportTicket } from "@/lib/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/** Admin inbox for in-app support tickets. */
export function AdminSupportPage() {
  const [activeStatus, setActiveStatus] = useState<SupportTicket["status"]>("OPEN");

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Support inbox"
        description="In-app tickets users have submitted from the Contact Support form."
      />

      <Tabs
        value={activeStatus}
        onValueChange={(v) => setActiveStatus(v as SupportTicket["status"])}
      >
        <TabsList>
          <TabsTrigger value="OPEN">
            <Inbox className="size-3.5" /> Open
          </TabsTrigger>
          <TabsTrigger value="IN_PROGRESS">
            <Clock className="size-3.5" /> In progress
          </TabsTrigger>
          <TabsTrigger value="RESOLVED">
            <CheckCircle2 className="size-3.5" /> Resolved
          </TabsTrigger>
        </TabsList>

        {(["OPEN", "IN_PROGRESS", "RESOLVED"] as const).map((s) => (
          <TabsContent key={s} value={s} className="mt-4">
            <TicketList status={s} />
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}

function TicketList({ status }: { status: SupportTicket["status"] }) {
  const q = useQuery({
    queryKey: ["support-tickets", status],
    queryFn: () => supportTicketsApi.list(status, 0, 50),
  });
  const [active, setActive] = useState<SupportTicket | null>(null);

  if (q.isLoading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-24 rounded-xl" />
        ))}
      </div>
    );
  }

  const items = q.data?.content ?? [];
  if (items.length === 0) {
    return (
      <EmptyState
        variant="info"
        icon={Inbox}
        title="Nothing here"
        description={`No support tickets in "${status}" right now. Tickets raised by tenants and owners land here for triage.`}
      />
    );
  }

  return (
    <>
      <div className="space-y-3">
        {items.map((t) => (
          <button
            key={t.id}
            onClick={() => setActive(t)}
            className="w-full text-left rounded-xl border bg-secondary/30 p-4 hover:bg-secondary/60 transition-colors"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="font-medium">{t.subject}</p>
                  <StatusBadge status={t.status} />
                  {t.userRole && (
                    <Badge variant="secondary" className="text-[10px]">
                      {t.userRole}
                    </Badge>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {t.userName ?? "Unknown user"} ·{" "}
                  {t.userEmail ?? t.userId}
                  {t.createdAt && (
                    <> · {new Date(t.createdAt).toLocaleString()}</>
                  )}
                </p>
                <p className="text-sm text-muted-foreground mt-2 line-clamp-2 whitespace-pre-wrap">
                  {t.message}
                </p>
                {t.contextUrl && (
                  <p className="text-[11px] text-muted-foreground mt-1 inline-flex items-center gap-1">
                    <ArrowUpRight className="size-3" />
                    raised from <code>{t.contextUrl}</code>
                  </p>
                )}
              </div>
            </div>
          </button>
        ))}
      </div>

      {active && (
        <RespondDialog
          ticket={active}
          onClose={() => setActive(null)}
        />
      )}
    </>
  );
}

function RespondDialog({
  ticket,
  onClose,
}: {
  ticket: SupportTicket;
  onClose: () => void;
}) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [response, setResponse] = useState(ticket.adminResponse ?? "");
  const [newStatus, setNewStatus] = useState<"IN_PROGRESS" | "RESOLVED" | "CLOSED">(
    "RESOLVED",
  );

  const respondM = useMutation({
    mutationFn: () =>
      supportTicketsApi.respond(ticket.id, {
        respondedBy: authUserId ?? "admin",
        adminResponse: response.trim(),
        newStatus,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["support-tickets"] });
      toast({ title: "Response sent" });
      onClose();
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't respond",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {ticket.subject}
            <StatusBadge status={ticket.status} />
          </DialogTitle>
          <DialogDescription>
            From <strong>{ticket.userName ?? "Unknown"}</strong> ·{" "}
            {ticket.userEmail ?? ticket.userId}
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-xl border bg-secondary/30 p-4 max-h-48 overflow-y-auto whitespace-pre-wrap text-sm">
          {ticket.message}
        </div>

        {ticket.contextUrl && (
          <p className="text-xs text-muted-foreground">
            Raised from <code>{ticket.contextUrl}</code>
          </p>
        )}

        <div className="space-y-2">
          <label className="text-sm font-medium">Your response</label>
          <Textarea
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            rows={5}
            maxLength={4000}
            placeholder="We've fixed this — please refresh and try again."
          />
        </div>

        <div className="space-y-2">
          <label className="text-sm font-medium">New status</label>
          <Select value={newStatus} onValueChange={(v) => setNewStatus(v as typeof newStatus)}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="IN_PROGRESS">In progress</SelectItem>
              <SelectItem value="RESOLVED">Resolved</SelectItem>
              <SelectItem value="CLOSED">Closed</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={respondM.isPending}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            onClick={() => respondM.mutate()}
            disabled={respondM.isPending || response.trim().length === 0}
          >
            {respondM.isPending && <Loader2 className="size-4 animate-spin" />}
            Send &amp; update status
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function StatusBadge({ status }: { status: SupportTicket["status"] }) {
  switch (status) {
    case "OPEN":
      return <Badge variant="warning" className="text-[10px]">Open</Badge>;
    case "IN_PROGRESS":
      return <Badge variant="secondary" className="text-[10px]">In progress</Badge>;
    case "RESOLVED":
      return <Badge variant="success" className="text-[10px]">Resolved</Badge>;
    default:
      return <Badge variant="secondary" className="text-[10px]">Closed</Badge>;
  }
}
