import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Calendar,
  CheckCircle2,
  Clock,
  Inbox,
  Loader2,
  Phone,
  Mail,
  XCircle,
  ArrowUpRight,
} from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import {
  visitRequestsApi,
  type VisitRequest,
  type VisitRequestStatus,
} from "@/lib/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
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

/**
 * Admin queue for property-visit requests submitted from the public
 * property-detail page. Each tab is a status (PENDING / CONFIRMED /
 * COMPLETED / CANCELLED). Cards are sorted by preferredAt ascending
 * inside each tab so the next slot to handle floats to the top.
 */
export function AdminVisitRequestsPage() {
  const [activeStatus, setActiveStatus] =
    useState<VisitRequestStatus>("PENDING");

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Visit requests"
        description="Property visits booked by interested renters from the public listing pages."
      />

      <Tabs
        value={activeStatus}
        onValueChange={(v) => setActiveStatus(v as VisitRequestStatus)}
      >
        <TabsList>
          <TabsTrigger value="PENDING">
            <Inbox className="size-3.5" /> Pending
          </TabsTrigger>
          <TabsTrigger value="CONFIRMED">
            <Clock className="size-3.5" /> Confirmed
          </TabsTrigger>
          <TabsTrigger value="COMPLETED">
            <CheckCircle2 className="size-3.5" /> Completed
          </TabsTrigger>
          <TabsTrigger value="CANCELLED">
            <XCircle className="size-3.5" /> Cancelled
          </TabsTrigger>
        </TabsList>

        {(["PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"] as const).map((s) => (
          <TabsContent key={s} value={s} className="mt-4">
            <RequestList status={s} />
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}

function RequestList({ status }: { status: VisitRequestStatus }) {
  const q = useQuery({
    queryKey: ["visit-requests", status],
    queryFn: () => visitRequestsApi.list({ status, page: 0, size: 50 }),
  });
  const [active, setActive] = useState<VisitRequest | null>(null);

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
      <Card>
        <CardContent className="p-12 text-center">
          <Calendar className="size-10 mx-auto text-muted-foreground" />
          <p className="font-medium mt-3">No visits in this state</p>
          <p className="text-sm text-muted-foreground mt-1">
            Nothing to handle right now.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <div className="space-y-3">
        {items.map((v) => (
          <button
            key={v.id}
            onClick={() => setActive(v)}
            className="w-full text-left rounded-xl border bg-secondary/30 p-4 hover:bg-secondary/60 transition-colors"
          >
            <div className="flex items-start justify-between gap-3 flex-wrap">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="font-medium">
                    {v.propertyLabel ?? `Flat ${v.flatId}`}
                  </p>
                  <StatusBadge status={v.status} />
                  {v.userId === "PUBLIC_VISITOR" && (
                    <Badge variant="secondary" className="text-[10px]">
                      Public
                    </Badge>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {v.visitorName} · {v.visitorEmail ?? "no email"}
                  {v.visitorPhone ? ` · ${v.visitorPhone}` : ""}
                </p>
                <p className="text-sm mt-2 inline-flex items-center gap-1.5">
                  <Calendar className="size-3.5 text-primary" />
                  <span className="font-medium">
                    {v.preferredAt
                      ? new Date(v.preferredAt).toLocaleString()
                      : "No preferred slot"}
                  </span>
                </p>
                {v.message && (
                  <p className="text-sm text-muted-foreground mt-2 line-clamp-2 whitespace-pre-wrap">
                    {v.message}
                  </p>
                )}
                {v.contextUrl && (
                  <p className="text-[11px] text-muted-foreground mt-1 inline-flex items-center gap-1">
                    <ArrowUpRight className="size-3" />
                    raised from <code>{v.contextUrl}</code>
                  </p>
                )}
              </div>
            </div>
          </button>
        ))}
      </div>

      {active && (
        <RespondDialog request={active} onClose={() => setActive(null)} />
      )}
    </>
  );
}

function RespondDialog({
  request,
  onClose,
}: {
  request: VisitRequest;
  onClose: () => void;
}) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [response, setResponse] = useState(request.adminResponse ?? "");
  const [newStatus, setNewStatus] = useState<VisitRequestStatus>(
    request.status === "PENDING" ? "CONFIRMED" : request.status,
  );

  const respondM = useMutation({
    mutationFn: () =>
      visitRequestsApi.respond(request.id, {
        respondedBy: authUserId ?? "admin",
        adminResponse: response.trim() || undefined,
        newStatus,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["visit-requests"] });
      toast({ title: "Visit request updated" });
      onClose();
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't update",
        description: extractErrorMessage(e),
      }),
  });

  // Quick-action contact links — same pattern as the property-detail
  // dialog. Saves the admin a copy/paste round-trip.
  const phoneDigits = (request.visitorPhone ?? "").replace(/\D/g, "");

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 flex-wrap">
            {request.propertyLabel ?? `Flat ${request.flatId}`}
            <StatusBadge status={request.status} />
          </DialogTitle>
          <DialogDescription>
            <span className="font-medium text-foreground">{request.visitorName}</span>
            {request.visitorEmail && <> · {request.visitorEmail}</>}
            {request.visitorPhone && <> · {request.visitorPhone}</>}
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-xl border bg-secondary/30 p-4 space-y-2">
          <div className="flex items-center gap-2 text-sm">
            <Calendar className="size-4 text-primary" />
            <span className="font-medium">
              {request.preferredAt
                ? new Date(request.preferredAt).toLocaleString()
                : "No preferred slot specified"}
            </span>
          </div>
          {request.message && (
            <p className="text-sm whitespace-pre-wrap text-muted-foreground">
              {request.message}
            </p>
          )}
        </div>

        {(request.visitorPhone || request.visitorEmail) && (
          <div className="flex flex-wrap gap-2">
            {request.visitorPhone && (
              <Button asChild size="sm" variant="outline">
                <a href={`tel:${request.visitorPhone}`}>
                  <Phone /> Call
                </a>
              </Button>
            )}
            {phoneDigits && (
              <Button asChild size="sm" variant="outline">
                <a
                  href={`https://wa.me/${phoneDigits}?text=${encodeURIComponent(
                    `Hi ${request.visitorName}, this is Anirudh Homes about your visit to ${request.propertyLabel ?? "the property"}.`,
                  )}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  <Phone /> WhatsApp
                </a>
              </Button>
            )}
            {request.visitorEmail && (
              <Button asChild size="sm" variant="outline">
                <a
                  href={`mailto:${request.visitorEmail}?subject=${encodeURIComponent(
                    `Visit to ${request.propertyLabel ?? "your property"}`,
                  )}`}
                >
                  <Mail /> Email
                </a>
              </Button>
            )}
          </div>
        )}

        <div className="space-y-2">
          <label className="text-sm font-medium">Note for the visitor (optional)</label>
          <Textarea
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            rows={4}
            maxLength={2000}
            placeholder="Confirmed for the requested slot — building gate is on the south side, ask security for B-202."
          />
        </div>

        <div className="space-y-2">
          <label className="text-sm font-medium">New status</label>
          <Select
            value={newStatus}
            onValueChange={(v) => setNewStatus(v as VisitRequestStatus)}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PENDING">Pending</SelectItem>
              <SelectItem value="CONFIRMED">Confirmed</SelectItem>
              <SelectItem value="COMPLETED">Completed</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
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
            disabled={respondM.isPending}
          >
            {respondM.isPending && <Loader2 className="size-4 animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function StatusBadge({ status }: { status: VisitRequestStatus }) {
  switch (status) {
    case "PENDING":
      return <Badge variant="warning" className="text-[10px]">Pending</Badge>;
    case "CONFIRMED":
      return <Badge variant="default" className="text-[10px]">Confirmed</Badge>;
    case "COMPLETED":
      return <Badge variant="success" className="text-[10px]">Completed</Badge>;
    case "CANCELLED":
      return <Badge variant="destructive" className="text-[10px]">Cancelled</Badge>;
  }
}
