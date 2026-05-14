import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  CheckCircle2,
  Loader2,
  Megaphone,
  Send,
  ShieldCheck,
  Users,
} from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { notificationsApi } from "@/lib/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { AuthUserResponse } from "@/types/api";

/**
 * Issue #9 — admin enhancement: platform-wide announcement composer.
 *
 * <p>Admin types a subject + message, picks an audience (everyone, all
 * tenants, all owners, or admins-only), then the SPA resolves the
 * audience client-side via {@code authApi.byRole} and posts the list
 * of auth user IDs to {@code POST /notifications/admin/broadcast}.
 *
 * <p>Backend (NotificationService.broadcast) fans the message out to
 * every recipient on INAPP (the bell) and EMAIL with the
 * ADMIN_BROADCAST category — users who muted that category in their
 * preferences will silently skip. Response shows a per-channel
 * delivery summary so the admin knows what landed.
 */
type Audience = "ALL" | "TENANT" | "OWNER" | "ADMIN";

export function AdminAnnouncementsPage() {
  const [audience, setAudience] = useState<Audience>("ALL");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");

  // Pull all three role lists — the audience picker switches between
  // them client-side. Cached for 60s so flipping the radio doesn't
  // re-fetch.
  const tenantsQ = useQuery({
    queryKey: ["admin", "users-tenant"],
    queryFn: () => authApi.byRole("TENANT"),
    staleTime: 60_000,
  });
  const ownersQ = useQuery({
    queryKey: ["admin", "users-owner"],
    queryFn: () => authApi.byRole("OWNER"),
    staleTime: 60_000,
  });
  const adminsQ = useQuery({
    queryKey: ["admin", "users-admin"],
    queryFn: () => authApi.byRole("ADMIN"),
    staleTime: 60_000,
  });

  const tenants = tenantsQ.data ?? [];
  const owners = ownersQ.data ?? [];
  const admins = adminsQ.data ?? [];

  const audienceUsers: AuthUserResponse[] = useMemo(() => {
    switch (audience) {
      case "TENANT":
        return tenants;
      case "OWNER":
        return owners;
      case "ADMIN":
        return admins;
      default:
        return [...tenants, ...owners, ...admins];
    }
  }, [audience, tenants, owners, admins]);

  // Strip null/blank IDs defensively — the backend will reject the
  // whole batch if any element is empty (jakarta @NotEmpty).
  const audienceIds = useMemo(
    () =>
      audienceUsers
        .map((u) => String(u.id ?? ""))
        .filter((id) => id && id !== "undefined"),
    [audienceUsers],
  );

  const sendM = useMutation({
    mutationFn: () =>
      notificationsApi.broadcast({
        userIds: audienceIds,
        subject: subject.trim(),
        message: message.trim(),
      }),
    onSuccess: (res) => {
      toast({
        title: "Announcement sent",
        description: `Delivered to ${res.inapp} inbox${
          res.inapp === 1 ? "" : "es"
        } (${res.emails} email${res.emails === 1 ? "" : "s"} dispatched, ${
          res.skipped
        } skipped).`,
      });
      setSubject("");
      setMessage("");
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Broadcast failed",
        description: extractErrorMessage(e),
      }),
  });

  const loading =
    tenantsQ.isLoading || ownersQ.isLoading || adminsQ.isLoading;
  const canSend =
    !loading &&
    audienceIds.length > 0 &&
    subject.trim().length > 0 &&
    message.trim().length > 0 &&
    !sendM.isPending;

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Announcements"
        description="Send a platform-wide message to every user, or filter by role."
      />

      <Card>
        <CardContent className="p-6 sm:p-8 space-y-5">
          {/* Audience picker — radio-like Select keeps the surface
              compact and the underlying state simple. */}
          <div>
            <Label>Audience</Label>
            <div className="mt-2 flex flex-wrap items-center gap-3">
              <Select
                value={audience}
                onValueChange={(v) => setAudience(v as Audience)}
              >
                <SelectTrigger className="w-64">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">
                    Everyone ({tenants.length + owners.length + admins.length})
                  </SelectItem>
                  <SelectItem value="TENANT">
                    All tenants ({tenants.length})
                  </SelectItem>
                  <SelectItem value="OWNER">
                    All owners ({owners.length})
                  </SelectItem>
                  <SelectItem value="ADMIN">
                    All admins ({admins.length})
                  </SelectItem>
                </SelectContent>
              </Select>
              <Badge variant="secondary" className="gap-1.5">
                <Users className="size-3" />
                {audienceIds.length} recipient
                {audienceIds.length === 1 ? "" : "s"}
              </Badge>
            </div>
          </div>

          {/* Subject — appears as the bell row title + email subject. */}
          <div>
            <Label htmlFor="subject">Subject</Label>
            <Input
              id="subject"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              maxLength={200}
              placeholder="Scheduled maintenance window — 14 May, 02:00–04:00 IST"
              className="mt-1.5"
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              {subject.length}/200
            </p>
          </div>

          {/* Message body — rendered as plain text in the bell and
              HTML-escaped before going into the email so admins can't
              accidentally inject markup. */}
          <div>
            <Label htmlFor="message">Message</Label>
            <Textarea
              id="message"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              maxLength={4000}
              rows={8}
              placeholder="Hi everyone,\n\nWe'll be running scheduled maintenance on Tuesday between 02:00 and 04:00 IST. The app may be intermittently unavailable.\n\n— The Hearth team"
              className="mt-1.5"
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              {message.length}/4000
            </p>
          </div>

          {/* Pre-send summary banner — surfaces the impact of clicking
              Send so admins don't accidentally page 10k inboxes. */}
          <div className="rounded-xl border bg-secondary/30 p-4 text-sm flex items-start gap-3">
            <Megaphone className="size-4 mt-0.5 shrink-0 text-primary" />
            <div className="flex-1">
              <p className="font-medium">Ready to broadcast</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                The message will appear as a new bell entry for every
                recipient, and an email will be dispatched to anyone with
                an address on file. Recipients who muted "Admin broadcasts"
                in their notification preferences will be skipped.
              </p>
            </div>
          </div>

          <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 pt-1">
            <Button
              variant="ghost"
              onClick={() => {
                setSubject("");
                setMessage("");
              }}
              disabled={sendM.isPending}
            >
              Clear
            </Button>
            <Button
              variant="gradient"
              disabled={!canSend}
              onClick={() => sendM.mutate()}
            >
              {sendM.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Send className="size-4" />
              )}
              Send announcement
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Audit-trail hint — admins should know broadcasts persist as
          NotificationLog rows and are queryable via the admin
          notifications endpoint. */}
      <div className="mt-4 text-xs text-muted-foreground flex items-center gap-2">
        <ShieldCheck className="size-3.5 text-success" />
        Every broadcast is recorded in the notification audit log with
        per-recipient delivery status.
      </div>

      {sendM.isSuccess && sendM.data && (
        <Card className="mt-4 border-success/30 bg-success/5">
          <CardContent className="p-4 sm:p-5 flex items-start gap-3 text-sm">
            <CheckCircle2 className="size-5 text-success mt-0.5 shrink-0" />
            <div>
              <p className="font-medium">Last broadcast complete</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                {sendM.data.inapp} in-app pings · {sendM.data.emails} emails
                dispatched · {sendM.data.skipped} skipped (out of{" "}
                {sendM.data.total} recipients).
              </p>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
