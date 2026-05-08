import { useQuery } from "@tanstack/react-query";
import { Star, Pencil } from "lucide-react";
import { useState } from "react";
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
import { ReviewForm } from "@/components/reviews/review-form";

export function TenantReviewsPage() {
  const { authUserId } = useAuthStore();
  const [writing, setWriting] = useState<{ id: string; type: "PROPERTY" | "OWNER" } | null>(null);

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

  return (
    <div className="animate-fade-in max-w-4xl">
      <PageHeader
        title="Reviews"
        description="Share your experience and help future tenants."
      />

      <Tabs defaultValue="write">
        <TabsList>
          <TabsTrigger value="write">Write a review</TabsTrigger>
          <TabsTrigger value="mine">My reviews ({myReviews.length})</TabsTrigger>
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
                    You can review the property and the owner separately.
                  </p>
                  {myFlatsQ.data.map((f) => (
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
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() =>
                              setWriting({ id: f.buildingId, type: "PROPERTY" })
                            }
                          >
                            <Pencil /> Property
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() =>
                              setWriting({
                                id: String((f as { ownerId?: string }).ownerId ?? f.buildingId),
                                type: "OWNER",
                              })
                            }
                          >
                            <Pencil /> Owner
                          </Button>
                        </div>
                      </div>
                      {writing &&
                        ((writing.type === "PROPERTY" && writing.id === f.buildingId) ||
                          (writing.type === "OWNER" &&
                            writing.id === String((f as { ownerId?: string }).ownerId ?? f.buildingId))) && (
                          <div className="mt-4">
                            <ReviewForm
                              reviewerId={userId ?? ""}
                              reviewerType="TENANT"
                              targetId={writing.id}
                              targetType={writing.type}
                              onSubmitted={() => setWriting(null)}
                            />
                          </div>
                        )}
                    </div>
                  ))}
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
              ) : myReviews.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  You haven't written a review yet.
                </p>
              ) : (
                <div className="space-y-3">
                  {myReviews.map((r) => (
                    <div
                      key={r.id}
                      className="rounded-xl border bg-secondary/30 p-4"
                    >
                      <div className="flex items-center justify-between flex-wrap gap-2">
                        <div className="flex items-center gap-2 flex-wrap">
                          <div className="flex items-center gap-0.5">
                            {[1, 2, 3, 4, 5].map((s) => (
                              <Star
                                key={s}
                                className={
                                  s <= r.rating
                                    ? "size-3.5 text-amber-500 fill-amber-500"
                                    : "size-3.5 text-muted-foreground/30"
                                }
                              />
                            ))}
                          </div>
                          {r.title && <p className="font-medium text-sm">{r.title}</p>}
                          <Badge variant="secondary" className="text-[10px]">
                            {r.targetType}
                          </Badge>
                          {r.moderationStatus === "APPROVED" && (
                            <Badge variant="success" className="text-[10px]">
                              Approved
                            </Badge>
                          )}
                          {r.moderationStatus === "PENDING" && (
                            <Badge variant="warning" className="text-[10px]">
                              Pending
                            </Badge>
                          )}
                          {r.moderationStatus === "REJECTED" && (
                            <Badge variant="destructive" className="text-[10px]">
                              Rejected
                            </Badge>
                          )}
                        </div>
                      </div>
                      {r.body && (
                        <p className="text-sm text-muted-foreground mt-2 whitespace-pre-wrap">
                          {r.body}
                        </p>
                      )}
                    </div>
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
