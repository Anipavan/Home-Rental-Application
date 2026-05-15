import { useState } from "react";
import { useLocation } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import {
  HelpCircle,
  Mail,
  MessageSquare,
  Send,
  Loader2,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { supportTicketsApi } from "@/lib/api/notifications";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/**
 * "Contact support" entry-point. Three options:
 *  1. Email — `mailto:` to support@anirudhhomes.in
 *  2. WhatsApp — `wa.me` deep link
 *  3. In-app form — POST to {@link supportTicketsApi.create}; admins see it
 *     under `/admin/support`.
 *
 * Used twice in the app shell — sidebar bottom card + dropdown menu item.
 */

const SUPPORT_EMAIL = "support@anirudhhomes.in";
const SUPPORT_WHATSAPP = "919999999999"; // E.164 without `+`
const SUPPORT_WHATSAPP_DISPLAY = "+91 99999 99999";

interface Props {
  variant?: "button" | "menu-item";
  className?: string;
}

export function ContactSupport({ variant = "button", className }: Props) {
  const [formOpen, setFormOpen] = useState(false);

  const trigger =
    variant === "menu-item" ? (
      <button
        className="relative flex cursor-default select-none items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none transition-colors hover:bg-accent hover:text-accent-foreground w-full"
        type="button"
      >
        <HelpCircle className="size-4" /> Contact support
      </button>
    ) : (
      <Button size="sm" variant="outline" className={className}>
        <HelpCircle /> Contact support
      </Button>
    );

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64">
          <DropdownMenuLabel>How can we help?</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem asChild>
            <a
              href={`mailto:${SUPPORT_EMAIL}`}
              className="flex items-center gap-2"
            >
              <Mail className="size-4" />
              <div className="flex-1">
                <p className="text-sm">Email us</p>
                <p className="text-[11px] text-muted-foreground">
                  {SUPPORT_EMAIL}
                </p>
              </div>
            </a>
          </DropdownMenuItem>
          <DropdownMenuItem asChild>
            <a
              href={`https://wa.me/${SUPPORT_WHATSAPP}?text=${encodeURIComponent(
                "Hi Anirudh Homes, I need help with…",
              )}`}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-2"
            >
              <MessageSquare className="size-4" />
              <div className="flex-1">
                <p className="text-sm">WhatsApp</p>
                <p className="text-[11px] text-muted-foreground">
                  {SUPPORT_WHATSAPP_DISPLAY}
                </p>
              </div>
            </a>
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onSelect={(e) => {
              e.preventDefault();
              setFormOpen(true);
            }}
          >
            <Send className="size-4" />
            <div className="flex-1">
              <p className="text-sm">Open a ticket</p>
              <p className="text-[11px] text-muted-foreground">
                We respond within 24 hours
              </p>
            </div>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <SupportTicketDialog open={formOpen} onOpenChange={setFormOpen} />
    </>
  );
}

function SupportTicketDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
}) {
  const { authUserId, userName, role } = useAuthStore();
  const location = useLocation();

  const submitM = useMutation({
    mutationFn: supportTicketsApi.create,
    onSuccess: () => {
      onOpenChange(false);
      toast({
        title: "Ticket submitted",
        description: "Our team will respond by email within 24 hours.",
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't submit",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Open a support ticket</DialogTitle>
          <DialogDescription>
            Tell us what's not working. We log this against your account so
            our team can dig in faster.
          </DialogDescription>
        </DialogHeader>
        <form
          id="support-form"
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (!authUserId) {
              toast({
                variant: "destructive",
                title: "Sign in required",
                description: "Please log in to submit a ticket.",
              });
              return;
            }
            const fd = new FormData(e.currentTarget);
            submitM.mutate({
              userId: authUserId,
              userName: userName ?? undefined,
              userRole: role ?? undefined,
              subject: String(fd.get("subject") ?? "").trim(),
              message: String(fd.get("message") ?? "").trim(),
              contextUrl: location.pathname,
            });
          }}
        >
          <div>
            <Label htmlFor="subject">Subject</Label>
            <Input
              id="subject"
              name="subject"
              required
              maxLength={200}
              placeholder="e.g. Can't download my lease deed"
              className="mt-1.5"
            />
          </div>
          <div>
            <Label htmlFor="message">What happened?</Label>
            <Textarea
              id="message"
              name="message"
              required
              rows={5}
              maxLength={4000}
              placeholder="Please include the steps you tried and any error you saw."
              className="mt-1.5"
            />
          </div>
          <p className="text-[11px] text-muted-foreground">
            We auto-include the page you were on ({location.pathname}) so
            our team can reproduce the issue faster.
          </p>
        </form>
        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={submitM.isPending}
          >
            Cancel
          </Button>
          <Button
            form="support-form"
            type="submit"
            variant="gradient"
            disabled={submitM.isPending}
          >
            {submitM.isPending && <Loader2 className="size-4 animate-spin" />}
            Submit ticket
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
