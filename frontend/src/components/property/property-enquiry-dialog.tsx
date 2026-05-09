import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Phone,
  Mail,
  Copy,
  Check,
  Loader2,
  UserCircle,
  Calendar,
  Send,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { usersApi } from "@/lib/api/users";
import { supportTicketsApi } from "@/lib/api/notifications";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/**
 * Property-detail enquiry dialogs.
 *
 * Two flavours sharing one form layer:
 *
 *   - mode="contact" — "Contact owner". For an authenticated visitor we
 *     try to look up the owner's user-service profile via
 *     {@link usersApi.byAuthId} and surface phone + email directly. The
 *     user-service primary id and authUserId are interchangeable in
 *     this codebase (see ContactPersonPopover usage), so passing the
 *     building's {@code ownerId} works either way. If the lookup
 *     succeeds we render a contact card; if it fails (404, public
 *     visitor with no token, or a partial profile) we fall back to the
 *     enquiry form.
 *
 *   - mode="visit" — "Schedule a visit". Always shows the form: visitor
 *     name / phone / email / preferred date / optional message. Logged-
 *     in users get their fields pre-filled from {@link useAuthStore} +
 *     the resolved profile.
 *
 * Both forms submit to {@link supportTicketsApi} so the existing
 * {@code /admin/support} inbox surfaces every lead. We tag the subject
 * (e.g. "Enquiry:" / "Visit request:") so admins can route it onward.
 */
interface Props {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  mode: "contact" | "visit";
  /** Building owner id from the building DTO. May be null if not loaded. */
  ownerId?: string | null;
  /** Human-readable property label, e.g. "Sunrise Residency · B-202". */
  propertyLabel: string;
  /** URL of the property detail page — recorded with the ticket. */
  contextUrl: string;
}

