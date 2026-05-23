import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, Loader2, AlertTriangle } from "lucide-react";
import { propertiesApi } from "@/lib/api/properties";
import { paymentsApi } from "@/lib/api/payments";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, formatINR } from "@/lib/utils";

/**
 * Tenant-initiated scheduled-vacate confirmation dialog (Issue #5, #4).
 *
 * <p>Issue #4 change: the move-out date is now USER-PICKED (not
 * auto-locked to today + 60 days). The tenant picks a date in the
 * date input; the Confirm button enables only when:
 * <ul>
 *   <li>Picked date is at least 60 days from today (60-day notice
 *       per spec). Inline error if not.</li>
 *   <li>All outstanding rent on this flat is cleared (PENDING +
 *       OVERDUE invoices must be empty).</li>
 * </ul>
 *
 * <p>Backend (FlatServiceImpul.scheduleVacate) re-validates both
 * conditions on the server side, so a direct API call can't bypass.
 */
interface Props {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  flatId: string;
  flatNumber: string;
  tenantId: string; // authUserId of the tenant (matches Flat.tenantId)
}

const VACATE_NOTICE_DAYS = 60;

export function ScheduleVacateDialog({
  open,
  onOpenChange,
  flatId,
  flatNumber,
  tenantId,
}: Props) {
  const qc = useQueryClient();

  // User-picked move-out date. Empty string by default — the spec
  // is "should not be default, user should be able to enter a date".
  const [vacateDate, setVacateDate] = useState<string>("");

  // Free-text reason the tenant gives for vacating. Surfaced to the
  // owner on the 10-day warning email + the flat detail page. Empty
  // is allowed (it's optional); we trim before sending so accidental
  // whitespace doesn't get persisted.
  const [comments, setComments] = useState<string>("");
  const COMMENTS_MAX = 1000;

  // Earliest acceptable date — today + 60 days. Stored as a
  // YYYY-MM-DD string (not a Date) for two reasons:
  //
  //   1. The <input type="date"> needs a YYYY-MM-DD string for its
  //      `min` attribute, so we'd convert anyway.
  //   2. Comparing the picked date to this threshold via string
  //      comparison ("2026-07-12" < "2026-07-13") is purely
  //      lexicographic on a sortable format — no Date arithmetic,
  //      no timezone arguments, no DST-boundary surprises.
  //
  // Recomputed every time the dialog opens (not memoized to mount
  // time) so a tab left open past midnight still rolls the floor
  // forward.
  const [earliestDateInput, setEarliestDateInput] = useState<string>(() =>
    toDateInput(plusDays(new Date(), VACATE_NOTICE_DAYS)),
  );

  // Reset state every time the dialog opens so a previously-picked
  // date doesn't leak across re-opens, AND refresh the floor so the
  // threshold reflects "today" at the moment the dialog opens.
  useEffect(() => {
    if (open) {
      setVacateDate("");
      setComments("");
      setEarliestDateInput(toDateInput(plusDays(new Date(), VACATE_NOTICE_DAYS)));
    }
  }, [open]);

  // Pretty-format the earliest date for display in the help-text +
  // error message. Built from the same string the input enforces, so
  // there's no chance of the display drifting from the validation.
  const earliestDateLabel = formatDate(earliestDateInput);

  // Validate the picked date. Empty = "not picked yet" (no error
  // message, button disabled). Anything earlier than earliestDateInput
  // = explicit error message + disabled. Pure string comparison —
  // YYYY-MM-DD sorts chronologically as text so this is both correct
  // and timezone-immune.
  const dateError = useMemo(() => {
    if (!vacateDate) return null;
    // Sanity: input control only emits YYYY-MM-DD, but guard against
    // hand-edited inputs / autocomplete glitches just in case.
    if (!/^\d{4}-\d{2}-\d{2}$/.test(vacateDate)) {
      return "Pick a valid date.";
    }
    if (vacateDate < earliestDateInput) {
      return `Can't vacate the house before 60 days. Earliest move-out date: ${earliestDateLabel}.`;
    }
    return null;
  }, [vacateDate, earliestDateInput, earliestDateLabel]);

  // Outstanding-dues check — same logic the backend enforces, but
  // surfaced here so the Confirm button stays disabled until cleared.
  const paymentsQ = useQuery({
    queryKey: ["tenant-payments-for-vacate", tenantId, flatId],
    queryFn: () => paymentsApi.byTenant(tenantId),
    enabled: open && !!tenantId,
    staleTime: 30_000,
  });

  const dues = useMemo(() => {
    const list = paymentsQ.data ?? [];
    const blocking = list.filter(
      (p) =>
        p.flatId === flatId &&
        (p.status === "PENDING" || p.status === "OVERDUE"),
    );
    const total = blocking.reduce(
      (sum, p) =>
        sum +
        Number(
          (p as { totalAmount?: number; amount?: number }).totalAmount ??
            p.amount ??
            0,
        ),
      0,
    );
    return { count: blocking.length, total };
  }, [paymentsQ.data, flatId]);

  const scheduleM = useMutation({
    mutationFn: () =>
      propertiesApi.flats.scheduleVacate(flatId, vacateDate, comments),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-flats"] });
      qc.invalidateQueries({ queryKey: ["flat", flatId] });
      toast({
        title: "Vacate scheduled",
        description: `You'll move out of Flat ${flatNumber} on ${formatDate(
          vacateDate,
        )}. Your owner has been notified.`,
      });
      onOpenChange(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't schedule vacate",
        description: extractErrorMessage(e),
      }),
  });

  const canConfirm =
    !!vacateDate &&
    !dateError &&
    !paymentsQ.isLoading &&
    dues.count === 0 &&
    !scheduleM.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <CalendarClock className="size-4 text-primary" /> Schedule vacate
          </DialogTitle>
          <DialogDescription>
            Plan to move out of{" "}
            <span className="font-medium">Flat {flatNumber}</span>? Pick a
            move-out date at least 60 days from today.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Move-out date — user-picked. min attribute enforces
              the 60-day floor at the browser level for date pickers
              that respect it; the dateError check is the cross-
              browser fallback + error-message source. */}
          <div>
            <Label htmlFor="vacateDate">Move-out date</Label>
            <Input
              id="vacateDate"
              type="date"
              value={vacateDate}
              min={earliestDateInput}
              // aria-invalid lights up screen-reader feedback the
              // moment the picked date violates the 60-day floor.
              aria-invalid={!!dateError}
              aria-describedby="vacateDate-error vacateDate-help"
              onChange={(e) => setVacateDate(e.target.value)}
              className="mt-1.5"
            />
            <p id="vacateDate-help" className="text-[11px] text-muted-foreground mt-1">
              Earliest acceptable date:{" "}
              <span className="font-medium">{earliestDateLabel}</span>{" "}
              (60 days from today). Your owner is notified 10 days before
              the move-out date.
            </p>
            {/* Promoted from a quiet line of text to a banner-style
                alert so the rejection is unmissable — the previous
                rendering was easy to miss next to the help text. */}
            {dateError && (
              <div
                id="vacateDate-error"
                role="alert"
                className="mt-2 flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive"
              >
                <AlertTriangle className="size-3.5 mt-0.5 shrink-0" />
                <span>{dateError}</span>
              </div>
            )}
          </div>

          {/* Reason for vacating — free-text, optional but encouraged.
              Surfaced to the owner so they can act on recurring property
              issues + plan re-letting. Capped at 1000 chars to match
              the DB column; over-cap pasted text is trimmed server-side. */}
          <div>
            <Label htmlFor="vacateComments">
              Reason for vacating{" "}
              <span className="text-muted-foreground font-normal">(optional)</span>
            </Label>
            <Textarea
              id="vacateComments"
              value={comments}
              onChange={(e) => setComments(e.target.value.slice(0, COMMENTS_MAX))}
              placeholder="E.g. Relocating for work, moving to a bigger flat, maintenance issues not resolved…"
              rows={3}
              maxLength={COMMENTS_MAX}
              className="mt-1.5"
            />
            <p className="text-[11px] text-muted-foreground mt-1 flex justify-between">
              <span>Helps your owner plan re-letting and act on recurring issues.</span>
              <span>
                {comments.length}/{COMMENTS_MAX}
              </span>
            </p>
          </div>

          {/* Outstanding-dues panel — only shown when there are blocking dues */}
          {paymentsQ.isLoading ? (
            <div className="text-sm text-muted-foreground flex items-center gap-2">
              <Loader2 className="size-4 animate-spin" />
              Checking outstanding rent…
            </div>
          ) : dues.count > 0 ? (
            <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 space-y-1">
              <div className="flex items-center gap-2 text-destructive">
                <AlertTriangle className="size-4" />
                <p className="font-semibold text-sm">
                  Clear outstanding rent first
                </p>
              </div>
              <p className="text-xs text-muted-foreground">
                You have{" "}
                <span className="font-semibold text-destructive">
                  {formatINR(dues.total)}
                </span>{" "}
                across {dues.count} unpaid invoice(s) on this flat. Pay them
                before you can schedule a vacate.
              </p>
              <Button
                asChild
                size="sm"
                variant="destructive"
                className="mt-2"
              >
                <a href="/app/payments">Pay rent now</a>
              </Button>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">
              ✓ No outstanding rent. You're good to schedule once you pick a date.
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={scheduleM.isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={!canConfirm}
            onClick={() => scheduleM.mutate()}
          >
            {scheduleM.isPending && <Loader2 className="size-4 animate-spin" />}
            Confirm vacate
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** YYYY-MM-DD in the local timezone — what <input type="date"> needs. */
function toDateInput(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * Return a NEW Date that's {@code days} after {@code base}, with the
 * time component clamped to local midnight. Used to compute the
 * "earliest acceptable move-out date" without mutating the caller's
 * Date object. {@code Date#setDate} handles month / year rollovers,
 * leap years, and DST transitions correctly, so a naive +ms approach
 * (which would be off-by-an-hour twice a year in DST locales) is
 * deliberately avoided here.
 */
function plusDays(base: Date, days: number): Date {
  const d = new Date(base.getTime());
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + days);
  return d;
}
