import { useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  CheckCircle2,
  Clock,
  Hourglass,
  XCircle,
} from "lucide-react";
import { claimsApi } from "@/lib/api/claims";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { useToast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import { formatDate } from "@/lib/utils";
import type { MembershipClaim, MembershipClaimStatus } from "@/types/api";

/**
 * Post-signup landing page for users who registered via the SOCIETY
 * path on the signup form. Shows the status of their pending claim
 * (or claims, if they submitted more than one) with a clear next-step
 * for each terminal state:
 *
 *  * PENDING — waiting on owner. Show "we'll let you know" + a
 *    Withdraw button so they can cancel if they registered against
 *    the wrong building.
 *  * APPROVED — done. Tell them to log out + back in to pick up the
 *    new role (necessary because the JWT they're holding still
 *    carries the old role).
 *  * REJECTED — show the owner's note (if any). They can resubmit
 *    via /register if they want to try again.
 *  * WITHDRAWN — they cancelled. Same resubmit path.
 *
 * Auto-refetches every 30s so the screen updates without manual
 * refresh once the owner approves.
 */
export function PendingClaimPage() {
  const myClaimsQ = useQuery({
    queryKey: ["my-claims"],
    queryFn: () => claimsApi.mine(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  // Surface the most-recent claim first. Decided claims are still
  // shown — the user wants to know their request was rejected, and
  // an APPROVED claim is the "you're set" panel.
  const ordered = useMemo<MembershipClaim[]>(() => {
    return [...(myClaimsQ.data ?? [])].sort((a, b) => {
      const ts = (s: string) => new Date(s).getTime();
      return ts(b.createdAt) - ts(a.createdAt);
    });
  }, [myClaimsQ.data]);

  if (myClaimsQ.isLoading) {
    return (
      <div className="animate-fade-in max-w-3xl">
        <Skeleton className="h-40 rounded-2xl" />
      </div>
    );
  }

  if (ordered.length === 0) {
    return (
      <div className="animate-fade-in max-w-3xl">
        <EmptyState
          variant="info"
          icon={Building2}
          title="No society claims yet"
          description="You can submit a new claim from your profile page."
          action={
            <Button asChild variant="gradient">
              <Link to="/app/profile">Go to profile</Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="animate-fade-in max-w-3xl space-y-4">
      <div>
        <h1 className="font-display text-2xl font-bold">
          Your society membership
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Status of your applications to join a society as a maintainer
          or resident.
        </p>
      </div>
      {ordered.map((c) => (
        <ClaimCard key={c.id} claim={c} />
      ))}
    </div>
  );
}

/** Where to route a user who wants to resubmit a rejected/withdrawn
 *  claim. They're already logged in, so we skip /register and drop
 *  them straight into the right claim-submission page for the role
 *  they were applying for. */
function resubmitPathFor(claim: MembershipClaim): string {
  if (claim.requestedRole === "MAINTAINER") return "/setup-society";
  // RESIDENT / FLAT_OWNER — /welcome has the "I'm a maintainee" card
  // that opens the building-search flow.
  return "/welcome";
}

const STATUS_META: Record<
  MembershipClaimStatus,
  { label: string; icon: typeof Clock; tone: string; bg: string }
> = {
  PENDING: {
    label: "Waiting for owner",
    icon: Hourglass,
    tone: "text-warning",
    bg: "bg-warning/10 border-warning/30",
  },
  APPROVED: {
    label: "Approved",
    icon: CheckCircle2,
    tone: "text-success",
    bg: "bg-success/10 border-success/30",
  },
  REJECTED: {
    label: "Rejected",
    icon: XCircle,
    tone: "text-destructive",
    bg: "bg-destructive/10 border-destructive/30",
  },
  WITHDRAWN: {
    label: "Withdrawn",
    icon: Clock,
    tone: "text-muted-foreground",
    bg: "bg-secondary/40 border-border",
  },
};

function ClaimCard({ claim }: { claim: MembershipClaim }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { toast: tst } = useToast();
  const meta = STATUS_META[claim.status];
  const Icon = meta.icon;

  const withdrawMut = useMutation({
    mutationFn: () => claimsApi.withdraw(claim.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-claims"] });
      tst({ title: "Request withdrawn." });
    },
    onError: (e) =>
      tst({
        title: "Couldn't withdraw",
        description: extractErrorMessage(e),
        variant: "destructive",
      }),
  });

  return (
    <Card className={meta.bg + " border"}>
      <CardContent className="p-5">
        <div className="flex items-start gap-3">
          <div className={`mt-0.5 ${meta.tone}`}>
            <Icon className="size-5" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <Badge variant="secondary" className="text-[10px]">
                {claim.requestedRole === "MAINTAINER"
                  ? "Maintainer"
                  : "Resident"}
              </Badge>
              <span className={`text-xs font-semibold uppercase tracking-wider ${meta.tone}`}>
                {meta.label}
              </span>
            </div>
            <p className="font-semibold mt-1">
              {claim.buildingName ?? "Building"}
              {claim.claimedFlatNumber && (
                <span className="text-sm text-muted-foreground font-normal">
                  {" "}
                  · flat {claim.claimedFlatNumber}
                </span>
              )}
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              Submitted {formatDate(claim.createdAt)}
              {claim.decidedAt && ` · decided ${formatDate(claim.decidedAt)}`}
            </p>

            {/* State-specific guidance */}
            {claim.status === "PENDING" && (
              <p className="text-sm mt-3">
                Your building owner will see this request in their
                dashboard. As soon as they approve, you'll get access.
                We auto-refresh every 30 seconds so just leave this tab
                open.
              </p>
            )}
            {claim.status === "APPROVED" && (
              <div className="mt-3 space-y-2">
                <p className="text-sm">
                  You're in.{" "}
                  {claim.requestedRole === "MAINTAINER"
                    ? "Sign out and back in to pick up the maintainer dashboard — your current session still has the old role."
                    : "You're attached to your flat. Head to the society page to see the books."}
                </p>
                <div className="flex gap-2">
                  {claim.requestedRole === "MAINTAINER" ? (
                    <Button
                      variant="gradient"
                      size="sm"
                      onClick={() => navigate("/login")}
                    >
                      Sign in again
                    </Button>
                  ) : (
                    <Button asChild variant="gradient" size="sm">
                      <Link to="/app">Open my dashboard</Link>
                    </Button>
                  )}
                </div>
              </div>
            )}
            {claim.status === "REJECTED" && (
              <div className="mt-3 space-y-2">
                {claim.decisionNote && (
                  <p className="text-sm italic">
                    Note from the owner: &ldquo;{claim.decisionNote}&rdquo;
                  </p>
                )}
                <p className="text-sm">
                  You can submit a new request — make sure you picked
                  the right building.
                </p>
                <Button asChild variant="outline" size="sm">
                  <Link to={resubmitPathFor(claim)}>Try again</Link>
                </Button>
              </div>
            )}
            {claim.status === "WITHDRAWN" && (
              <div className="mt-3 space-y-2">
                <p className="text-sm text-muted-foreground">
                  You cancelled this request. Change your mind? Submit
                  a fresh one — no need to re-register.
                </p>
                <Button asChild variant="outline" size="sm">
                  <Link to={resubmitPathFor(claim)}>Try again</Link>
                </Button>
              </div>
            )}
          </div>

          {claim.status === "PENDING" && (
            <Button
              variant="ghost"
              size="sm"
              disabled={withdrawMut.isPending}
              onClick={() => {
                if (confirm("Withdraw this request?")) withdrawMut.mutate();
              }}
            >
              Withdraw
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
