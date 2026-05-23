import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, Star } from "lucide-react";
import { reviewsApi } from "@/lib/api/reviews";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/**
 * Unified review form — covers ONE stay with TWO ratings (property +
 * owner) and a SINGLE shared text body.
 *
 * <p>Design choice: instead of refactoring the Review entity to carry
 * two ratings on a single document, this component issues two POSTs
 * to /reviews (one with targetType=PROPERTY, one with OWNER) sharing
 * the same body and title. Pros: zero backend changes, both surfaces
 * (property reviews list, owner reviews list) keep working unchanged.
 * Con: ~2x storage per stay, but reviews are low-volume so it's
 * a worthwhile trade for the simpler ship.
 *
 * <p>If the second POST fails after the first succeeded, the user
 * sees a "partial submit" toast — they can re-open the form and
 * resubmit. The first review is harmless on its own and will simply
 * not have an owner-rating pair until they retry.
 */
interface Props {
  /** authUserId of the tenant writing the review. */
  reviewerId: string;
  /** Building id (the "property" target). */
  propertyTargetId: string;
  /** Owner authUserId (the "owner" target). */
  ownerTargetId: string;
  /** Friendly name of the property — used in the form headline. */
  propertyLabel?: string;
  onSubmitted?: () => void;
  onCancel?: () => void;
}

