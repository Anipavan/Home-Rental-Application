import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Mail,
  Phone,
  Calendar,
  MapPin,
  ShieldCheck,
  Building2,
  Home,
  Copy,
  Check,
  Loader2,
  BadgeCheck,
  Ban,
  CheckCircle2,
  AlertTriangle,
} from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { usersApi } from "@/lib/api/users";
import { authApi } from "@/lib/api/auth";
import { useAuthStore } from "@/stores/auth-store";
import { toast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import { cn, formatDate, initials, normalizeDocUrl } from "@/lib/utils";
import type { AuthUserResponse, Role } from "@/types/api";

/**
 * Admin-only user detail dialog. Opens when an admin clicks any row on
 * /admin/users. Renders:
 *   • Large avatar (uses the user's profile photo if present) tinted
 *     by role — matches the row tint on the list view.
 *   • Auth-side identity: username, email, role, status, joined date,
 *     auth id (copyable).
 *   • User-service profile (lazily fetched the first time the dialog
 *     opens): first/last name, phone, address, gender, KYC status.
 *     Falls back gracefully when the profile hasn't been bootstrapped
 *     (404 from /users/by-auth-id is treated as "no profile yet" not
 *     an error).
 *   • Contact actions: mailto: + tel: + clipboard-copy helpers so the
 *     admin can reach out without leaving the dialog.
 *
 * Read-only on purpose. Editing user state (disable, role change,
 * password reset) is a future capability — the dialog reserves space
 * for those actions in its footer but ships without them.
 */
export function UserDetailDialog({
  user,
  open,
  onOpenChange,
}: {
  user: AuthUserResponse | null;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const role = (user?.role ?? (user?.userRole as Role) ?? "TENANT") as Role;
  const authId = user?.id ? String(user.id) : null;

  // ── Enable / Disable state ─────────────────────────────────────
  const qc = useQueryClient();
  const currentAdminId = useAuthStore((s) => s.authUserId);
  // Two-step confirm: click "Disable" opens the reason form + confirm
  // buttons; click Confirm to actually fire the mutation. Prevents a
  // fat-finger single-click from disabling someone irreversibly to the
  // user's day (they need to reach an admin to re-enable).
  const [confirmMode, setConfirmMode] = useState(false);
  const [reason, setReason] = useState("");
  // Reset the inline confirm state whenever the dialog re-targets a
  // different user or is closed. Without this, opening user B after
  // half-typing a reason for user A would carry the typed text over.
  useEffect(() => {
    setConfirmMode(false);
    setReason("");
  }, [authId, open]);

  const setEnabledMut = useMutation({
    mutationFn: (args: { id: string; enabled: boolean; reason?: string }) =>
      authApi.setUserEnabled(args.id, args.enabled, args.reason),
    onSuccess: (updated) => {
      toast({
        title: updated.isActive === false
          ? "Account disabled"
          : "Account enabled",
        description: updated.isActive === false
          ? `${updated.userName} can no longer sign in or use the app.`
          : `${updated.userName} can sign in again.`,
      });
      // Refresh every users query on the admin page so the badge on
      // the list view flips without a manual reload. Wildcard prefix
      // covers ["admin", "users-tenant"] / "users-owner" / "users-admin".
      qc.invalidateQueries({ queryKey: ["admin"] });
      // Also update the profile lookup that this dialog fetches
      // (kycStatus etc.) — no dependency on the enabled flag, but
      // consistent to refresh in case the admin re-opens.
      qc.invalidateQueries({ queryKey: ["admin", "user-profile", authId] });
      setConfirmMode(false);
      setReason("");
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't update the account",
        description: extractErrorMessage(err),
      });
    },
  });

  const isSelf = !!authId && !!currentAdminId && authId === currentAdminId;
  // Fall back to "Active" when isActive is undefined — a Feign consumer
  // that pre-dates the isActive field on AuthUserResponse would omit
  // it, and we don't want to render "Disabled" for every legacy row.
  const isDisabled = user?.isActive === false;
  const canToggle = !isSelf && !!authId;

  // Lazy: only fetches when the dialog is actually open AND we have an
  // id. Cached for 60s so re-opening the same user in quick succession
  // hits the cache instead of re-firing the request.
  const profileQ = useQuery({
    queryKey: ["admin", "user-profile", authId],
    queryFn: () => usersApi.byAuthId(authId!),
    enabled: open && !!authId,
    staleTime: 60_000,
    retry: (failureCount, err) => {
      // 404 = no user-service profile row exists yet (common for fresh
      // registrations that haven't completed onboarding). Show
      // "no profile yet" UX instead of bouncing through retries.
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 404 && failureCount < 1;
    },
  });

  const profile = profileQ.data;
  const photoUrl = normalizeDocUrl(profile?.profilePictureUrl);
  const fullName =
    profile?.firstName || profile?.lastName
      ? `${profile?.firstName ?? ""} ${profile?.lastName ?? ""}`.trim()
      : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="sr-only">User details</DialogTitle>
          <DialogDescription className="sr-only">
            Profile information and contact actions for the selected user.
          </DialogDescription>
        </DialogHeader>

        {!user ? null : (
          <div>
            {/* Hero strip — large avatar tinted by role, name + role
                badge below. Role-tinted background wash so the dialog
                "introduces" the user with the same visual language
                the row used on the list. */}
            <div
              className={cn(
                "rounded-xl p-5 -mx-1 -mt-2 mb-5",
                roleHeroClass(role),
              )}
            >
              <div className="flex items-center gap-4">
                <Avatar className="size-16 ring-4 ring-white/40">
                  {photoUrl && <AvatarImage src={photoUrl} alt={user.userName} />}
                  <AvatarFallback className={roleAvatarClass(role)}>
                    {initials(fullName ?? user.userName)}
                  </AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="font-display font-bold text-xl truncate">
                    {fullName ?? user.userName}
                  </p>
                  {fullName && (
                    <p className="text-xs text-muted-foreground font-mono truncate">
                      @{user.userName}
                    </p>
                  )}
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    <RoleBadge role={role} />
                    <Badge
                      variant={user.isActive === false ? "secondary" : "success"}
                    >
                      {user.isActive === false ? "Disabled" : "Active"}
                    </Badge>
                    {profile?.kycStatus === "VERIFIED" && (
                      <Badge variant="success">
                        <BadgeCheck className="size-3" /> KYC verified
                      </Badge>
                    )}
                    {profile?.kycStatus &&
                      profile.kycStatus !== "VERIFIED" && (
                        <Badge variant="warning">
                          KYC {profile.kycStatus.toLowerCase()}
                        </Badge>
                      )}
                  </div>
                </div>
              </div>
            </div>

            {/* Contact strip — email + phone with one-click actions.
                Phone only shown if the user-service profile carries
                one (auth-service alone has no phone field). */}
            <div className="space-y-3">
              {user.email && (
                <ContactRow
                  icon={Mail}
                  label="Email"
                  value={user.email}
                  href={`mailto:${user.email}`}
                  copyable
                />
              )}
              {profile?.phone && (
                <ContactRow
                  icon={Phone}
                  label="Phone"
                  value={profile.phone}
                  href={`tel:${profile.phone}`}
                  copyable
                />
              )}
              {profile?.address && (
                <ContactRow
                  icon={MapPin}
                  label="Address"
                  value={profile.address}
                />
              )}
            </div>

            <Separator className="my-5" />

            {/* Metadata — auth-side timestamps + raw id. */}
            <div className="space-y-2.5 text-sm">
              <MetaRow
                icon={Calendar}
                label="Joined"
                value={formatDate(user.recordCreatedDate ?? user.createdAt)}
              />
              <MetaRow
                icon={ShieldCheck}
                label="Auth ID"
                value={String(user.id)}
                mono
                copyable
              />
              {profile?.gender && (
                <MetaRow icon={Home} label="Gender" value={profile.gender} />
              )}
              {role === "TENANT" && profile?.tenantType && (
                <MetaRow
                  icon={Home}
                  label="Tenant type"
                  value={profile.tenantType.toLowerCase()}
                />
              )}
              {role === "OWNER" && (
                <MetaRow
                  icon={Building2}
                  label="Properties"
                  value="Visible in admin Properties tab"
                />
              )}
            </div>

            {profileQ.isLoading && (
              <div className="mt-4 flex items-center gap-2 text-xs text-muted-foreground">
                <Loader2 className="size-3 animate-spin" />
                Loading full profile…
              </div>
            )}

            {profileQ.isError && (
              <p className="mt-4 text-xs text-muted-foreground">
                No full profile on file yet — this user hasn't completed the
                onboarding form.
              </p>
            )}

            {/* ── Admin actions footer ─────────────────────────────
                Disable / Enable button — hidden entirely when the
                admin is viewing themselves (the backend also refuses
                self-toggle, but we don't even render the button so
                there's nothing to fat-finger). Two-step confirm
                pattern instead of a native window.confirm() so we
                can capture an optional audit reason for disables. */}
            {canToggle && (
              <>
                <Separator className="my-5" />
                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">Account access</p>
                      <p className="text-xs text-muted-foreground">
                        {isDisabled
                          ? "Currently disabled — the user can't sign in or use any part of the app."
                          : "Currently active — the user can sign in and use the app normally."}
                      </p>
                    </div>
                    {!confirmMode && (
                      <Button
                        type="button"
                        variant={isDisabled ? "default" : "destructive"}
                        onClick={() => setConfirmMode(true)}
                        disabled={setEnabledMut.isPending}
                      >
                        {isDisabled ? (
                          <>
                            <CheckCircle2 /> Enable account
                          </>
                        ) : (
                          <>
                            <Ban /> Disable account
                          </>
                        )}
                      </Button>
                    )}
                  </div>

                  {confirmMode && (
                    <div className={cn(
                      "rounded-xl border p-3.5",
                      isDisabled
                        ? "border-success/40 bg-success/5"
                        : "border-destructive/40 bg-destructive/5",
                    )}>
                      <div className="flex items-start gap-2.5 mb-3">
                        <AlertTriangle
                          className={cn(
                            "size-4 mt-0.5 shrink-0",
                            isDisabled ? "text-success" : "text-destructive",
                          )}
                        />
                        <div className="text-xs">
                          {isDisabled ? (
                            <>
                              Re-enable <span className="font-medium">{user.userName}</span>?
                              They'll be able to sign in immediately.
                            </>
                          ) : (
                            <>
                              Disable <span className="font-medium">{user.userName}</span>?
                              Their active session will be revoked within a minute
                              and every future request will fail with an
                              "account disabled" error.
                            </>
                          )}
                        </div>
                      </div>
                      {!isDisabled && (
                        <Input
                          value={reason}
                          onChange={(e) => setReason(e.target.value.slice(0, 60))}
                          placeholder="Optional reason (audit trail) — e.g. spam, fraud, user requested"
                          maxLength={60}
                          className="mb-3 text-sm"
                          autoFocus
                        />
                      )}
                      <div className="flex justify-end gap-2">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setConfirmMode(false);
                            setReason("");
                          }}
                          disabled={setEnabledMut.isPending}
                        >
                          Cancel
                        </Button>
                        <Button
                          type="button"
                          variant={isDisabled ? "default" : "destructive"}
                          size="sm"
                          onClick={() =>
                            setEnabledMut.mutate({
                              id: authId!,
                              enabled: isDisabled,
                              reason: isDisabled ? undefined : reason.trim() || undefined,
                            })
                          }
                          disabled={setEnabledMut.isPending}
                        >
                          {setEnabledMut.isPending ? (
                            <>
                              <Loader2 className="animate-spin" />
                              {isDisabled ? "Enabling…" : "Disabling…"}
                            </>
                          ) : isDisabled ? (
                            <>
                              <CheckCircle2 /> Confirm enable
                            </>
                          ) : (
                            <>
                              <Ban /> Confirm disable
                            </>
                          )}
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

/* ─────────────────────────── building blocks ─────────────────────────── */

function ContactRow({
  icon: Icon,
  label,
  value,
  href,
  copyable,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  href?: string;
  copyable?: boolean;
}) {
  return (
    <div className="flex items-center gap-3 p-3 rounded-xl border bg-card">
      <div className="size-9 rounded-lg bg-primary/10 text-primary grid place-items-center shrink-0">
        <Icon className="size-4" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
          {label}
        </p>
        {href ? (
          <a
            href={href}
            className="text-sm font-medium hover:text-primary hover:underline truncate block"
          >
            {value}
          </a>
        ) : (
          <p className="text-sm font-medium truncate">{value}</p>
        )}
      </div>
      {copyable && <CopyButton value={value} />}
    </div>
  );
}

function MetaRow({
  icon: Icon,
  label,
  value,
  mono,
  copyable,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  mono?: boolean;
  copyable?: boolean;
}) {
  return (
    <div className="flex items-center gap-3">
      <Icon className="size-4 text-muted-foreground shrink-0" />
      <span className="text-xs uppercase tracking-wider text-muted-foreground w-24 shrink-0">
        {label}
      </span>
      <span
        className={cn(
          "flex-1 truncate text-sm capitalize",
          mono && "font-mono text-xs normal-case",
        )}
        title={value}
      >
        {value}
      </span>
      {copyable && <CopyButton value={value} />}
    </div>
  );
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      className="size-7 shrink-0"
      onClick={async (e) => {
        e.stopPropagation();
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          toast({
            variant: "destructive",
            title: "Couldn't copy",
            description: "Your browser blocked clipboard access.",
          });
        }
      }}
    >
      {copied ? (
        <Check className="size-3.5 text-success" />
      ) : (
        <Copy className="size-3.5" />
      )}
    </Button>
  );
}

function RoleBadge({ role }: { role: Role }) {
  if (role === "ADMIN")
    return (
      <Badge>
        <ShieldCheck className="size-3" /> Admin
      </Badge>
    );
  if (role === "OWNER")
    return (
      <Badge variant="secondary">
        <Building2 className="size-3" /> Owner
      </Badge>
    );
  return (
    <Badge variant="secondary">
      <Home className="size-3" /> Tenant
    </Badge>
  );
}

/* ─────────────────────────── role styling ─────────────────────────── */

/**
 * Role-tinted background wash for the dialog hero strip + the list
 * row. Same hue family as the avatar fallback class so a tenant row's
 * teal wash continues into the dialog's teal hero, keeping the visual
 * identity continuous.
 *
 * <p>Light tints (50/100 with /60 alpha) so they read as a SURFACE,
 * not a chip — the text on top stays the default foreground colour
 * and remains legible without a contrast inversion.
 */
function roleHeroClass(role: Role): string {
  switch (role) {
    case "ADMIN":
      return "bg-purple-50/80 border border-purple-200/60";
    case "OWNER":
      return "bg-amber-50/80 border border-amber-200/60";
    case "TENANT":
    default:
      return "bg-sky-50/80 border border-sky-200/60";
  }
}

/**
 * Inner avatar background — saturated 500-shade circle with white
 * initials. Identical to the one used on /admin/users so the
 * "switching from list to detail" transition reads as the same user.
 */
function roleAvatarClass(role: Role): string {
  const base = "bg-none";
  switch (role) {
    case "ADMIN":
      return `${base} bg-purple-600 text-white`;
    case "OWNER":
      return `${base} bg-amber-500 text-white`;
    case "TENANT":
    default:
      return `${base} bg-sky-500 text-white`;
  }
}

/**
 * Export the row tint helper so /admin/users can tint its rows with
 * the same colour family as the dialog hero. Single source of truth
 * keeps the colour scheme consistent across the two surfaces.
 */
export function roleRowTintClass(role: Role): string {
  switch (role) {
    case "ADMIN":
      return "hover:bg-purple-50/60";
    case "OWNER":
      return "hover:bg-amber-50/60";
    case "TENANT":
    default:
      return "hover:bg-sky-50/60";
  }
}
