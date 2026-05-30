import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Check, ChevronRight, Sparkles, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  useOnboardingProgress,
  type OnboardingStep,
} from "@/hooks/use-onboarding-progress";
import { cn } from "@/lib/utils";

/* ───────────────────────── Modal (first-login) ───────────────────────── */

/**
 * First-login onboarding modal. Shown ONCE per (user × browser) — once
 * the user dismisses (Skip or Continue setup), we set a localStorage
 * flag and never auto-open the modal again. The persistent banner
 * keeps surfacing progress on every page until 4/4 done.
 *
 * <p>Renders nothing when:
 *   • progress query is still loading (avoid flashing the wrong steps)
 *   • the user is an admin (no onboarding flow for that role)
 *   • the user already dismissed the modal once
 *   • the user has finished all steps
 */
export function OnboardingWizard() {
  const progress = useOnboardingProgress();
  const [open, setOpen] = useState(false);

  // Auto-open once when the progress finishes loading, IF the user
  // hasn't dismissed before AND there's something to do. Effect runs
  // every render but the gate inside means setOpen(true) fires at
  // most once per mount.
  useEffect(() => {
    if (progress.isLoading) return;
    if (progress.modalDismissed) return;
    if (progress.allComplete) return;
    if (progress.totalCount === 0) return;
    setOpen(true);
  }, [
    progress.isLoading,
    progress.modalDismissed,
    progress.allComplete,
    progress.totalCount,
  ]);

  const handleDismiss = (markPermanent: boolean) => {
    setOpen(false);
    if (markPermanent) {
      progress.dismissModal();
    }
  };

  if (progress.totalCount === 0) return null;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        // Closing via backdrop or X = "permanent" — same as Skip.
        if (!v) handleDismiss(true);
        else setOpen(true);
      }}
    >
      <DialogContent className="max-w-lg p-0 overflow-hidden">
        {/* Gradient hero strip — reinforces the brand surface from
            the marketing pages so onboarding feels intentional, not
            like a generic system dialog. */}
        <div className="gradient-brand text-white p-6 sm:p-7 relative">
          <div className="flex items-start gap-3">
            <div className="size-10 rounded-xl bg-white/20 grid place-items-center shrink-0">
              <Sparkles className="size-5" />
            </div>
            <div className="flex-1">
              <DialogHeader className="text-left space-y-1">
                <DialogTitle className="text-white font-display text-xl">
                  Welcome to Anirudh Homes
                </DialogTitle>
                <DialogDescription className="text-white/85 text-sm">
                  A short checklist to get you fully set up. You can do
                  these in any order, in your own time.
                </DialogDescription>
              </DialogHeader>
            </div>
          </div>
          {/* Progress ring — text rather than a circle, easier to scan */}
          <div className="mt-5 text-white/90 text-sm font-medium">
            <span className="font-display font-bold text-lg">
              {progress.completeCount}
            </span>
            <span className="mx-1">/</span>
            <span>{progress.totalCount} done</span>
          </div>
        </div>

        {/* Step list */}
        <div className="p-5 sm:p-6 space-y-3">
          {progress.steps.map((step, i) => (
            <StepRow
              key={step.id}
              step={step}
              index={i}
              onNavigate={() => handleDismiss(true)}
            />
          ))}
        </div>

        {/* Footer */}
        <div className="border-t border-border/60 p-4 flex flex-wrap items-center justify-between gap-3 bg-secondary/30">
          <p className="text-xs text-muted-foreground">
            Progress saves automatically. You'll see this checklist on
            your dashboard until you're done.
          </p>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleDismiss(true)}
            className="gap-1.5"
          >
            <X className="size-3.5" />
            Skip for now
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

/* ───────────────────────── Banner (persistent) ───────────────────────── */

/**
 * Persistent "Continue setup" banner shown above the page content on
 * every authenticated route while the user has incomplete onboarding
 * steps. Auto-hides on:
 *   • all-complete
 *   • zero steps (admin)
 *   • the onboarding modal being currently open (avoid double-stacking)
 *
 * <p>Clicking the banner re-opens the wizard modal — the user can
 * use the banner as the "one button to go pick the next thing up"
 * affordance instead of remembering which sub-page does what.
 */