export function UnifiedStayReviewForm({
  reviewerId,
  propertyTargetId,
  ownerTargetId,
  propertyLabel,
  onSubmitted,
  onCancel,
}: Props) {
  const qc = useQueryClient();
  const [propertyRating, setPropertyRating] = useState(0);
  const [propertyHover, setPropertyHover] = useState(0);
  const [ownerRating, setOwnerRating] = useState(0);
  const [ownerHover, setOwnerHover] = useState(0);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [tags, setTags] = useState("");

  /**
   * Submit both reviews. The mutation returns the count of successful
   * POSTs so the toast can phrase the result correctly:
   *   2 → "Thanks — both reviews are in."
   *   1 → "Partial submit — one half didn't go through. Retry?"
   *   0 → throw (caught by onError)
   */
  const submitM = useMutation({
    mutationFn: async () => {
      const parsedTags = tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean);
      const sharedBase = {
        reviewerId,
        reviewerType: "TENANT" as const,
        title: title.trim() || undefined,
        body: body.trim() || undefined,
        tags: parsedTags.length ? parsedTags : undefined,
      };

      // Fire both in parallel — partial success is captured below.
      const [propRes, ownerRes] = await Promise.allSettled([
        reviewsApi.submit({
          ...sharedBase,
          targetId: propertyTargetId,
          targetType: "PROPERTY",
          rating: propertyRating,
        }),
        reviewsApi.submit({
          ...sharedBase,
          targetId: ownerTargetId,
          targetType: "OWNER",
          rating: ownerRating,
        }),
      ]);
      const okCount =
        (propRes.status === "fulfilled" ? 1 : 0) +
        (ownerRes.status === "fulfilled" ? 1 : 0);

      if (okCount === 0) {
        // Both failed — use the property error message since it's
        // typically the more informative one when both target IDs
        // are valid but the gateway/server is down.
        if (propRes.status === "rejected") throw propRes.reason;
        if (ownerRes.status === "rejected") throw ownerRes.reason;
      }
      return okCount;
    },
    onSuccess: (okCount) => {
      qc.invalidateQueries({ queryKey: ["reviews", "PROPERTY", propertyTargetId] });
      qc.invalidateQueries({ queryKey: ["reviews", "OWNER", ownerTargetId] });
      qc.invalidateQueries({ queryKey: ["review-summary", "PROPERTY", propertyTargetId] });
      qc.invalidateQueries({ queryKey: ["review-summary", "OWNER", ownerTargetId] });
      qc.invalidateQueries({ queryKey: ["my-reviews"] });
      if (okCount === 2) {
        toast({
          title: "Thanks — your review is in",
          description:
            "Both the property and the owner have been rated. Future tenants will see it after a quick moderation pass.",
        });
      } else {
        toast({
          variant: "destructive",
          title: "Partial submit",
          description:
            "Only one of the two ratings went through. Open the form again to retry the missing half.",
        });
      }
      // Clear form state on full success only.
      if (okCount === 2) {
        setPropertyRating(0);
        setOwnerRating(0);
        setTitle("");
        setBody("");
        setTags("");
      }
      onSubmitted?.();
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't submit",
        description: extractErrorMessage(e),
      }),
  });

  const valid = propertyRating > 0 && ownerRating > 0;

  return (
    <form
      className="space-y-4 rounded-xl border bg-background p-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (!valid) {
          toast({
            variant: "destructive",
            title: "Rate both the property and the owner",
            description:
              "Tap a star next to each so future tenants get the full picture.",
          });
          return;
        }
        submitM.mutate();
      }}
    >
      <div>
        <p className="text-sm font-medium">
          {propertyLabel
            ? `Review your stay at ${propertyLabel}`
            : "Review your stay"}
        </p>
        <p className="text-xs text-muted-foreground mt-1">
          Two ratings, one comment — separate stars for the place and for
          how the owner handled things.
        </p>
      </div>

      <StarRow
        label="Property rating"
        sublabel="Maintenance, cleanliness, neighbourhood, amenities"
        value={propertyRating}
        hover={propertyHover}
        setValue={setPropertyRating}
        setHover={setPropertyHover}
      />

      <StarRow
        label="Owner rating"
        sublabel="Responsiveness, fairness, communication"
        value={ownerRating}
        hover={ownerHover}
        setValue={setOwnerRating}
        setHover={setOwnerHover}
      />

      <div>
        <Label htmlFor="us-title">Title (optional)</Label>
        <Input
          id="us-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          placeholder="Quiet flat, responsive owner"
          className="mt-1.5"
        />
      </div>

      <div>
        <Label htmlFor="us-body">Your experience</Label>
        <Textarea
          id="us-body"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={4}
          maxLength={4000}
          placeholder="Tell future tenants what to expect — what worked, what didn't?"
          className="mt-1.5"
        />
        <p className="text-[11px] text-muted-foreground mt-1">
          One comment covers both the property and the owner — future
          tenants see it under both surfaces.
        </p>
      </div>

      <div>
        <Label htmlFor="us-tags">Tags (comma-separated, optional)</Label>
        <Input
          id="us-tags"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          placeholder="quiet, well-maintained, central"
          className="mt-1.5"
        />
      </div>

      <div className="flex items-center justify-end gap-2">
        {onCancel && (
          <Button
            type="button"
            variant="ghost"
            onClick={onCancel}
            disabled={submitM.isPending}
          >
            Cancel
          </Button>
        )}
        <Button
          type="submit"
          variant="gradient"
          disabled={!valid || submitM.isPending}
        >
          {submitM.isPending && <Loader2 className="size-4 animate-spin" />}
          Submit review
        </Button>
      </div>
    </form>
  );
}

/** Single star-row with hover preview. Reused for property + owner rating. */
function StarRow({
  label,
  sublabel,
  value,
  hover,
  setValue,
  setHover,
}: {
  label: string;
  sublabel: string;
  value: number;
  hover: number;
  setValue: (n: number) => void;
  setHover: (n: number) => void;
}) {
  return (
    <div>
      <Label>{label}</Label>
      <p className="text-[11px] text-muted-foreground">{sublabel}</p>
      <div className="flex items-center gap-1 mt-1.5">
        {[1, 2, 3, 4, 5].map((s) => (
          <button
            key={s}
            type="button"
            onMouseEnter={() => setHover(s)}
            onMouseLeave={() => setHover(0)}
            onClick={() => setValue(s)}
            aria-label={`${s} star${s === 1 ? "" : "s"}`}
          >
            <Star
              className={
                s <= (hover || value)
                  ? "size-7 text-amber-500 fill-amber-500"
                  : "size-7 text-muted-foreground/30"
              }
            />
          </button>
        ))}
        {value > 0 && (
          <span className="ml-2 text-xs text-muted-foreground">
            {value}/5
          </span>
        )}
      </div>
    </div>
  );
}
