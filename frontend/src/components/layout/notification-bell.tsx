import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bell,
  BellRing,
  Mail,
  MessageSquare,
  Smartphone,
  Inbox,
  ArrowRight,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { notificationsApi } from "@/lib/api/notifications";
import { useNotificationStream } from "@/hooks/use-notification-stream";
import { toast } from "@/hooks/use-toast";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import type { NotificationResponse, NotificationType } from "@/types/api";
import { cn } from "@/lib/utils";

/**
 * Notification bell in the AppShell header. Shows the latest 10 messages
 * for the signed-in user, with an unread count badge.
 *
 * <p>Two delivery paths fight for low latency:
 * <ul>
 *   <li>{@link useNotificationStream} — Server-Sent Events from
 *       /notifications/stream. When a cross-role event hits the
 *       backend it pushes here within ~30 ms. The hook invalidates
 *       this query on every push so the list refreshes.</li>
 *   <li>HTTP polling at {@code POLL_INTERVAL_MS} as a safety net for
 *       when SSE drops (browser sleep, gateway restart, etc.). With
 *       SSE working the poll is mostly redundant — kept at 60 s.</li>
 * </ul>
 *
 * <p>"Read" tracking: opening the dropdown calls
 * {@code PUT /notifications/user/{userId}/read-all} to clear the
 * badge, with an optimistic cache update so the UI flips instantly.
 *
 * <p>Click-to-expand: rows are buttons that open a Dialog with the
 * full subject + message + metadata. Mirrors LinkedIn / Facebook —
 * the dropdown is a preview, the dialog is the detail view. The
 * "See all" link at the bottom navigates to the full inbox at
 * {@code /app/notifications} (or the role-appropriate variant).
 */
const POLL_INTERVAL_MS = 60_000;

export function NotificationBell() {
  const { authUserId, isAuthenticated, role } = useAuthStore();
  const qc = useQueryClient();
  // The currently-open notification detail. null = dialog closed.
  // Lives at the bell level (not per-row) so opening one notification
  // closes any previously open one cleanly and Radix only ever mounts
  // a single Dialog instance.
  const [selected, setSelected] = useState<NotificationResponse | null>(null);

  const q = useQuery({
    queryKey: ["notifications", authUserId],
    queryFn: () => notificationsApi.byUser(authUserId!),
    enabled: !!authUserId && isAuthenticated,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: 1,
  });

  // Real-time push subscription. On every server push we:
  //   1. Invalidate the bell query so the dropdown re-renders.
  //   2. Surface a small toast with the new notification's subject +
  //      message snippet so the user sees activity even when the
  //      bell is closed. Falls back to a generic copy if the
  //      server-sent payload is missing fields (malformed SSE
  //      payload, JSON parse error, etc.).
  useNotificationStream(authUserId, (payload) => {
    qc.invalidateQueries({ queryKey: ["notifications", authUserId] });
    toast({
      title: payload.subject?.trim() || "New notification",
      description:
        typeof payload.message === "string" && payload.message.trim().length > 0
          ? payload.message
          : "Open the bell to read it.",
    });
  });

  // Mark-all-as-read mutation with optimistic cache update.
  const markAllReadM = useMutation({
    mutationFn: () => notificationsApi.markAllAsRead(authUserId!),
    onMutate: async () => {
      await qc.cancelQueries({ queryKey: ["notifications", authUserId] });
      const previous = qc.getQueryData<NotificationResponse[]>([
        "notifications",
        authUserId,
      ]);
      // Flip every PENDING/SENT row to READ in the cache so the badge
      // clears the instant the user opens the dropdown.
      if (previous) {
        qc.setQueryData<NotificationResponse[]>(
          ["notifications", authUserId],
          previous.map((n) =>
            n.status === "PENDING" || n.status === "SENT"
              ? { ...n, status: "READ" as const }
              : n,
          ),
        );
      }
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      // Roll back the optimistic update on failure.
      if (ctx?.previous) {
        qc.setQueryData(["notifications", authUserId], ctx.previous);
      }
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ["notifications", authUserId] });
    },
  });

  // Per-row mark-as-read — fires when the user clicks a row to read the
  // full message. The dropdown-open path already flips everything to
  // READ, but this covers the case where the user navigates to a
  // notification deep-link directly (or opens the dialog without ever
  // closing the dropdown). Idempotent on the server.
  const markOneReadM = useMutation({
    mutationFn: (id: string) => notificationsApi.markAsRead(id),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["notifications", authUserId] }),
  });

  // Hide the operational noise so the bell only shows user-facing
  // notifications:
  //   - FAILED: usually "no recipient for channel=EMAIL" — the INAPP
  //     sibling carries the same content, so the FAILED row is a
  //     duplicate from the user's perspective.
  //   - SKIPPED: opt-out audit (e.g. "channel SMS disabled by user").
  // Status values that DO show: PENDING / SENT / DELIVERED / READ.
  const visible = (q.data ?? []).filter(
    (n) => n.status !== "FAILED" && n.status !== "SKIPPED",
  );
  // Sort newest-first by sentAt so freshly-fanned cross-role events
  // float to the top of the dropdown.
  visible.sort((a, b) => {
    const ta = a.sentAt ? new Date(a.sentAt).getTime() : 0;
    const tb = b.sentAt ? new Date(b.sentAt).getTime() : 0;
    return tb - ta;
  });
  const items = visible.slice(0, 10);
  // A row is "unread" until the user opens the bell — backend marks
  // it READ via the bulk endpoint. INAPP entries land as SENT (the
  // dispatcher's Inapp adapter is synchronous-success), so they
  // count as unread until the badge-clearing mutation fires.
  const unreadCount = items.filter(
    (n) => n.status !== "READ" && n.status !== "DELIVERED",
  ).length;

  const inboxPath =
    role === "OWNER"
      ? "/owner/notifications"
      : role === "ADMIN"
        ? "/admin/notifications"
        : "/app/notifications";

  function openDetail(n: NotificationResponse) {
    setSelected(n);
    // Best-effort: flip this single row to READ on the server. We don't
    // block on it — the cache flip happened on dropdown-open already.
    if (n.id && n.status !== "READ" && n.status !== "DELIVERED") {
      markOneReadM.mutate(n.id);
    }
  }

  return (
    <>
      <DropdownMenu
        onOpenChange={(open) => {
          // Mark everything visible as read the moment the dropdown
          // opens — the optimistic update inside markAllReadM flips
          // the cache instantly; the network call follows. Only fire
          // when there's actually something to clear.
          if (open && unreadCount > 0 && authUserId) {
            markAllReadM.mutate();
          }
        }}
      >
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Notifications"
            className="relative"
          >
            <Bell />
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 h-4 min-w-4 px-1 rounded-full bg-destructive text-destructive-foreground text-[10px] font-semibold grid place-items-center">
                {unreadCount > 9 ? "9+" : unreadCount}
              </span>
            )}
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-80 p-0">
          <DropdownMenuLabel className="flex items-center justify-between px-3 py-2">
            <span>Notifications</span>
            {q.isFetching && (
              <span className="text-[10px] text-muted-foreground font-normal">
                Refreshing…
              </span>
            )}
          </DropdownMenuLabel>
          <DropdownMenuSeparator />

          {q.isLoading ? (
            <div className="px-3 py-6 text-center text-sm text-muted-foreground">
              Loading…
            </div>
          ) : items.length === 0 ? (
            <div className="px-3 py-8 text-center">
              <Inbox className="size-7 mx-auto text-muted-foreground" />
              <p className="text-sm text-muted-foreground mt-2">
                No notifications yet
              </p>
              <p className="text-xs text-muted-foreground/80 mt-0.5">
                You'll see rent reminders, maintenance updates and lease
                alerts here.
              </p>
            </div>
          ) : (
            <>
              <ul className="max-h-96 overflow-y-auto">
                {items.map((n, i) => (
                  <NotificationRow
                    key={n.id ?? i}
                    n={n}
                    onOpen={() => openDetail(n)}
                  />
                ))}
              </ul>
              <DropdownMenuSeparator />
              <Link
                to={inboxPath}
                className="flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium text-primary hover:bg-secondary/60 transition-colors"
              >
                See all notifications
                <ArrowRight className="size-3" />
              </Link>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Detail dialog — opens when a row is clicked. */}
      <NotificationDetailDialog
        notification={selected}
        onClose={() => setSelected(null)}
      />
    </>
  );
}

