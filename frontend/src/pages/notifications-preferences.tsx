import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Bell,
  CheckCircle2,
  Loader2,
  Mail,
  MessageSquare,
  Phone,
  Smartphone,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import {
  preferencesApi,
  type NotificationPreferences,
  type NotificationPreferencesUpdate,
} from "@/lib/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";

/**
 * User-facing notification preferences page. Sits at
 *   /app/notifications/preferences
 * (and the role-equivalent /owner/* / /admin/*) — linked from the
 * notification inbox header.
 *
 * Surfaces four channel toggles + the contact-info fields they
 * depend on:
 *   - INAPP    (always on; can't be disabled because it backs the bell)
 *   - EMAIL    (default on; needs `email` set)
 *   - SMS      (default on; needs `phone` set)
 *   - WHATSAPP (default OFF — explicit opt-in for messaging-app traffic)
 *
 * The Save button POSTs the full payload via PUT /preferences/{id}; the
 * backend treats every field as optional so unchanged knobs aren't
 * touched.
 */
export function NotificationPreferencesPage() {
  const { authUserId } = useAuthStore();

  const q = useQuery({
    queryKey: ["notifications", "preferences", authUserId],
    queryFn: () => preferencesApi.get(authUserId!),
    enabled: !!authUserId,
  });

  const qc = useQueryClient();
  const saveM = useMutation({
    mutationFn: (body: NotificationPreferencesUpdate) =>
      preferencesApi.upsert(authUserId!, body),
    onSuccess: (saved) => {
      qc.setQueryData(["notifications", "preferences", authUserId], saved);
      toast({ title: "Preferences saved" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't save preferences",
        description: extractErrorMessage(e),
      }),
  });

  if (q.isLoading || !q.data) {
    return (
      <div className="animate-fade-in max-w-2xl space-y-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-48 rounded-2xl" />
        <Skeleton className="h-64 rounded-2xl" />
      </div>
    );
  }

  return (
    <PreferencesForm
      initial={q.data}
      onSave={(body) => saveM.mutate(body)}
      saving={saveM.isPending}
    />
  );
}

function PreferencesForm({
  initial,
  onSave,
  saving,
}: {
  initial: NotificationPreferences;
  onSave: (body: NotificationPreferencesUpdate) => void;
  saving: boolean;
}) {
  const [email, setEmail] = useState(initial.email ?? "");
  const [phone, setPhone] = useState(initial.phone ?? "");
  const [emailEnabled, setEmailEnabled] = useState(initial.emailEnabled);
  const [smsEnabled, setSmsEnabled] = useState(initial.smsEnabled);
  const [whatsappEnabled, setWhatsappEnabled] = useState(initial.whatsappEnabled);
  const [pushEnabled, setPushEnabled] = useState(initial.pushEnabled);

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    onSave({
      email: email.trim() || undefined,
      phone: phone.trim() || undefined,
      emailEnabled,
      smsEnabled,
      whatsappEnabled,
      pushEnabled,
    });
  }

  return (
    <form
      onSubmit={onSubmit}
      className="animate-fade-in max-w-2xl space-y-5"
    >
      <Button asChild variant="ghost" size="sm">
        <Link to="/app/notifications">
          <ArrowLeft /> Back to inbox
        </Link>
      </Button>

      <PageHeader
        title="Notification preferences"
        description="Pick how you want to hear from us. Toggle channels on or off any time."
      />

      {/* Contact info */}
      <Card>
        <CardContent className="p-6 space-y-4">
          <h3 className="font-display font-semibold">Contact info</h3>
          <p className="text-xs text-muted-foreground">
            We use these to deliver email and phone-based notifications.
            Email is mandatory at sign-up; phone is optional but unlocks
            SMS + WhatsApp.
          </p>
          <div>
            <Label htmlFor="email">Email address</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="mt-1.5"
            />
          </div>
          <div>
            <Label htmlFor="phone">Phone (E.164 format)</Label>
            <Input
              id="phone"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+91 98765 43210"
              className="mt-1.5"
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              Include the country code. Used for SMS + WhatsApp.
            </p>
          </div>
        </CardContent>
      </Card>

      {/* Channels */}
      <Card>
        <CardContent className="p-6 space-y-2">
          <h3 className="font-display font-semibold mb-1">Delivery channels</h3>
          <ChannelToggle
            label="In-app bell"
            description="Always on. Backs the notification dropdown."
            icon={Bell}
            enabled={true}
            disabled
            onToggle={() => undefined}
          />
          <ChannelToggle
            label="Email"
            description={
              email
                ? `Sent to ${email}.`
                : "Add an email above to receive these."
            }
            icon={Mail}
            enabled={emailEnabled}
            disabled={!email}
            onToggle={() => setEmailEnabled((v) => !v)}
          />
          <ChannelToggle
            label="SMS"
            description={
              phone
                ? `Text messages to ${phone}.`
                : "Add a phone number above to receive these."
            }
            icon={MessageSquare}
            enabled={smsEnabled}
            disabled={!phone}
            onToggle={() => setSmsEnabled((v) => !v)}
          />
          <ChannelToggle
            label="WhatsApp"
            description={
              phone
                ? `Messages via WhatsApp to ${phone}.`
                : "Add a phone number above and we'll route through WhatsApp."
            }
            icon={Phone}
            enabled={whatsappEnabled}
            disabled={!phone}
            onToggle={() => setWhatsappEnabled((v) => !v)}
            badge="Off by default"
          />
          <ChannelToggle
            label="Mobile push"
            description="Push notifications when the mobile app is installed."
            icon={Smartphone}
            enabled={pushEnabled}
            onToggle={() => setPushEnabled((v) => !v)}
          />
        </CardContent>
      </Card>

      <div className="flex justify-end gap-2">
        <Button asChild variant="ghost">
          <Link to="/app/notifications">Cancel</Link>
        </Button>
        <Button type="submit" variant="gradient" disabled={saving}>
          {saving ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <CheckCircle2 className="size-4" />
          )}
          Save preferences
        </Button>
      </div>
    </form>
  );
}

function ChannelToggle({
  label,
  description,
  icon: Icon,
  enabled,
  disabled,
  onToggle,
  badge,
}: {
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  enabled: boolean;
  disabled?: boolean;
  onToggle: () => void;
  badge?: string;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      aria-pressed={enabled}
      className={cn(
        "w-full flex items-start gap-3 rounded-xl border p-3.5 text-left transition-colors",
        disabled
          ? "border-border opacity-50 cursor-not-allowed"
          : enabled
            ? "border-primary/50 bg-primary/5 hover:bg-primary/10"
            : "border-border hover:bg-secondary/40",
      )}
    >
      <div
        className={cn(
          "size-9 rounded-lg grid place-items-center shrink-0",
          enabled
            ? "bg-primary text-primary-foreground"
            : "bg-secondary text-muted-foreground",
        )}
      >
        <Icon className="size-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm">{label}</span>
          {badge && (
            <span className="text-[10px] uppercase tracking-wider text-muted-foreground bg-secondary px-1.5 py-0.5 rounded">
              {badge}
            </span>
          )}
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
      </div>
      <div
        className={cn(
          "size-10 h-6 rounded-full p-0.5 transition-colors relative shrink-0",
          enabled ? "bg-primary" : "bg-secondary",
        )}
      >
        <span
          className={cn(
            "absolute top-0.5 size-5 rounded-full bg-white shadow-sm transition-transform",
            enabled ? "translate-x-4" : "translate-x-0",
          )}
        />
      </div>
    </button>
  );
}
