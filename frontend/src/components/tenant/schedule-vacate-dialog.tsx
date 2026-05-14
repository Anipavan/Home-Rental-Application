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

  // Reset state every time the dialog opens so a previously-picked
  // date doesn't leak across re-opens.
  useEffect(() => {
    if (open) setVacateDate("");
  }, [open]);

  // Earliest acceptable date — today + 60 days. Used as the input's
  // `min` attribute and as the client-side validation threshold.
  const earliestDate = useMemo(() => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + VACATE_NOTICE_DAYS);
    return d;
  }, []);
  const earliestDateInput = toDateInput(earliestDate);

  // Validate the picked date. Empty = "not picked yet" (no error
  // message, button disabled). Anything < earliestDate = explicit
  // error message + disabled.
  const dateError = useMemo(() => {
    if (!vacateDate) return null;
    const picked = new Date(vacateDate + "T00:00:00");
    if (Number.isNaN(picked.getTime())) return "Pick a valid date.";
    if (picked < earliestDate) {
      return `You can't vacate the house before ${formatDate(
        earliestDate.toISOString(),
      )} — a minimum 60 days' notice to the owner is required.`;
    }
    return null;
  }, [vacateDate, earliestDate]);

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
      propertiesApi.flats.scheduleVacate(flatId, vacateDate),
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
              onChange={(e) => setVacateDate(e.target.value)}
              className="mt-1.5"
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              Earliest acceptable date:{" "}
              <span className="font-medium">
                {formatDate(earliestDate.toISOString())}
              </span>{" "}
              (60 days from today). Your owner is notified 10 days before
              the move-out date.
            </p>
            {dateError && (
              <p className="text-xs text-destructive mt-2">{dateError}</p>
            )}
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