function NotificationRow({
  n,
  onOpen,
}: {
  n: NotificationResponse;
  onOpen: () => void;
}) {
  const Icon = iconFor(n.type);
  const unread = n.status !== "DELIVERED" && n.status !== "READ";
  return (
    <li className="border-b border-border/40 last:border-b-0">
      <button
        type="button"
        onClick={onOpen}
        className="w-full text-left px-3 py-2.5 hover:bg-secondary/60 transition-colors"
      >
        <div className="flex items-start gap-2.5">
          <div
            className={cn(
              "size-7 rounded-lg grid place-items-center shrink-0",
              unread
                ? "bg-primary/10 text-primary"
                : "bg-secondary text-muted-foreground",
            )}
          >
            <Icon className="size-3.5" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1.5">
              <p
                className={cn(
                  "text-sm truncate",
                  unread ? "font-medium" : "text-muted-foreground",
                )}
              >
                {n.subject ?? n.category ?? "Notification"}
              </p>
              {unread && (
                <span className="size-1.5 rounded-full bg-primary shrink-0" />
              )}
            </div>
            {n.message && (
              <p className="text-xs text-muted-foreground line-clamp-2 mt-0.5">
                {n.message}
              </p>
            )}
            <div className="flex items-center gap-2 mt-1">
              <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
                {n.type}
              </span>
              {n.sentAt && (
                <span className="text-[10px] text-muted-foreground">
                  · {timeAgo(n.sentAt)}
                </span>
              )}
            </div>
          </div>
        </div>
      </button>
    </li>
  );
}

/**
 * Full-content dialog popped open when a row is clicked. Mirrors the
 * LinkedIn / Facebook UX — the bell list is a preview, this is the
 * detail. Shows the complete subject + message body (no truncation),
 * delivery channel, category, timestamps, and any error/retry metadata
 * recorded by the dispatcher.
 */
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

              {/* The actual message body. whitespace-pre-wrap preserves
                  template newlines from the notification-service
                  rendering pipeline. break-words handles long unbroken
                  strings (URLs, ids) without overflowing the dialog. */}
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
      // The default channel for cross-role events. A different bell
      // glyph from the trigger button so the row icon doesn't visually
      // collide with the header.
      return BellRing;
    default:
      return Bell;
  }
}

function timeAgo(iso: string) {
  const ms = Date.now() - new Date(iso).getTime();
  if (ms < 60_000) return "just now";
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m`;
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h`;
  return `${Math.floor(ms / 86_400_000)}d`;
}

/** {@code "MAINTENANCE_CREATED"} → {@code "Maintenance created"}. */
function prettyCategory(c: string): string {
  return c
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
