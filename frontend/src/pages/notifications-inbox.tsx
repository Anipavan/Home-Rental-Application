import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bell,
  BellRing,
  CheckCheck,
  Inbox,
  Mail,
  MessageSquare,
  Smartphone,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { notificationsApi } from "@/lib/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import type {
  NotificationResponse,
  NotificationType,
} from "@/types/api";

/**
 * Full notifications inbox. Mounted at:
 *   - /app/notifications      (TENANT)
 *   - /owner/notifications    (OWNER)
 *   - /admin/notifications    (ADMIN)
 *
 * <p>Differences vs. the bell dropdown:
 *  - Shows every notification, not just the latest 10.
 *  - Inline filter tabs (All / Unread).
 *  - Click any row to see the full subject + message body in a Dialog
 *    (LinkedIn / Facebook style — the list is preview, detail is modal).
 *  - "Mark all as read" button mirrors the bulk endpoint the bell calls
 *    on open, so users who arrive here from a deep link can clear the
 *    badge explicitly.
 */
export function NotificationsInboxPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [selected, setSelected] = useState<NotificationResponse | null>(null);

  const q = useQuery({
    queryKey: ["notifications", authUserId],
    queryFn: () => notificationsApi.byUser(authUserId!),
    enabled: !!authUserId,
  });

  const markAllReadM = useMutation({
    mutationFn: () => notificationsApi.markAllAsRead(authUserId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications", authUserId] });
      toast({ title: "All notifications marked as read" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't mark as read",
        description: extractErrorMessage(e),
      }),
  });

  const markOneReadM = useMutation({
    mutationFn: (id: string) => notificationsApi.markAsRead(id),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["notifications", authUserId] }),
  });

  // Strip the FAILED + SKIPPED operational rows for the same reason
  // the bell dropdown does: those are audit-log entries, not real
  // user-facing messages. Sort newest-first.
  const all = (q.data ?? [])
    .filter((n) => n.status !== "FAILED" && n.status !== "SKIPPED")
    .sort((a, b) => {
      const ta = a.sentAt ? new Date(a.sentAt).getTime() : 0;
      const tb = b.sentAt ? new Date(b.sentAt).getTime() : 0;
      return tb - ta;
    });
  const unread = all.filter(
    (n) => n.status !== "READ" && n.status !== "DELIVERED",
  );

  function openDetail(n: NotificationResponse) {
    setSelected(n);
    if (n.id && n.status !== "READ" && n.status !== "DELIVERED") {
      markOneReadM.mutate(n.id);
    }
  }

  const renderList = (rows: NotificationResponse[]) => {
    if (q.isLoading) {
      return (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-20 rounded-2xl" />
          ))}
        </div>
      );
    }
    if (rows.length === 0) {
      return (
        <Card className="p-12 text-center">
          <Inbox className="size-10 mx-auto text-muted-foreground" />
          <p className="font-display font-semibold text-lg mt-3">
            Nothing here yet
          </p>
          <p className="text-muted-foreground text-sm mt-1">
            New activity will appear in your inbox automatically.
          </p>
        </Card>
      );
    }
    return (
      <div className="space-y-2">
        {rows.map((n) => (
          <InboxRow key={n.id} n={n} onOpen={() => openDetail(n)} />
        ))}
      </div>
    );
  };

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Notifications"
        description="Every alert from your home, lease, payments, and complaints in one place."
        actions={
          unread.length > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => markAllReadM.mutate()}
              disabled={markAllReadM.isPending}
            >
              <CheckCheck className="size-4" /> Mark all as read
            </Button>
          )
        }
      />

      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All ({all.length})</TabsTrigger>
          <TabsTrigger value="unread">Unread ({unread.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="all">{renderList(all)}</TabsContent>
        <TabsContent value="unread">{renderList(unread)}</TabsContent>
      </Tabs>

      <NotificationDetailDialog
        notification={selected}
        onClose={() => setSelected(null)}
      />
    </div>
  );
}

