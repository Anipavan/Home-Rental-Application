import { useQuery } from "@tanstack/react-query";
import { Star } from "lucide-react";
import { reviewsApi } from "@/lib/api/reviews";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import type { ReviewTargetType } from "@/types/api";

interface Props {
  targetType: ReviewTargetType;
  targetId: string;
  pageSize?: number;
}

/**
 * Reusable list-view for reviews about a property / owner / tenant.
 * Renders the rating summary up top + paginated reviews below.
 */
export function ReviewList({ targetType, targetId, pageSize = 10 }: Props) {
  const summaryQ = useQuery({
    queryKey: ["review-summary", targetType, targetId],
    queryFn: () => reviewsApi.summary(targetType, targetId),
    enabled: !!targetId,
    retry: false,
  });

  const listQ = useQuery({
    queryKey: ["reviews", targetType, targetId, 0, pageSize],
    queryFn: () => {
      const fetcher =
        targetType === "PROPERTY"
          ? reviewsApi.byProperty
          : targetType === "OWNER"
            ? reviewsApi.byOwner
            : reviewsApi.byTenant;
      return fetcher(targetId, 0, pageSize);
    },
    enabled: !!targetId,
  });

  if (summaryQ.isLoading || listQ.isLoading) {
    return <Skeleton className="h-40 rounded-2xl" />;
  }

  const summary = summaryQ.data;
  const items = listQ.data?.content ?? [];

  return (
    <div className="space-y-5">
      {summary && (
        <div className="rounded-xl border bg-secondary/30 p-5 flex items-start gap-5 flex-wrap">
          <div className="text-center">
            <p className="font-display text-4xl font-bold">
              {summary.averageRating.toFixed(1)}
            </p>
            <Stars rating={Math.round(summary.averageRating)} />
            <p className="text-xs text-muted-foreground mt-1">
              {summary.totalReviews} review{summary.totalReviews === 1 ? "" : "s"}
            </p>
          </div>
          <div className="flex-1 min-w-[220px]">
            {[5, 4, 3, 2, 1].map((star) => {
              const count = Number(summary.ratingHistogram[String(star)] ?? 0);
              const pct = summary.totalReviews
                ? (count / summary.totalReviews) * 100
                : 0;
              return (
                <div key={star} className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-medium w-3">{star}</span>
                  <div className="flex-1 h-1.5 rounded-full bg-secondary overflow-hidden">
                    <div
                      className="h-full bg-amber-400"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <span className="text-xs text-muted-foreground w-7 text-right">
                    {count}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No reviews yet.</p>
      ) : (
        <div className="space-y-3">
          {items.map((r) => (
            <div key={r.id} className="rounded-xl border bg-background p-4">
              <div className="flex items-center gap-2 flex-wrap">
                <Stars rating={r.rating} />
                {r.title && (
                  <p className="font-medium text-sm">{r.title}</p>
                )}
                {r.isVerified && (
                  <Badge variant="success" className="text-[10px]">Verified</Badge>
                )}
              </div>
              {r.body && (
                <p className="text-sm text-muted-foreground mt-2 whitespace-pre-wrap">
                  {r.body}
                </p>
              )}
              {r.tags && r.tags.length > 0 && (
                <div className="flex gap-1.5 flex-wrap mt-2">
                  {r.tags.map((t) => (
                    <Badge key={t} variant="secondary" className="text-[10px]">
                      #{t}
                    </Badge>
                  ))}
                </div>
              )}
              {r.createdAt && (
                <p className="text-[11px] text-muted-foreground mt-2">
                  {new Date(r.createdAt).toLocaleDateString()}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function Stars({ rating }: { rating: number }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((s) => (
        <Star
          key={s}
          className={
            s <= rating
              ? "size-4 text-amber-500 fill-amber-500"
              : "size-4 text-muted-foreground/30"
          }
        />
      ))}
    </div>
  );
}
