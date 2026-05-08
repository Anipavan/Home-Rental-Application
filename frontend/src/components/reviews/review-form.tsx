import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Star } from "lucide-react";
import { useState } from "react";
import { reviewsApi } from "@/lib/api/reviews";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type {
  CreateReviewRequest,
  ReviewerType,
  ReviewTargetType,
} from "@/types/api";

interface Props {
  reviewerId: string;
  reviewerType: ReviewerType;
  targetId: string;
  targetType: ReviewTargetType;
  onSubmitted?: () => void;
}

/** Compact form to write a review. Used on property-detail and tenant pages. */
export function ReviewForm({
  reviewerId,
  reviewerType,
  targetId,
  targetType,
  onSubmitted,
}: Props) {
  const qc = useQueryClient();
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);

  const submitM = useMutation({
    mutationFn: (b: CreateReviewRequest) => reviewsApi.submit(b),
    onSuccess: () => {
      // Invalidate any list / summary that displays reviews for this target
      qc.invalidateQueries({ queryKey: ["reviews", targetType, targetId] });
      qc.invalidateQueries({ queryKey: ["review-summary", targetType, targetId] });
      setRating(0);
      toast({ title: "Thanks — your review is in." });
      onSubmitted?.();
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't submit",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <form
      className="space-y-3 rounded-xl border bg-background p-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (rating < 1) {
          toast({
            variant: "destructive",
            title: "Pick a rating",
            description: "Tap a star to rate before submitting.",
          });
          return;
        }
        const fd = new FormData(e.currentTarget);
        const tagsRaw = String(fd.get("tags") ?? "").trim();
        submitM.mutate({
          reviewerId,
          reviewerType,
          targetId,
          targetType,
          rating,
          title: String(fd.get("title") ?? "") || undefined,
          body: String(fd.get("body") ?? "") || undefined,
          tags: tagsRaw
            ? tagsRaw.split(",").map((t) => t.trim()).filter(Boolean)
            : undefined,
        });
      }}
    >
      <div>
        <Label>Rating</Label>
        <div className="flex items-center gap-1 mt-1.5">
          {[1, 2, 3, 4, 5].map((s) => (
            <button
              key={s}
              type="button"
              onMouseEnter={() => setHover(s)}
              onMouseLeave={() => setHover(0)}
              onClick={() => setRating(s)}
              aria-label={`${s} star${s === 1 ? "" : "s"}`}
            >
              <Star
                className={
                  s <= (hover || rating)
                    ? "size-7 text-amber-500 fill-amber-500"
                    : "size-7 text-muted-foreground/30"
                }
              />
            </button>
          ))}
        </div>
      </div>
      <div>
        <Label htmlFor="title">Title</Label>
        <Input
          id="title"
          name="title"
          maxLength={200}
          placeholder="Quiet neighbourhood, responsive owner"
          className="mt-1.5"
        />
      </div>
      <div>
        <Label htmlFor="body">Your experience</Label>
        <Textarea
          id="body"
          name="body"
          rows={4}
          maxLength={4000}
          placeholder="Tell future tenants what to expect — what worked, what didn't?"
          className="mt-1.5"
        />
      </div>
      <div>
        <Label htmlFor="tags">Tags (comma-separated, optional)</Label>
        <Input
          id="tags"
          name="tags"
          placeholder="quiet, well-maintained, central"
          className="mt-1.5"
        />
      </div>
      <Button type="submit" variant="gradient" disabled={submitM.isPending}>
        {submitM.isPending ? "Submitting…" : "Submit review"}
      </Button>
    </form>
  );
}