export function OnboardingBanner() {
  const progress = useOnboardingProgress();
  const location = useLocation();
  const navigate = useNavigate();
  const [reopened, setReopened] = useState(false);

  // Hide the banner on the same route as the next-step CTA — avoids
  // a banner that says "Continue: Add bank details" while the user
  // is already ON the bank-details page.
  const nextStep = progress.steps.find((s) => !s.complete);
  const onTargetRoute =
    nextStep != null && location.pathname.startsWith(nextStep.href);

  if (progress.totalCount === 0) return null;
  if (progress.allComplete) return null;
  if (progress.isLoading) return null;
  if (onTargetRoute) return null;
  if (!nextStep) return null;

  return (
    <>
      <Card
        className="mb-5 p-4 sm:p-5 gradient-brand-soft border-primary/20 flex flex-wrap items-center gap-4 cursor-pointer hover:border-primary/40 transition-colors"
        onClick={() => setReopened(true)}
      >
        <div className="size-10 rounded-xl bg-primary/15 text-primary grid place-items-center shrink-0">
          <Sparkles className="size-5" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs uppercase tracking-wider text-primary font-semibold">
            Finish setting up · {progress.completeCount} of{" "}
            {progress.totalCount} done
          </p>
          <p className="font-display font-semibold mt-0.5 truncate">
            Next: {nextStep.label}
          </p>
          <p className="text-xs text-muted-foreground truncate">
            {nextStep.description}
          </p>
        </div>
        <Button
          variant="gradient"
          size="sm"
          onClick={(e) => {
            // Stop click bubbling to the Card so we don't both navigate
            // AND open the modal. The button is the direct CTA.
            e.stopPropagation();
            navigate(nextStep.href);
          }}
        >
          Continue
          <ChevronRight className="size-3.5" />
        </Button>
      </Card>

      {/* Same Wizard modal but in "user-asked-for-it" mode — opens
          when the banner card is clicked. Independent open state so it
          doesn't conflict with the first-login auto-open. */}
      <Dialog open={reopened} onOpenChange={setReopened}>
        <DialogContent className="max-w-lg p-0 overflow-hidden">
          <div className="gradient-brand text-white p-6 sm:p-7">
            <DialogHeader className="text-left">
              <DialogTitle className="text-white font-display text-xl">
                Your setup checklist
              </DialogTitle>
              <DialogDescription className="text-white/85 text-sm">
                {progress.completeCount} of {progress.totalCount} steps
                complete. Pick whichever feels easiest right now.
              </DialogDescription>
            </DialogHeader>
          </div>
          <div className="p-5 sm:p-6 space-y-3">
            {progress.steps.map((step, i) => (
              <StepRow
                key={step.id}
                step={step}
                index={i}
                onNavigate={() => setReopened(false)}
              />
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

/* ───────────────────────── Shared row ───────────────────────── */

/**
 * One step row inside the wizard modal. Renders the icon chip, label,
 * description, and a check-or-arrow status indicator. Wrapping it in
 * Link rather than Button means right-click → open in new tab works,
 * which power users will reach for.
 */
function StepRow({
  step,
  index,
  onNavigate,
}: {
  step: OnboardingStep;
  index: number;
  onNavigate: () => void;
}) {
  const Icon = step.icon;
  return (
    <Link
      to={step.href}
      onClick={onNavigate}
      className={cn(
        "flex items-center gap-4 p-3.5 rounded-xl border transition-colors",
        step.complete
          ? "border-success/30 bg-success/5 hover:bg-success/10"
          : "border-border bg-card hover:border-primary/30 hover:bg-secondary/30",
      )}
    >
      {/* Icon chip */}
      <div
        className={cn(
          "size-10 rounded-xl grid place-items-center shrink-0",
          step.complete
            ? "bg-success/15 text-success"
            : "bg-primary/10 text-primary",
        )}
      >
        <Icon className="size-5" />
      </div>

      {/* Label + description */}
      <div className="flex-1 min-w-0">
        <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
          Step {index + 1}
        </p>
        <p className="font-display font-semibold text-sm truncate">
          {step.label}
        </p>
        <p className="text-xs text-muted-foreground line-clamp-2">
          {step.description}
        </p>
      </div>

      {/* Status indicator: check when complete, chevron when not */}
      <div
        className={cn(
          "size-7 rounded-full grid place-items-center shrink-0",
          step.complete
            ? "bg-success text-white"
            : "bg-secondary text-foreground",
        )}
      >
        {step.complete ? (
          <Check className="size-3.5" />
        ) : (
          <ChevronRight className="size-3.5" />
        )}
      </div>
    </Link>
  );
}