export function PropertyEnquiryDialog({
  open,
  onOpenChange,
  mode,
  ownerId,
  propertyLabel,
  contextUrl,
}: Props) {
  const { authUserId, userName, role, isAuthenticated } = useAuthStore();

  // Lazy: only fire the owner-lookup once the dialog opens. For the
  // visit flow we never need it — the form submits to support either
  // way — so skip the call entirely.
  const ownerQ = useQuery({
    queryKey: ["owner-contact", ownerId],
    queryFn: () => usersApi.byAuthId(ownerId!),
    enabled: !!ownerId && open && mode === "contact" && isAuthenticated,
    staleTime: 60_000,
    retry: false,
  });

  // Pre-fill the enquiry form from the logged-in user's profile when we
  // have it, otherwise leave blank and let them type. We don't gate the
  // form on this — it just makes the UX nicer for known visitors.
  const meQ = useQuery({
    queryKey: ["me", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId && open,
    staleTime: 60_000,
    retry: false,
  });

  const submitM = useMutation({
    mutationFn: supportTicketsApi.create,
    onSuccess: () => {
      onOpenChange(false);
      toast({
        title: mode === "contact" ? "Enquiry sent" : "Visit request sent",
        description:
          "We've notified the owner. You'll hear back within 24 hours.",
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't submit",
        description: extractErrorMessage(e),
      }),
  });

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const visitorName = String(fd.get("name") ?? "").trim();
    const visitorPhone = String(fd.get("phone") ?? "").trim();
    const visitorEmail = String(fd.get("email") ?? "").trim();
    const preferredDate = String(fd.get("preferredDate") ?? "").trim();
    const userMessage = String(fd.get("message") ?? "").trim();

    const subject =
      mode === "contact"
        ? `Enquiry: ${propertyLabel}`
        : `Visit request: ${propertyLabel}`;

    // Roll the contact + preferred-date fields into the message body so
    // an admin reviewing the ticket has everything in one place. The
    // dedicated support-ticket DTO doesn't have first-class fields for
    // these.
    const messageLines = [
      mode === "visit" && preferredDate
        ? `Preferred date: ${preferredDate}`
        : null,
      `Name: ${visitorName || "—"}`,
      `Phone: ${visitorPhone || "—"}`,
      `Email: ${visitorEmail || "—"}`,
      "",
      userMessage ||
        (mode === "contact"
          ? `I'm interested in ${propertyLabel}. Please reach out.`
          : `I'd like to schedule a visit to ${propertyLabel}.`),
    ].filter(Boolean) as string[];

    submitM.mutate({
      // Backend requires a non-blank userId. Public visitors get a
      // synthetic id; admins can spot these as anonymous-source leads.
      userId: authUserId ?? "PUBLIC_VISITOR",
      userName: visitorName || userName || undefined,
      userEmail: visitorEmail || meQ.data?.email || undefined,
      userRole: role ?? "PUBLIC",
      subject,
      message: messageLines.join("\n"),
      contextUrl,
    });
  }

  const isContact = mode === "contact";
  const owner = ownerQ.data;
  const showOwnerCard =
    isContact && isAuthenticated && !ownerQ.isLoading && !!owner;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isContact ? "Contact owner" : "Schedule a visit"}
          </DialogTitle>
          <DialogDescription>
            {isContact
              ? `Get in touch about ${propertyLabel}.`
              : `Pick a time to see ${propertyLabel} in person.`}
          </DialogDescription>
        </DialogHeader>

        {/* Owner card — only when we resolved the profile. Otherwise
            we fall through to the enquiry form below. */}
        {showOwnerCard && (
          <div className="rounded-xl border bg-secondary/30 p-4 space-y-3">
            <div className="flex items-center gap-3">
              <div className="size-10 rounded-full bg-primary/10 grid place-items-center">
                <UserCircle className="size-6 text-primary" />
              </div>
              <div>
                <p className="font-semibold">
                  {`${owner!.firstName ?? ""} ${owner!.lastName ?? ""}`.trim() ||
                    "Owner"}
                </p>
                <p className="text-xs text-muted-foreground">
                  Verified property owner
                </p>
              </div>
            </div>

            {owner!.phone ? (
              <a
                href={`tel:${owner!.phone}`}
                className="flex items-center gap-2 text-sm hover:bg-secondary rounded-md px-2 py-1.5 transition"
              >
                <Phone className="size-4" />
                <span className="flex-1">{owner!.phone}</span>
                <CopyChip value={owner!.phone} />
              </a>
            ) : (
              <p className="text-sm text-muted-foreground flex items-center gap-2 px-2">
                <Phone className="size-4" /> No phone on file
              </p>
            )}

            {owner!.email ? (
              <a
                href={`mailto:${owner!.email}?subject=${encodeURIComponent(
                  `Enquiry: ${propertyLabel}`,
                )}`}
                className="flex items-center gap-2 text-sm hover:bg-secondary rounded-md px-2 py-1.5 transition"
              >
                <Mail className="size-4" />
                <span className="flex-1 truncate">{owner!.email}</span>
              </a>
            ) : (
              <p className="text-sm text-muted-foreground flex items-center gap-2 px-2">
                <Mail className="size-4" /> No email on file
              </p>
            )}
          </div>
        )}

        {/* Loading state while fetching owner */}
        {isContact && ownerQ.isLoading && isAuthenticated && (
          <div className="text-sm text-muted-foreground flex items-center gap-2 py-4 justify-center">
            <Loader2 className="size-4 animate-spin" />
            Looking up owner contact…
          </div>
        )}

        {/* Form — shown when we don't have an owner card to display. For
            visit-mode this is always shown; for contact-mode it's the
            fallback when the owner profile isn't reachable. */}
        {(!showOwnerCard || !isContact) && (
          <form
            id="enquiry-form"
            className="space-y-3"
            onSubmit={handleSubmit}
          >
            {isContact && isAuthenticated && !ownerQ.isLoading && !owner && (
              <p className="text-xs text-muted-foreground">
                Couldn't load the owner's contact directly. Send a message
                below — we'll route it to them.
              </p>
            )}
            {!isAuthenticated && isContact && (
              <p className="text-xs text-muted-foreground">
                Not signed in? Leave your details below and the owner will
                reach out.
              </p>
            )}

            <div>
              <Label htmlFor="enq-name">Your name</Label>
              <Input
                id="enq-name"
                name="name"
                required
                defaultValue={
                  meQ.data
                    ? `${meQ.data.firstName ?? ""} ${meQ.data.lastName ?? ""}`.trim()
                    : (userName ?? "")
                }
                className="mt-1.5"
              />
            </div>
            <div className="grid sm:grid-cols-2 gap-3">
              <div>
                <Label htmlFor="enq-phone">Phone</Label>
                <Input
                  id="enq-phone"
                  name="phone"
                  type="tel"
                  required
                  defaultValue={meQ.data?.phone ?? ""}
                  className="mt-1.5"
                  placeholder="+91 …"
                />
              </div>
              <div>
                <Label htmlFor="enq-email">Email</Label>
                <Input
                  id="enq-email"
                  name="email"
                  type="email"
                  required
                  defaultValue={meQ.data?.email ?? ""}
                  className="mt-1.5"
                />
              </div>
            </div>

            {mode === "visit" && (
              <div>
                <Label htmlFor="enq-date" className="flex items-center gap-1.5">
                  <Calendar className="size-3.5" /> Preferred date & time
                </Label>
                <Input
                  id="enq-date"
                  name="preferredDate"
                  type="datetime-local"
                  required
                  className="mt-1.5"
                />
              </div>
            )}

            <div>
              <Label htmlFor="enq-message">Message</Label>
              <Textarea
                id="enq-message"
                name="message"
                rows={3}
                maxLength={1000}
                placeholder={
                  mode === "contact"
                    ? `I'm interested in ${propertyLabel}.`
                    : `Looking forward to seeing ${propertyLabel}.`
                }
                className="mt-1.5"
              />
            </div>
          </form>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={submitM.isPending}
          >
            {showOwnerCard ? "Close" : "Cancel"}
          </Button>
          {!showOwnerCard && (
            <Button
              form="enquiry-form"
              type="submit"
              variant="gradient"
              disabled={submitM.isPending}
            >
              {submitM.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Send className="size-4" />
              )}
              {isContact ? "Send enquiry" : "Request visit"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CopyChip({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      onClick={async (e) => {
        e.preventDefault();
        e.stopPropagation();
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1200);
        } catch {
          /* clipboard blocked — silently no-op */
        }
      }}
      className="text-muted-foreground hover:text-foreground"
      aria-label="Copy phone number"
    >
      {copied ? (
        <Check className="size-3.5 text-emerald-500" />
      ) : (
        <Copy className="size-3.5" />
      )}
    </button>
  );
}
