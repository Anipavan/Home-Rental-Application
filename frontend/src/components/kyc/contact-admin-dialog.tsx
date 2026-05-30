import { Phone, Mail } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

/**
 * Friendly escalation popup shown to users when KYC verification fails
 * because OUR vendor account hit a billing/quota limit — i.e. the
 * problem is NOT the user's data, it's a temporary supply-side gap on
 * our end. Surfaces a phone-call CTA so users know exactly how to
 * complete verification while we top up the vendor.
 *
 * <p>Fires when the backend returns {@code failureCode === "VENDOR_UNAVAILABLE"}
 * (set in KycServiceImpl when SandboxKycProvider records a BILLING_ALERT).
 * The same admin dashboard shows the same incident on the operator's
 * side so they can act on it without waiting for a phone call.
 *
 * <p>Phone number is the verified support line published on the
 * registration form and the public footer (single source of truth via
 * the constant below — when this changes, change it here).
 */
const SUPPORT_PHONE_DISPLAY = "+91 91082 01223";
const SUPPORT_PHONE_TEL = "+919108201223";
const SUPPORT_EMAIL = "support@anirudhhomes.in";

export function ContactAdminForKycDialog({
  open,
  onOpenChange,
  reason,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  /** Server-supplied human-readable reason; rendered as supporting copy. */
  reason?: string;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="font-display text-xl">
            We can't verify your identity right now
          </DialogTitle>
          <DialogDescription className="text-sm leading-relaxed">
            {reason && reason.trim().length > 0
              ? reason
              : "Our verification service is temporarily paused — this isn't a problem with your details."}{" "}
            Please contact our team and we'll complete the check manually
            for you.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 mt-2">
          <a
            href={`tel:${SUPPORT_PHONE_TEL}`}
            className="flex items-center gap-3 p-4 rounded-xl border bg-primary/5 hover:bg-primary/10 transition-colors"
          >
            <div className="size-10 rounded-lg gradient-brand grid place-items-center shrink-0">
              <Phone className="size-5 text-white" />
            </div>
            <div className="flex-1">
              <p className="text-xs uppercase tracking-wider text-muted-foreground font-semibold">
                Call our team
              </p>
              <p className="font-display font-semibold text-base">
                {SUPPORT_PHONE_DISPLAY}
              </p>
              <p className="text-xs text-muted-foreground">
                Open 9am – 9pm IST · Quickest path to verification
              </p>
            </div>
          </a>

          <a
            href={`mailto:${SUPPORT_EMAIL}?subject=Help%20completing%20KYC%20verification`}
            className="flex items-center gap-3 p-4 rounded-xl border bg-card hover:bg-secondary/30 transition-colors"
          >
            <div className="size-10 rounded-lg bg-foreground/5 text-foreground grid place-items-center shrink-0">
              <Mail className="size-5" />
            </div>
            <div className="flex-1">
              <p className="text-xs uppercase tracking-wider text-muted-foreground font-semibold">
                Or email us
              </p>
              <p className="font-medium text-sm">{SUPPORT_EMAIL}</p>
              <p className="text-xs text-muted-foreground">
                We reply within a few hours
              </p>
            </div>
          </a>

          <p className="text-xs text-muted-foreground leading-relaxed pt-1">
            Your PAN and personal details are stored encrypted and only
            shared with the verification provider when you ask us to.
            Nothing's been sent yet for this attempt.
          </p>
        </div>

        <DialogFooter className="mt-4">
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
