import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Star, CheckCircle2, XCircle, Flag } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { reviewsApi } from "@/lib/api/reviews";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { ReviewResponse } from "@/types/api";

export function AdminReviewsPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();

  const pendingQ = useQuery({
    queryKey: ["pending-reviews"],
    queryFn: () => reviewsApi.pendingModeration(0, 50),
  });

  const moderateM = useMutation({
    mutationFn: (b: {
      id: string;
      decision: "APPROVED" | "REJECTED" | "FLAGGED";
      reason?: string;
    }) =>
      reviewsApi.moderate(b.id, {
        decision: b.decision,
        moderatorId: authUserId ?? "admin",
        reason: b.reason,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pending-reviews"] });
      toast({ title: "Review moderated" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't moderate",
        description: extractErrorMessage(e),
      }),
  });

  const items = pendingQ.data?.content ?? [];

  return (
    <div className="animate-fade-in max-w-4xl">
      <PageHeader
        title="Review moderation"
        description="Approve, reject, or flag user-submitted reviews."
      />

      <Card>
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg">
            Pending queue ({items.length})
          </h3>

          {pendingQ.isLoading ? (
            <div className="mt-4 space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-28 rounded-xl" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <p className="mt-6 text-sm text-muted-foreground">
              Nothing pending — you're all caught up.
            </p>
          ) : (
            <div className="mt-4 space-y-3">
              {items.map((r) => (
                <ModerateRow
                  key={r.id}
                  review={r}
                  onDecide={(decision, reason) =>
                    moderateM.mutate({ id: r.id, decision, reason })
                  }
                  busy={moderateM.isPending && moderateM.variables?.id === r.id}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function ModerateRow({
  review,
  onDecide,
  busy,
}: {
  review: ReviewResponse;
  onDecide: (
    decision: "APPROVED" | "REJECTED" | "FLAGGED",
    reason?: string,
  ) => void;
  busy: boolean;
}) {
  return (
    <div className="rounded-xl border bg-secondary/30 p-4">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="flex items-center gap-0.5">
              {[1, 2, 3, 4, 5].map((s) => (
                <Star
                  key={s}
                  className={
                    s <= review.rating
                      ? "size-3.5 text-amber-500 fill-amber-500"
                      : "size-3.5 text-muted-foreground/30"
                  }
                />
              ))}
            </div>
            <Badge variant="secondary" className="text-[10px]">
              {review.reviewerType} → {review.targetType}
            </Badge>
            {review.title && <p className="font-medium text-sm">{review.title}</p>}
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">
            reviewer <code>{review.reviewerId}</code> · target{" "}
            <code>{review.targetId}</code>
          </p>
          {review.body && (
            <p className="text-sm text-muted-foreground mt-2 whitespace-pre-wrap">
              {review.body}
            </p>
          )}
          {review.tags && review.tags.length > 0 && (
            <div className="flex gap-1.5 flex-wrap mt-2">
              {review.tags.map((t) => (
                <Badge key={t} variant="secondary" className="text-[10px]">
                  #{t}
                </Badge>
              ))}
            </div>
          )}
        </div>
        <div className="flex flex-col gap-2 shrink-0">
          <Button
            size="sm"
            variant="gradient"
            onClick={() => onDecide("APPROVED")}
            disabled={busy}
          >
            <CheckCircle2 /> Approve
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              const reason = prompt("Reason for rejecting (optional)") ?? undefined;
              onDecide("REJECTED", reason);
            }}
            disabled={busy}
          >
            <XCircle /> Reject
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => onDecide("FLAGGED")}
            disabled={busy}
          >
            <Flag /> Flag
          </Button>
        </div>
      </div>
    </div>
  );
}
