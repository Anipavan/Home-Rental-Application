import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Hourglass, Sparkles } from "lucide-react";
import { maintainerPaymentApi } from "@/lib/api/maintainer-payment";
import { useAuthStore } from "@/stores/auth-store";
import { cn } from "@/lib/utils";
import { PaymentPromptModal } from "./payment-prompt-modal";

/**
 * Wraps the entire maintainer shell with the soft payment gate. Polls
 * {@code /auth/me/payment-status} on mount + every 5 minutes; renders
 * the matching banner / modal based on the returned state. PAID
 * (default for every user when the admin toggle is OFF, plus
 * grandfathered + truly-paid accounts) passes through unchanged.
 *
 * <p>The wrapper is rendered around the entire AppShell, not just the
 * dashboard inner content, so the banner + modal show on every
 * maintainer route. Users in FORCED state can still log out via the
 * modal's Log out button, which clears the auth store and routes
 * back to /login.
 */
export function MaintainerPaymentGate({
  children,
}: {
  children: React.ReactNode;
}) {
  const qc = useQueryClient();
  const role = useAuthStore((s) => s.role);
  const userName = useAuthStore((s) => s.userName) ?? "Maintainer";

  // The gate only applies to MAINTAINER role. OWNERs who self-assigned
  // as the maintainer for one of their own buildings (router whitelists
  // them onto /maintainer/**) shouldn't be paywalled — they're owners.
  const enabled = role === "MAINTAINER";

  const statusQ = useQuery({
    queryKey: ["auth", "me", "payment-status"],
    queryFn: () => maintainerPaymentApi.getStatus(),
    enabled,
    // Auto-refresh every 5 minutes — long enough not to be chatty,
    // short enough that an admin toggle flip + window leave-return
    // shows the right state without the user having to refresh.
    refetchInterval: 5 * 60 * 1000,
    refetchOnWindowFocus: true,
    retry: false,
  });

  // Non-MAINTAINER users — OWNERs or any future role — never see the
  // gate. Same when the status query is still loading; we don't want
  // to flash a modal before the data lands.
  if (!enabled || !statusQ.data) {
    return <>{children}</>;
  }

  const { status, trialDaysLeft, skipsLeft, nextPromptAt, amountInr } = statusQ.data;

  const refetchStatus = () =>
    qc.invalidateQueries({ queryKey: ["auth", "me", "payment-status"] });

  // PAID — render plain.
  if (status === "PAID") {
    return <>{children}</>;
  }

  return (
    <>
      {/* Banner — surface trial countdown / skip-grace status above
          the maintainer chrome. Never blocking. */}
      {status === "TRIAL" && trialDaysLeft != null && (
        <GateBanner tone="teal" icon={Sparkles}>
          <span>
            Free trial — <b>{trialDaysLeft}</b>{" "}
            {trialDaysLeft === 1 ? "day" : "days"} left. Activate any time
            to remove this banner.
          </span>
        </GateBanner>
      )}
      {status === "SKIP_GRACE" && skipsLeft != null && nextPromptAt && (
        <GateBanner tone="amber" icon={Hourglass}>
          <span>
            Activation pending — we'll prompt you again on{" "}
            <b>{new Date(nextPromptAt).toLocaleDateString()}</b>.{" "}
            {skipsLeft} of 2 skips left.
          </span>
        </GateBanner>
      )}

      {children}

      {(status === "PROMPT" || status === "FORCED") && (
        <PaymentPromptModal
          status={status}
          skipsLeft={skipsLeft ?? 0}
          amountInr={amountInr ?? 999}
          userName={userName}
          onSkipped={refetchStatus}
        />
      )}
    </>
  );
}

/** Thin coloured strip above the AppShell content. */
function GateBanner({
  tone,
  icon: Icon,
  children,
}: {
  tone: "teal" | "amber";
  icon: React.ComponentType<{ className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "w-full text-xs sm:text-sm px-4 py-2 flex items-center gap-2 justify-center",
        tone === "teal"
          ? "bg-primary/10 text-primary"
          : "bg-amber-500/10 text-amber-700 dark:text-amber-300",
      )}
    >
      <Icon className="size-3.5 shrink-0" />
      <div>{children}</div>
    </div>
  );
}
