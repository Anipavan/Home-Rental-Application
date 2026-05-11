import { Outlet } from "react-router-dom";
import { Construction } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

/**
 * Generic route-level "feature temporarily disabled" gate.
 *
 * <p>Mirrors {@link FlatRequiredOutlet} in shape — the underlying page
 * still renders (so the URL bar reflects what the user clicked and
 * lifting the kill-switch on the next deploy makes the page work
 * without a code change) but is ghosted out and non-interactive,
 * with a centered card explaining why.
 *
 * <p>Used today to put the KYC feature under maintenance across every
 * role/scenario. Reusable for any future "we're temporarily turning
 * this off" situation — just drop the route under
 * {@code <FeatureDisabledOutlet feature="X" />}.
 */
interface Props {
  /** Short label, e.g. "KYC". Shown in the headline. */
  feature: string;
  /** One-liner shown beneath the headline. Optional. */
  reason?: string;
}

export function FeatureDisabledOutlet({ feature, reason }: Props) {
  return (
    <div className="relative min-h-[60vh]">
      {/* The page itself, ghosted out and inert. aria-hidden so screen
          readers skip the dead content and jump straight to the
          message panel below. */}
      <div
        className="opacity-40 pointer-events-none select-none blur-[1px]"
        aria-hidden="true"
      >
        <Outlet />
      </div>

      {/* Overlay panel — semi-transparent backdrop + a centered card. */}
      <div className="absolute inset-0 flex items-center justify-center bg-background/60 backdrop-blur-sm">
        <Card className="max-w-md w-[min(28rem,90%)] border-dashed">
          <CardContent className="p-8 text-center">
            <div className="size-14 rounded-full bg-warning/15 grid place-items-center mx-auto">
              <Construction className="size-6 text-warning" />
            </div>
            <h2 className="font-display text-xl font-semibold mt-4">
              {feature} is temporarily unavailable
            </h2>
            <p className="text-muted-foreground mt-2">
              {reason ??
                "We've paused this feature for maintenance. It will be back online shortly — no action needed from you."}
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
