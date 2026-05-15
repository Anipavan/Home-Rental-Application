import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Calendar,
  Inbox,
  Loader2,
  Phone,
  Mail,
  MessageCircle,
  ArrowUpRight,
} from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import {
  supportTicketsApi,
  visitRequestsApi,
  type SupportTicket,
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
 * Owner-side enquiries inbox. Surfaces every contact-owner enquiry and
 * every visit request that came in via the public property-detail page,
 * scoped to this owner via the denormalised {@code ownerId} field on
 * both notification-service entities.
 *
 * <p>Owner sees these without needing an admin login — admins still
 * get the unfiltered global view at {@code /admin/support} and
 * {@code /admin/visit-requests}.
 */
export function OwnerEnquiriesPage() {
  const { authUserId } = useAuthStore();

  return (
    <div className="animate-fade-in max-w-5xl">
      <PageHeader
        title="Enquiries"
        description="People who contacted you about your buildings — visit bookings and direct enquiries."
      />

      {!authUserId ? null : (
        <Tabs defaultValue="visits">
          <TabsList>
            <TabsTrigger value="visits">
              <Calendar className="size-3.5" /> Visit requests
            </TabsTrigger>
            <TabsTrigger value="enquiries">
              <Inbox className="size-3.5" /> Enquiries
            </TabsTrigger>
          </TabsList>
          <TabsContent value="visits" className="mt-4">
            <VisitsList ownerId={authUserId} />
          </TabsContent>
          <TabsContent value="enquiries" className="mt-4">
            <EnquiriesList ownerId={authUserId} />
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}

/* ───────────────── visits ───────────────── */

function VisitsList({ ownerId }: { ownerId: string }) {
  const q = useQuery({
    queryKey: ["owner-visit-requests", ownerId],
    queryFn: () => visitRequestsApi.byOwner(ownerId, 0, 50),
    staleTime: 30_000,
  });
  const [active, setActive] = useState<VisitRequest | null>(null);

  if (q.isLoading) return <ListSkeleton />;
  const items = q.data?.content ?? [];
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Calendar className="size-10 mx-auto text-muted-foreground" />}
        title="No visit requests yet"
        message="Once someone schedules a visit on a public property page, it'll show up here."
      />
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
                  <VisitStatusBadge status={v.status} />
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
              </div>
            </div>
          </button>
        ))}
      </div>
      {active && (
        <VisitDialog request={active} onClose={() => setActive(null)} />
      )}
    </>
  );
}

function VisitDialog({
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
        respondedBy: authUserId ?? "owner",
        adminResponse: response.trim() || undefined,
        newStatus,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-visit-requests"] });
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

  const phoneDigits = (request.visitorPhone ?? "").replace(/\D/g, "");

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 flex-wrap">
            {request.propertyLabel ?? `Flat ${request.flatId}`}
            <VisitStatusBadge status={request.status} />
          </DialogTitle>
          <DialogDescription>
            <span className="font-medium text-foreground">
              {request.visitorName}
            </span>
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

        <ContactActions
          phone={request.visitorPhone}
          phoneDigits={phoneDigits}
          email={request.visitorEmail}
          name={request.visitorName}
          subject={`Visit to ${request.propertyLabel ?? "your property"}`}
        />

        <div className="space-y-2">
          <label className="text-sm font-medium">
            Note for the visitor (optional)
          </label>
          <Textarea
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            rows={4}
            maxLength={2000}
            placeholder="Confirmed for the requested slot — building gate is on the south side."
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

/* ───────────────── enquiries (support tickets) ───────────────── */

function EnquiriesList({ ownerId }: { ownerId: string }) {
  const q = useQuery({
    queryKey: ["owner-support-tickets", ownerId],
    queryFn: () => supportTicketsApi.byOwner(ownerId, 0, 50),
    staleTime: 30_000,
  });
  const [active, setActive] = useState<SupportTicket | null>(null);

  if (q.isLoading) return <ListSkeleton />;
  const items = q.data?.content ?? [];
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Inbox className="size-10 mx-auto text-muted-foreground" />}
        title="No enquiries yet"
        message="Direct contact-owner messages from the public property pages land here."
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
                  <SupportStatusBadge status={t.status} />
                  {t.userId === "PUBLIC_VISITOR" && (
                    <Badge variant="secondary" className="text-[10px]">
                      Public
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
        <EnquiryDialog ticket={active} onClose={() => setActive(null)} />
      )}
    </>
  );
}