function InboxRow({
  n,
  onOpen,
}: {
  n: NotificationResponse;
  onOpen: () => void;
}) {
  const Icon = iconFor(n.type);
  const unread = n.status !== "DELIVERED" && n.status !== "READ";
  return (
    <Card
      className={cn(
        "transition-colors cursor-pointer hover:border-primary/40",
        unread && "border-primary/30",
      )}
      onClick={onOpen}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen();
        }
      }}
    >
      <CardContent className="p-4 flex items-start gap-3">
        <div
          className={cn(
            "size-9 rounded-lg grid place-items-center shrink-0",
            unread
              ? "bg-primary/10 text-primary"
              : "bg-secondary text-muted-foreground",
          )}
        >
          <Icon className="size-4" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p
              className={cn(
                "text-sm truncate",
                unread ? "font-semibold" : "text-muted-foreground",
              )}
            >
              {n.subject ?? n.category ?? "Notification"}
            </p>
            {unread && (
              <span className="size-1.5 rounded-full bg-primary shrink-0" />
            )}
          </div>
          {n.message && (
            <p className="text-xs text-muted-foreground line-clamp-2 mt-1">
              {n.message}
            </p>
          )}
          <div className="flex items-center gap-2 mt-2 flex-wrap">
            <Badge variant="secondary" className="text-[10px] uppercase">
              {n.type}
            </Badge>
            {n.category && (
              <span className="text-[10px] text-muted-foreground">
                {prettyCategory(n.category)}
              </span>
            )}
            {n.sentAt && (
              <span className="text-[10px] text-muted-foreground ml-auto">
                {new Date(n.sentAt).toLocaleString()}
              </span>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/** Same detail dialog the bell uses. Duplicated locally (rather than
 * exported from notification-bell) so the bell module stays its own
 * unit and a future refactor of either surface doesn't drag the other
 * along. */
function NotificationDetailDialog({
  notification,
  onClose,
}: {
  notification: NotificationResponse | null;
  onClose: () => void;
}) {
  const open = notification !== null;
  return (
    <Dialog open={open} onOpenChange={(o) => (!o ? onClose() : undefined)}>
      <DialogContent className="max-w-lg">
        {notification && (
          <>
            <DialogHeader>
              <div className="flex items-start gap-3">
                <div className="size-10 rounded-xl bg-primary/10 text-primary grid place-items-center shrink-0">
                  {(() => {
                    const Icon = iconFor(notification.type);
                    return <Icon className="size-5" />;
                  })()}
                </div>
                <div className="flex-1 min-w-0">
                  <DialogTitle className="break-words">
                    {notification.subject ?? notification.category ?? "Notification"}
                  </DialogTitle>
                  <DialogDescription className="mt-1">
                    {notification.sentAt
                      ? new Date(notification.sentAt).toLocaleString()
                      : "Just now"}
                  </DialogDescription>
                </div>
              </div>
            </DialogHeader>

            <div className="space-y-4">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary" className="uppercase text-[10px]">
                  {notification.type}
                </Badge>
                {notification.category && (
                  <Badge variant="outline" className="text-[10px]">
                    {prettyCategory(notification.category)}
                  </Badge>
                )}
                {notification.status && (
                  <Badge
                    variant={
                      notification.status === "READ" ||
                      notification.status === "DELIVERED"
                        ? "outline"
                        : "default"
                    }
                    className="text-[10px]"
                  >
                    {notification.status}
                  </Badge>
                )}
              </div>

              <div className="rounded-xl border bg-secondary/30 px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap break-words">
                {notification.message?.trim()
                  ? notification.message
                  : "No message body — open the related page for details."}
              </div>

              {notification.errorMessage && (
                <p className="text-xs text-destructive">
                  Delivery note: {notification.errorMessage}
                </p>
              )}
              {notification.deliveredAt && (
                <p className="text-[11px] text-muted-foreground">
                  Delivered{" "}
                  {new Date(notification.deliveredAt).toLocaleString()}
                </p>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function iconFor(t: NotificationType) {
  switch (t) {
    case "EMAIL":
      return Mail;
    case "SMS":
      return MessageSquare;
    case "PUSH":
      return Smartphone;
    case "INAPP":
      return BellRing;
    default:
      return Bell;
  }
}

function prettyCategory(c: string): string {
  return c
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
