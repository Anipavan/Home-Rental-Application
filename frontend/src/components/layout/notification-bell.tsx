import { useQuery } from "@tanstack/react-query";
import {
  Bell,
  Mail,
  MessageSquare,
  Smartphone,
  Inbox,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { notificationsApi } from "@/lib/api/notifications";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import type { NotificationResponse, NotificationType } from "@/types/api";
import { cn } from "@/lib/utils";

/**
 * Notification bell in the AppShell header. Shows the latest 10 messages
 * for the signed-in user, with an unread count badge. Polls every 60 s
 * while the page is focused.
 */
const POLL_INTERVAL_MS = 60_000;

export function NotificationBell() {
  const { authUserId, isAuthenticated } = useAuthStore();

  const q = useQuery({
    queryKey: ["notifications", authUserId],
    queryFn: () => notificationsApi.byUser(authUserId!),
    enabled: !!authUserId && isAuthenticated,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: 1,
  });

  const items = (q.data ?? []).slice(0, 10);
  // Status values: PENDING / SENT / DELIVERED / FAILED / SKIPPED. We treat
  // the absence of an explicit "read" flag as "unread = anything sent today
  // and not delivered yet." A first-pass heuristic — replace with a real
  // read marker on the server when we add per-user read state.
  const unreadCount = items.filter(
    (n) => n.status !== "DELIVERED" && n.status !== "READ",
  ).length;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Notifications" className="relative">
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
          <ul className="max-h-96 overflow-y-auto">
            {items.map((n, i) => (
              <NotificationRow key={n.id ?? i} n={n} />
            ))}
          </ul>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function NotificationRow({ n }: { n: NotificationResponse }) {
  const Icon = iconFor(n.type);
  const unread = n.status !== "DELIVERED" && n.status !== "READ";
  return (
    <li className="px-3 py-2.5 hover:bg-secondary/60 border-b border-border/40 last:border-b-0">
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
    </li>
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
