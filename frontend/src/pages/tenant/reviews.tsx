import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Star, Pencil, Building2, User, ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ReviewResponse } from "@/types/api";
import { useAuthStore } from "@/stores/auth-store";
import { reviewsApi } from "@/lib/api/reviews";
import { propertiesApi } from "@/lib/api/properties";
import { usersApi } from "@/lib/api/users";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import { PageHeader } from "@/components/layout/page-header";
import { UnifiedStayReviewForm } from "@/components/reviews/unified-stay-review-form";

export function TenantReviewsPage() {
  const { authUserId } = useAuthStore();
  /**
   * Which flat the tenant is currently writing a review for. Keyed by
   * the flat id (unique per row, even when one tenant occupies two
   * flats in the same building — keying on building/owner id collided
   * in that edge case previously).
   *
   * Replaces the older two-mode {flatId, targetId, type: "PROPERTY"|"OWNER"}
   * state. The unified form now takes BOTH the propertyTargetId (building)
   * and ownerTargetId (owner authUserId), so we only need to remember
   * the flat being reviewed.
   */
  const [writingFlatId, setWritingFlatId] = useState<string | null>(null);

  const meQ = useQuery({
    queryKey: ["me", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId,
  });
  const userId = meQ.data ? String(meQ.data.id) : undefined;

  const myFlatsQ = useQuery({
    queryKey: ["my-flats", authUserId],
    queryFn: () => propertiesApi.flats.byTenant(authUserId!),
    enabled: !!authUserId,
  });

  const myReviewsQ = useQuery({
    queryKey: ["my-reviews", userId],
    queryFn: () => reviewsApi.byReviewer(userId!, 0, 20),
    enabled: !!userId,
  });

  const myReviews = myReviewsQ.data?.content ?? [];

  /**
   * Group the raw rows by "submission" — the unified form fires two
   * parallel POSTs (PROPERTY + OWNER) that share the same title +
   * body and land within a few hundred milliseconds of each other.
   * Showing both as separate cards reads like the user wrote two
   * different reviews; the actual mental model is one review with
   * two ratings.
   *
   * Grouping key: reviewerId + title + body + createdAt rounded to
   * the same minute. This pairs the PROPERTY and OWNER rows from a
   * single submission without coupling the frontend to any
   * server-side "submission id" field.
   *
   * If a row has no partner (e.g. one of the two parallel POSTs
   * failed and was silently dropped by Promise.allSettled), the
   * group still renders — just shows one rating instead of two.
   */
  const groupedReviews = useMemo(
    () => groupBySubmission(myReviews),
    [myReviews],
  );

  return (
    <div className="animate-fade-in max-w-4xl">
      <PageHeader
        title="Reviews"
        description="Share your experience and help future tenants."
      />

      <Tabs defaultValue="write">
        <TabsList>
          <TabsTrigger value="write">Write a review</TabsTrigger>
          <TabsTrigger value="mine">My reviews ({groupedReviews.length})</TabsTrigger>
        </TabsList>

        <TabsContent value="write" className="mt-4">
          <Card>
            <CardContent className="p-6 sm:p-8">
              {myFlatsQ.isLoading ? (
                <Skeleton className="h-32 rounded-xl" />
              ) : !myFlatsQ.data || myFlatsQ.data.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Once you're assigned to a flat you can leave a review.
                </p>
              ) : (
                <div className="space-y-4">
                  <p className="text-sm text-muted-foreground">
                    One combined review per stay — rate the property and
                    the owner separately, write a single comment that
                    appears under both.
                  </p>
                  {myFlatsQ.data.map((f) => {
                    // Owner id may not be on the flat row in every
                    // response — fall back to the buildingId so the
                    // owner-review row at least clusters by building
                    // until the API surfaces ownerId everywhere.
                    const ownerTargetId =
                      (f as { ownerId?: string }).ownerId ?? f.buildingId;
                    return (
                      <div
                        key={f.id}
                        className="rounded-xl border bg-secondary/30 p-4"
                      >
                        <div className="flex items-center justify-between flex-wrap gap-2">
                          <div>
                            <p className="font-medium">
                              {f.buildingName ?? "Flat"} · #{f.flatNumber}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {f.buildingAddress ?? ""}
                            </p>
                          </div>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() =>
                              setWritingFlatId(
                                writingFlatId === f.id ? null : f.id,
                              )
                            }
                          >
                            <Pencil />
                            {writingFlatId === f.id
                              ? "Close"
                              : "Write review"}
                          </Button>
                        </div>
                        {writingFlatId === f.id && (
                          <div className="mt-4">
                            <UnifiedStayReviewForm
                              reviewerId={userId ?? ""}
                              propertyTargetId={f.buildingId}
                              ownerTargetId={ownerTargetId}
                              propertyLabel={
                                f.buildingName
                                  ? `${f.buildingName} · #${f.flatNumber}`
                                  : `Flat ${f.flatNumber}`
                              }
                              onSubmitted={() => setWritingFlatId(null)}
                              onCancel={() => setWritingFlatId(null)}
                            />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="mine" className="mt-4">
          <Card>
            <CardContent className="p-6 sm:p-8">
              {myReviewsQ.isLoading ? (
                <div className="space-y-3">
                  {[1, 2].map((i) => (
                    <Skeleton key={i} className="h-20 rounded-xl" />
                  ))}
                </div>
              ) : groupedReviews.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  You haven't written a review yet.
                </p>
              ) : (
                <div className="space-y-3">
                  {/* One card per submission (PROPERTY + OWNER rows
                      grouped together). Card collapsed by default —
                      shows the average rating + body. Click to expand
                      and see the property + owner ratings split out
                      with their individual badges. */}
                  {groupedReviews.map((group) => (
                    <ReviewGroupCard key={group.key} group={group} />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

/* ─────────────────────────── grouped review helpers ─────────────────────────── */

interface ReviewGroup {
  /** Stable React key — first row's id is good enough since the
   *  backend never collapses a submission across reviewer ids. */
  key: string;
  /** Most recent createdAt across the rows in this group. */
  createdAt?: string;
  /** Shared title (both rows always have the same title in a unified submission). */
  title?: string;
  /** Shared body. */
  body?: string;
  /** The PROPERTY-targeted row, if present. */
  propertyRow?: ReviewResponse;
  /** The OWNER-targeted row, if present. */
  ownerRow?: ReviewResponse;
  /** Any other rows (e.g. TENANT reviews from owners — uncommon on tenant page). */
  otherRows: ReviewResponse[];
  /** Average rating across whichever rows exist — what the collapsed card shows. */
  averageRating: number;
}

/**
 * Group review rows by submission. The unified form fires two POSTs
 * within milliseconds; we pair them by reviewerId + title + body +
 * createdAt rounded to the nearest minute. Rows that don't pair
 * (legacy single-target reviews, or one POST that failed) become
 * single-row groups.
 */
function groupBySubmission(rows: ReviewResponse[]): ReviewGroup[] {
  const groups = new Map<string, ReviewGroup>();
  for (const r of rows) {
    const minuteBucket = r.createdAt
      ? r.createdAt.slice(0, 16) // yyyy-MM-ddTHH:mm — rounds to the minute
      : "no-date";
    const key = [
      r.reviewerId,
      (r.title ?? "").trim().toLowerCase(),
      (r.body ?? "").trim().toLowerCase(),
      minuteBucket,
    ].join("|");

    let g = groups.get(key);
    if (!g) {
      g = {
        key: r.id, // first row's id stays stable across re-renders
        createdAt: r.createdAt,
        title: r.title,
        body: r.body,
        otherRows: [],
        averageRating: r.rating,
      };
      groups.set(key, g);
    }

    if (r.targetType === "PROPERTY") g.propertyRow = r;
    else if (r.targetType === "OWNER") g.ownerRow = r;
    else g.otherRows.push(r);

    // Use the newest createdAt of the group for the collapsed display.
    if (r.createdAt && (!g.createdAt || r.createdAt > g.createdAt)) {
      g.createdAt = r.createdAt;
    }
  }
  // Compute average rating per group once all rows are paired in.
  for (const g of groups.values()) {
    const ratings: number[] = [];
    if (g.propertyRow) ratings.push(g.propertyRow.rating);
    if (g.ownerRow) ratings.push(g.ownerRow.rating);
    for (const o of g.otherRows) ratings.push(o.rating);
    g.averageRating =
      ratings.length === 0
        ? 0
        : Math.round(
            (ratings.reduce((s, n) => s + n, 0) / ratings.length) * 10,
          ) / 10;
  }
  // Sort newest-first by createdAt (falling back to the original
  // first-row insertion order when timestamps are missing).
  return [...groups.values()].sort((a, b) => {
    if (!a.createdAt && !b.createdAt) return 0;
    if (!a.createdAt) return 1;
    if (!b.createdAt) return -1;
    return b.createdAt.localeCompare(a.createdAt);
  });
}

/**
 * Collapsed card per submission. Click to expand and see the
 * PROPERTY + OWNER ratings split out with their individual badges.
 * Body is shown in both states (collapsed + expanded) because
 * truncating it on the collapsed card just hides what the user
 * actually wrote.
 */
function ReviewGroupCard({ group }: { group: ReviewGroup }) {
  const [expanded, setExpanded] = useState(false);
  const hasBothRatings = !!(group.propertyRow && group.ownerRow);
  return (
    <button
      type="button"
      onClick={() => setExpanded((v) => !v)}
      className={cn(
        "w-full text-left rounded-xl border bg-secondary/30 p-4 transition-colors",
        "hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
      )}
    >
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-0.5">
            {[1, 2, 3, 4, 5].map((s) => (
              <Star
                key={s}
                className={
                  s <= Math.round(group.averageRating)
                    ? "size-3.5 text-amber-500 fill-amber-500"
                    : "size-3.5 text-muted-foreground/30"
                }
              />
            ))}
          </div>
          {group.title && (
            <p className="font-medium text-sm">{group.title}</p>
          )}
          {hasBothRatings && (
            <Badge variant="secondary" className="text-[10px]">
              Property + Owner
            </Badge>
          )}
          {/* Moderation badge — uses the property row's status if
              available, else falls back to the owner row's. Both
              should track together since they were submitted as one
              unit. */}
          {(() => {
            const m =
              group.propertyRow?.moderationStatus ??
              group.ownerRow?.moderationStatus;
            if (m === "APPROVED") {
              return <Badge variant="success" className="text-[10px]">Approved</Badge>;
            }
            if (m === "PENDING") {
              return <Badge variant="warning" className="text-[10px]">Pending</Badge>;
            }
            if (m === "REJECTED") {
              return <Badge variant="destructive" className="text-[10px]">Rejected</Badge>;
            }
            return null;
          })()}
        </div>
        <div className="flex items-center gap-1 text-xs text-muted-foreground">
          <span>{expanded ? "Hide details" : "View details"}</span>
          <ChevronDown
            className={cn(
              "size-3.5 transition-transform",
              expanded && "rotate-180",
            )}
          />
        </div>
      </div>

      {group.body && (
        <p className="text-sm text-muted-foreground mt-2 whitespace-pre-wrap">
          {group.body}
        </p>
      )}

      {expanded && (
        <div className="mt-4 pt-4 border-t border-border/60 grid sm:grid-cols-2 gap-3">
          {group.propertyRow && (
            <RatingTile
              icon={Building2}
              label="Property rating"
              row={group.propertyRow}
              tint="sky"
            />
          )}
          {group.ownerRow && (
            <RatingTile
              icon={User}
              label="Owner rating"
              row={group.ownerRow}
              tint="amber"
            />
          )}
          {group.otherRows.map((row) => (
            <RatingTile
              key={row.id}
              icon={Star}
              label={`${row.targetType.toLowerCase()} rating`}
              row={row}
              tint="sky"
            />
          ))}
        </div>
      )}
    </button>
  );
}

function RatingTile({
  icon: Icon,
  label,
  row,
  tint,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  row: ReviewResponse;
  tint: "sky" | "amber";
}) {
  const wash =
    tint === "sky"
      ? "bg-sky-50/80 border-sky-200/60"
      : "bg-amber-50/80 border-amber-200/60";
  return (
    <div className={cn("rounded-lg border p-3", wash)}>
      <div className="flex items-center gap-2">
        <Icon className="size-4 text-foreground/70" />
        <p className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
          {label}
        </p>
      </div>
      <div className="flex items-center gap-0.5 mt-2">
        {[1, 2, 3, 4, 5].map((s) => (
          <Star
            key={s}
            className={
              s <= row.rating
                ? "size-4 text-amber-500 fill-amber-500"
                : "size-4 text-muted-foreground/30"
            }
          />
        ))}
        <span className="ml-1.5 text-sm font-semibold">{row.rating}/5</span>
      </div>
    </div>
  );
}
