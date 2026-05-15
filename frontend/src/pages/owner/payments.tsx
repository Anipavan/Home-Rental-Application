import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Banknote, Bell, Download, FileText, Loader2, QrCode } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { paymentsApi } from "@/lib/api/payments";
import { notificationsApi } from "@/lib/api/notifications";
import { useFlatLookup } from "@/hooks/use-flat-lookup";
import { useUserLookup } from "@/hooks/use-user-lookup";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse, PaymentStatus } from "@/types/api";

// Track recently-sent reminders client-side. Survives a page refresh via
// localStorage so the owner doesn't accidentally double-remind.
const REMINDER_LOG_KEY = "hearth-payment-reminders";
const REMINDER_COOLDOWN_HOURS = 12;

function loadReminderLog(): Record<string, number> {
  try {
    const raw = localStorage.getItem(REMINDER_LOG_KEY);
    return raw ? (JSON.parse(raw) as Record<string, number>) : {};
  } catch {
    return {};
  }
}
function saveReminderLog(log: Record<string, number>) {
  try {
    localStorage.setItem(REMINDER_LOG_KEY, JSON.stringify(log));
  } catch {
    /* ignore */
  }
}

export function OwnerPaymentsPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [cashTarget, setCashTarget] = useState<PaymentResponse | null>(null);
  const [upiTarget, setUpiTarget] = useState<PaymentResponse | null>(null);
  const [reminderLog, setReminderLog] = useState<Record<string, number>>(
    () => loadReminderLog(),
  );

  useEffect(() => saveReminderLog(reminderLog), [reminderLog]);

  const q = useQuery({
    queryKey: ["owner-payments", authUserId],
    queryFn: () => paymentsApi.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const cashMutation = useMutation({
    mutationFn: ({ id, reference }: { id: string; reference?: string }) =>
      paymentsApi.payCash(id, { ownerId: authUserId!, reference }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-payments", authUserId] });
      qc.invalidateQueries({ queryKey: ["payment"] });
      qc.invalidateQueries({ queryKey: ["my-payments"] });
      toast({ title: "Marked as paid", description: "Cash receipt recorded." });
      setCashTarget(null);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't record cash payment",
        description: extractErrorMessage(e),
      }),
  });

  // Same mutation as cashMutation but hits the UPI receipt endpoint
  // and labels the resulting toast / audit row differently. Owner
  // sees their UPI / NEFT money in the bank, opens this dialog,
  // types the UPI reference number from the SMS / app, hits confirm.
  const upiMutation = useMutation({
    mutationFn: ({ id, reference }: { id: string; reference?: string }) =>
      paymentsApi.markUpiReceived(id, { ownerId: authUserId!, reference }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-payments", authUserId] });
      qc.invalidateQueries({ queryKey: ["payment"] });
      qc.invalidateQueries({ queryKey: ["my-payments"] });
      toast({
        title: "Marked as paid",
        description: "UPI / bank-transfer receipt recorded.",
      });
      setUpiTarget(null);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't record UPI payment",
        description: extractErrorMessage(e),
      }),
  });

  const remindMutation = useMutation({
    mutationFn: (p: PaymentResponse) => {
      const overdue = p.status === "OVERDUE";
      return notificationsApi.sendEmail({
        userId: p.tenantId,
        type: "EMAIL",
        category: overdue ? "PAYMENT_OVERDUE" : "PAYMENT_REMINDER",
        subject: overdue
          ? `Overdue rent for flat #${p.flatId}`
          : `Friendly rent reminder for flat #${p.flatId}`,
        message: overdue
          ? `Hi, this is a reminder that the rent of ₹${p.totalAmount ?? p.amount} for flat ${p.flatId} (due ${p.dueDate}) is overdue. Please pay at your earliest convenience.`
          : `Hi, just a quick reminder that ₹${p.totalAmount ?? p.amount} of rent is due on ${p.dueDate} for flat ${p.flatId}. You can pay from your tenant dashboard.`,
        templateVariables: {
          flatId: p.flatId,
          amount: p.totalAmount ?? p.amount,
          dueDate: p.dueDate,
          status: p.status,
        },
      });
    },
    onSuccess: (_data, p) => {
      setReminderLog((log) => ({ ...log, [p.id]: Date.now() }));
      toast({
        title: "Reminder sent",
        description: `Tenant ${p.tenantId} has been notified.`,
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't send reminder",
        description: extractErrorMessage(e),
      }),
  });

  const list = q.data ?? [];
  const pending = list.filter((p) => p.status === "PENDING");
  const overdue = list.filter((p) => p.status === "OVERDUE");
  const paid = list.filter((p) => p.status === "PAID");

  const totalCollected = paid.reduce(
    (s, p) => s + Number(p.totalAmount ?? p.amount),
    0,
  );
  const totalOutstanding = [...pending, ...overdue].reduce(
    (s, p) => s + Number(p.totalAmount ?? p.amount),
    0,
  );

  const renderTable = (rows: PaymentResponse[]) => (
    <Table
      loading={q.isLoading}
      payments={rows}
      onMarkCash={(p) => setCashTarget(p)}
      onMarkUpi={(p) => setUpiTarget(p)}
      onRemind={(p) => remindMutation.mutate(p)}
      remindingId={remindMutation.isPending ? remindMutation.variables?.id : undefined}
      reminderLog={reminderLog}
    />
  );

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Payments"
        description="Every rent payment, paid and pending."
        actions={
          <Button variant="outline">
            <Download /> Export CSV
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3 mb-6">
        <Stat label="Collected" value={formatINR(totalCollected)} tone="success" />
        <Stat label="Outstanding" value={formatINR(totalOutstanding)} tone="warning" />
        <Stat
          label="Overdue"
          value={formatINR(
            overdue.reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0),
          )}
          tone="destructive"
        />
      </div>

      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All ({list.length})</TabsTrigger>
          <TabsTrigger value="overdue">Overdue ({overdue.length})</TabsTrigger>
          <TabsTrigger value="pending">Pending ({pending.length})</TabsTrigger>
          <TabsTrigger value="paid">Paid ({paid.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="all">{renderTable(list)}</TabsContent>
        <TabsContent value="overdue">{renderTable(overdue)}</TabsContent>
        <TabsContent value="pending">{renderTable(pending)}</TabsContent>
        <TabsContent value="paid">{renderTable(paid)}</TabsContent>
      </Tabs>

      <RecordCashDialog
        target={cashTarget}
        onClose={() => setCashTarget(null)}
        onConfirm={(reference) => {
          if (cashTarget) {
            cashMutation.mutate({ id: cashTarget.id, reference });
          }
        }}
        loading={cashMutation.isPending}
      />

      <RecordUpiDialog
        target={upiTarget}
        onClose={() => setUpiTarget(null)}
        onConfirm={(reference) => {
          if (upiTarget) {
            upiMutation.mutate({ id: upiTarget.id, reference });
          }
        }}
        loading={upiMutation.isPending}
      />
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: "success" | "warning" | "destructive";
}) {
  const cls =
    tone === "success"
      ? "text-success"
      : tone === "warning"
        ? "text-warning"
        : tone === "destructive"
          ? "text-destructive"
          : "";
  return (
    <Card className="p-5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`font-display text-2xl font-bold mt-1 ${cls}`}>{value}</p>
    </Card>
  );
}

function Table({
  loading,
  payments,
  onMarkCash,
  onMarkUpi,
  onRemind,
  remindingId,
  reminderLog,
}: {
  loading?: boolean;
  payments: PaymentResponse[];
  onMarkCash: (p: PaymentResponse) => void;
  onMarkUpi: (p: PaymentResponse) => void;
  onRemind: (p: PaymentResponse) => void;
  remindingId?: string;
  reminderLog: Record<string, number>;
}) {
  // Resolve flat UUID -> "A-302" and tenant authUserId -> "First Last" for
  // every row in this table. Single batch fetch, cached for 60 s.
  const flatLookup = useFlatLookup(payments.map((p) => p.flatId));
  const userLookup = useUserLookup(payments.map((p) => p.tenantId));

  if (loading) {
    return (
      <Card className="p-3 space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </Card>
    );
  }
  if (payments.length === 0) {
    return (
      <Card className="p-12 text-center text-muted-foreground">
        Nothing to show here.
      </Card>
    );
  }
  return (
    <Card>
      <div className="hidden sm:grid grid-cols-[90px_1fr_100px_100px_110px_100px_100px_240px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
        <span>Flat</span>
        <span>Tenant</span>
        <span>Due</span>
        <span>Paid</span>
        <span>Amount</span>
        <span>Status</span>
        <span>Method</span>
        <span className="text-right">Actions</span>
      </div>
      <div className="divide-y">
        {payments.map((p) => {
          const lastRemindedAt = reminderLog[p.id];
          const recently =
            lastRemindedAt &&
            Date.now() - lastRemindedAt < REMINDER_COOLDOWN_HOURS * 3600 * 1000;
          // Backend's guardNotPaid blocks pay-cash only on PAID +
          // CANCELLED — so the FE button mirrors that and stays open
          // for PENDING / OVERDUE (normal flow) AND for PROCESSING /
          // FAILED (tenant tried UPI / card, gateway didn't settle,
          // owner now records the cash that was handed over off-band).
          // Without this, the button disappeared the moment a gateway
          // attempt left the invoice in PROCESSING — owner had no
          // way to reconcile.
          const canRecordCash =
            p.status === "PENDING" ||
            p.status === "OVERDUE" ||
            p.status === "PROCESSING" ||
            p.status === "FAILED";
          const isUnpaid = p.status === "PENDING" || p.status === "OVERDUE";
          return (
            <div
              key={p.id}
              className="grid grid-cols-2 sm:grid-cols-[90px_1fr_100px_100px_110px_100px_100px_240px] gap-3 px-5 py-3.5 text-sm items-center"
            >
              <span className="font-medium">{flatLookup.nameOf(p.flatId)}</span>
              <span className="truncate text-muted-foreground">
                {userLookup.nameOf(p.tenantId)}
              </span>
              <span className="text-muted-foreground">{formatDate(p.dueDate)}</span>
              <span className="text-muted-foreground">
                {p.paymentDate ? formatDate(p.paymentDate) : "—"}
              </span>
              <span className="font-medium">
                {formatINR(p.totalAmount ?? p.amount)}
              </span>
              <StatusBadge status={p.status} />
              {/* Method column — surfaces "Cash" vs "UPI" / "Card" /
                  etc. at a glance so the owner can reconcile what's
                  digital vs what they collected by hand. Renders an
                  em-dash for invoices that haven't been settled yet. */}
              <MethodBadge method={p.paymentMethod} />
              <div className="flex justify-end gap-2 flex-wrap">
                {isUnpaid && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => onRemind(p)}
                    disabled={remindingId === p.id || Boolean(recently)}
                    title={
                      recently
                        ? `Reminded ${Math.round(
                            (Date.now() - lastRemindedAt!) / 60000,
                          )}m ago — wait ${REMINDER_COOLDOWN_HOURS}h before sending another.`
                        : "Send a payment reminder email to this tenant"
                    }
                  >
                    {remindingId === p.id ? (
                      <Loader2 className="animate-spin" />
                    ) : (
                      <Bell />
                    )}
                    {recently ? "Reminded" : "Remind"}
                  </Button>
                )}
                {canRecordCash && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => onMarkUpi(p)}
                    title="Tenant paid via UPI / NEFT / IMPS — confirm receipt."
                  >
                    <QrCode className="size-4" /> UPI received
                  </Button>
                )}
                {canRecordCash && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => onMarkCash(p)}
                    title="Tenant paid in cash — record it now."
                  >
                    <Banknote /> Record cash
                  </Button>
                )}
                {p.status === "PAID" && <ReceiptButton paymentId={p.id} />}
                {isUnpaid && <InvoiceButton paymentId={p.id} />}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/**
 * Compact "how was this paid?" badge for the payments table. Maps the
 * backend's underscore-separated enum names to human-friendly labels
 * ({@code NET_BANKING} → "Net banking", {@code BANK_TRANSFER} → "Bank
 * transfer"). CASH gets a coin icon so it pops in the reconciliation
 * scan.
 */
function MethodBadge({ method }: { method?: string | null }) {
  if (!method) {
    return <span className="text-xs text-muted-foreground">—</span>;
  }
  if (method === "CASH") {
    return (
      <Badge variant="secondary" className="gap-1">
        <Banknote className="size-3" /> Cash
      </Badge>
    );
  }
  const label = method
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
  return (
    <Badge variant="secondary" className="text-[10px]">
      {label}
    </Badge>
  );
}

function RecordCashDialog({
  target,
  onClose,
  onConfirm,
  loading,
}: {
  target: PaymentResponse | null;
  onClose: () => void;
  /**
   * Called with the trimmed reference, or {@code undefined} when the
   * owner left the field blank. The {@code undefined} path tells the
   * service to auto-generate a {@code CASH-<uuid>} transaction id so
   * the receipt + audit log still have a unique row identifier.
   */
  onConfirm: (reference: string | undefined) => void;
  loading: boolean;
}) {
  const [reference, setReference] = useState("");

  // Resolve the single tenant id to a friendly name so the confirmation
  // dialog reads naturally ("Mark ₹15,000 from Asha Rao as paid").
  const userLookup = useUserLookup(target?.tenantId ? [target.tenantId] : []);

  return (
    <Dialog
      open={!!target}
      onOpenChange={(open) => {
        if (!open) {
          setReference("");
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Record cash payment</DialogTitle>
          <DialogDescription>
            {target ? (
              <>
                Mark{" "}
                <span className="font-semibold text-foreground">
                  {formatINR(target.totalAmount ?? target.amount)}
                </span>{" "}
                from{" "}
                <span className="font-medium text-foreground">
                  {userLookup.nameOf(target.tenantId)}
                </span>{" "}
                as paid in cash.
              </>
            ) : null}
          </DialogDescription>
        </DialogHeader>

        <div>
          <Label htmlFor="reference">Reference (optional)</Label>
          <Input
            id="reference"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            placeholder="Cheque #, receipt #, or note"
            className="mt-1.5"
            maxLength={100}
          />
          <p className="text-xs text-muted-foreground mt-1.5">
            We'll attach this to the receipt for your records.
          </p>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            onClick={() => onConfirm(reference.trim() || undefined)}
            disabled={loading}
          >
            {loading && <Loader2 className="animate-spin" />}
            Mark as paid
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Owner confirms a UPI / NEFT / IMPS payment received out-of-band.
 * Same shape as RecordCashDialog, different copy + a different
 * reference placeholder (the UPI reference number is the natural
 * input here, not a cheque number).
 */
function RecordUpiDialog({
  target,
  onClose,
  onConfirm,
  loading,
}: {
  target: PaymentResponse | null;
  onClose: () => void;
  onConfirm: (reference: string | undefined) => void;
  loading: boolean;
}) {
  const [reference, setReference] = useState("");
  const userLookup = useUserLookup(target?.tenantId ? [target.tenantId] : []);

  return (
    <Dialog
      open={!!target}
      onOpenChange={(open) => {
        if (!open) {
          setReference("");
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirm UPI / bank-transfer payment</DialogTitle>
          <DialogDescription>
            {target ? (
              <>
                Mark{" "}
                <span className="font-semibold text-foreground">
                  {formatINR(target.totalAmount ?? target.amount)}
                </span>{" "}
                from{" "}
                <span className="font-medium text-foreground">
                  {userLookup.nameOf(target.tenantId)}
                </span>{" "}
                as paid via UPI or bank transfer. Only confirm AFTER you
                see the money in your bank account or UPI app.
              </>
            ) : null}
          </DialogDescription>
        </DialogHeader>

        <div>
          <Label htmlFor="upi-reference">UPI / bank reference (optional)</Label>
          <Input
            id="upi-reference"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            placeholder="e.g. UPI Ref 412395123456 / IMPS 9876543"
            className="mt-1.5"
            maxLength={100}
          />
          <p className="text-xs text-muted-foreground mt-1.5">
            Copy the UPI reference number from your bank SMS or your UPI
            app's transaction history. We attach it to the receipt so
            both sides can audit later.
          </p>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            onClick={() => onConfirm(reference.trim() || undefined)}
            disabled={loading}
          >
            {loading && <Loader2 className="animate-spin" />}
            Confirm receipt
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function StatusBadge({ status }: { status: PaymentStatus }) {
  if (status === "PAID") return <Badge variant="success">Paid</Badge>;
  if (status === "PROCESSING") return <Badge variant="warning">Processing</Badge>;
  if (status === "OVERDUE") return <Badge variant="destructive">Overdue</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  if (status === "CANCELLED") return <Badge variant="secondary">Cancelled</Badge>;
  if (status === "REFUNDED") return <Badge variant="secondary">Refunded</Badge>;
  return <Badge variant="warning">Pending</Badge>;
}

/**
 * Trigger a browser download for a Blob fetched from the API.
 * Same shape as the lease/agreement download flow.
 */
async function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function ReceiptButton({ paymentId }: { paymentId: string }) {
  const [busy, setBusy] = useState(false);
  return (
    <Button
      size="sm"
      variant="ghost"
      title="Download receipt PDF"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        try {
          const blob = await paymentsApi.receiptPdf(paymentId);
          await downloadBlob(blob, `receipt-${paymentId.slice(0, 8)}.pdf`);
        } catch (e) {
          toast({
            variant: "destructive",
            title: "Couldn't download receipt",
            description: extractErrorMessage(e),
          });
        } finally {
          setBusy(false);
        }
      }}
    >
      {busy ? <Loader2 className="animate-spin" /> : <Download />}
      Receipt
    </Button>
  );
}

function InvoiceButton({ paymentId }: { paymentId: string }) {
  const [busy, setBusy] = useState(false);
  return (
    <Button
      size="sm"
      variant="ghost"
      title="Download invoice PDF"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        try {
          const blob = await paymentsApi.invoicePdf(paymentId);
          await downloadBlob(blob, `invoice-${paymentId.slice(0, 8)}.pdf`);
        } catch (e) {
          toast({
            variant: "destructive",
            title: "Couldn't download invoice",
            description: extractErrorMessage(e),
          });
        } finally {
          setBusy(false);
        }
      }}
    >
      {busy ? <Loader2 className="animate-spin" /> : <FileText />}
      Invoice
    </Button>
  );
}