function EnquiryDialog({
  ticket,
  onClose,
}: {
  ticket: SupportTicket;
  onClose: () => void;
}) {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [response, setResponse] = useState(ticket.adminResponse ?? "");
  const [newStatus, setNewStatus] =
    useState<"IN_PROGRESS" | "RESOLVED" | "CLOSED">("RESOLVED");

  const respondM = useMutation({
    mutationFn: () =>
      supportTicketsApi.respond(ticket.id, {
        respondedBy: authUserId ?? "owner",
        adminResponse: response.trim(),
        newStatus,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-support-tickets"] });
      toast({ title: "Reply sent" });
      onClose();
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't reply",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {ticket.subject}
            <SupportStatusBadge status={ticket.status} />
          </DialogTitle>
          <DialogDescription>
            From <strong>{ticket.userName ?? "Unknown"}</strong> ·{" "}
            {ticket.userEmail ?? ticket.userId}
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-xl border bg-secondary/30 p-4 max-h-48 overflow-y-auto whitespace-pre-wrap text-sm">
          {ticket.message}
        </div>

        <ContactActions
          email={ticket.userEmail}
          name={ticket.userName}
          subject={`Re: ${ticket.subject}`}
        />

        <div className="space-y-2">
          <label className="text-sm font-medium">Your reply</label>
          <Textarea
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            rows={5}
            maxLength={4000}
            placeholder="Thanks for getting in touch — happy to discuss. The flat is available from…"
          />
        </div>

        <div className="space-y-2">
          <label className="text-sm font-medium">New status</label>
          <Select
            value={newStatus}
            onValueChange={(v) => setNewStatus(v as typeof newStatus)}
          >
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
            Send &amp; update
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/* ───────────────── shared bits ───────────────── */

function ContactActions({
  phone,
  phoneDigits,
  email,
  name,
  subject,
}: {
  phone?: string;
  phoneDigits?: string;
  email?: string;
  name?: string;
  subject: string;
}) {
  if (!phone && !email) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {phone && (
        <Button asChild size="sm" variant="outline">
          <a href={`tel:${phone}`}>
            <Phone /> Call
          </a>
        </Button>
      )}
      {phoneDigits && (
        <Button asChild size="sm" variant="outline">
          <a
            href={`https://wa.me/${phoneDigits}?text=${encodeURIComponent(
              `Hi ${name ?? "there"}, this is your owner via Anirudh Homes.`,
            )}`}
            target="_blank"
            rel="noreferrer"
          >
            <MessageCircle /> WhatsApp
          </a>
        </Button>
      )}
      {email && (
        <Button asChild size="sm" variant="outline">
          <a
            href={`mailto:${email}?subject=${encodeURIComponent(subject)}`}
          >
            <Mail /> Email
          </a>
        </Button>
      )}
    </div>
  );
}

function ListSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <Skeleton key={i} className="h-24 rounded-xl" />
      ))}
    </div>
  );
}

function EmptyState({
  icon,
  title,
  message,
}: {
  icon: React.ReactNode;
  title: string;
  message: string;
}) {
  return (
    <Card>
      <CardContent className="p-12 text-center">
        {icon}
        <p className="font-medium mt-3">{title}</p>
        <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
          {message}
        </p>
      </CardContent>
    </Card>
  );
}

function VisitStatusBadge({ status }: { status: VisitRequestStatus }) {
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

function SupportStatusBadge({ status }: { status: SupportTicket["status"] }) {
  switch (status) {
    case "OPEN":
      return <Badge variant="warning" className="text-[10px]">Open</Badge>;
    case "IN_PROGRESS":
      return <Badge variant="secondary" className="text-[10px]">In progress</Badge>;
    case "RESOLVED":
      return <Badge variant="success" className="text-[10px]">Resolved</Badge>;
    case "CLOSED":
      return <Badge variant="secondary" className="text-[10px]">Closed</Badge>;
  }
}
