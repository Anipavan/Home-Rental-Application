import { useMemo } from "react";
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
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, formatINR } from "@/lib/utils";

/**
 * Tenant-initiated scheduled-vacate confirmation dialog (Issue #5).
 *
 * <p>The spec is "vacate any time after assigned, but informed to
 * owner 2 months prior, and all dues should be cleared before
 * vacating". This dialog enforces all three:
 * <ul>
 *   <li>Confirms the locked effective date — always today + 60 days.</li>
 *   <li>Fetches the tenant's payments and surfaces total PENDING +
 *       OVERDUE rent for the current flat. If > 0, the Confirm button
 *       stays disabled with a clear "Pay ₹X first" message.</li>
 *   <li>On confirm, calls {@code POST /flats/{id}/schedule-vacate}
 *       which sets {@code scheduledVacateDate} on the flat. The
 *       backend re-checks dues server-side (defence in depth) so a
 *       direct API call can't bypass.</li>
 * </ul>
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

  /* Compute the locked vacate date — today + 60 days. Memoised so the
   * label doesn't drift on every render (and so the user sees the same
   * date they'll actually get). */
  const effectiveDate = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + VACATE_NOTICE_DAYS);
    return d;
  }, []);

  // Outstanding-dues check — same logic the backend enforces, but
  // surfaced here so the Confirm button stays disabled until cleared.
  // Filters server response to PENDING + OVERDUE on this specific flat
  // (a tenant might have history on a previous flat we shouldn't block on).
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
    mutationFn: () => propertiesApi.flats.scheduleVacate(flatId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["my-flats"] });
      qc.invalidateQueries({ queryKey: ["flat", flatId] });
      toast({
        title: "Vacate scheduled",
        description: `You'll move out of Flat ${flatNumber} on ${formatDate(
          effectiveDate.toISOString(),
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
            <span className="font-medium">Flat {flatNumber}</span>? You'll
            still pay rent and can raise issues until the date below.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Locked effective date — always today + 60 days */}
          <div className="rounded-xl border bg-secondary/30 p-4">
            <p className="text-xs text-muted-foreground">Move-out date</p>
            <p className="font-display font-semibold text-lg mt-0.5">
              {formatDate(effectiveDate.toISOString())}
            </p>
            <p className="text-[11px] text-muted-foreground mt-1">
              Locked to {VACATE_NOTICE_DAYS} days from today so your owner
              has time to prepare. Your owner will be alerted 10 days before
              this date.
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
              ✓ No outstanding rent. You're good to schedule.
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
