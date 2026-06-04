import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  Copy,
  Loader2,
  Smartphone,
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { societyApi } from "@/lib/api/society";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/layout/page-header";
import { formatINR } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { extractErrorMessage } from "@/lib/api/client";
import type {
  FlatChargeCategory,
  FlatMaintenanceRow,
  SocietyConfig,
} from "@/types/api";

const CATEGORY_LABELS: Record<FlatChargeCategory, string> = {
  WATER_BILL: "Water bill",
  MAINTENANCE: "Maintenance",
  GAS_BILL: "Gas bill",
  ELECTRICITY: "Electricity",
  COMMON_AREA_SHARE: "Common-area share",
  OTHER: "Other",
};

/**
 * Dedicated payment page for one society charge. Reached from the
 * tenant's society page ("Pay" button on a DUE charge row) — replaces
 * the earlier in-page modal. URL shape mirrors the rent-pay route
 * (/app/payments/:id/pay) so the navigation pattern is familiar:
 * /app/society/pay/:buildingId/:collectionId.
 *
 * <p>The page shows:
 * <ol>
 *   <li>Charge details (category, month, amount, notes).</li>
 *   <li>A large centred UPI QR code targeting the society's
 *       collection account, plus a tap-to-pay link for mobile.</li>
 *   <li>Bank fallback details (UPI ID, payee, account number, IFSC)
 *       for tenants who'd rather add the account as a banking
 *       beneficiary.</li>
 *   <li>An "I've paid" form that records the tenant's UPI reference
 *       on the row's notes. The maintainer sees the self-reported
 *       payment on their dashboard, verifies it in their bank app,
 *       then flips the row to PAID.</li>
 * </ol>
 *
 * <p>No Razorpay integration yet — payments still settle into the
 * society's bank account via UPI / bank transfer, and the maintainer
 * confirms manually. That's the documented limitation.
 */
export function SocietyPayPage() {
  const { buildingId, collectionId } = useParams<{
    buildingId: string;
    collectionId: string;
  }>();
  const navigate = useNavigate();

  // The society config (for the UPI / bank fields).
  const configQ = useQuery({
    queryKey: ["tenant-society"],
    queryFn: () => societyApi.myTenant(),
  });

  // The tenant's bills for the current month — find the row matching
  // collectionId. We accept a small inefficiency here (we re-fetch the
  // whole month list rather than expose a single-row GET) because the
  // list is small and React Query has the response cached from the
  // /app/society page that linked here.
  const month = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  })();

  const billsQ = useQuery({
    queryKey: ["tenant-society-bills", buildingId, month],
    queryFn: () => societyApi.myBills(buildingId!, month),
    enabled: !!buildingId,
    staleTime: 15_000,
  });

  // Walk a few months back if the row isn't in the current month —
  // covers the case where the tenant clicks Pay on a row from a
  // past month they were viewing.
  const monthsQ = useQuery({
    queryKey: ["tenant-society-bills-recent", buildingId],
    queryFn: async () => {
      const months = lastNMonths(6);
      const all = await Promise.all(
        months.map((m) => societyApi.myBills(buildingId!, m)),
      );
      return all.flat();
    },
    enabled: !!buildingId,
    staleTime: 30_000,
  });

  const row = useMemo(() => {
    const candidates = [
      ...(billsQ.data ?? []),
      ...(monthsQ.data ?? []),
    ];
    return candidates.find((r) => r.collectionId === collectionId) ?? null;
  }, [billsQ.data, monthsQ.data, collectionId]);

  if (!buildingId || !collectionId) {
    return (
      <EmptyState
        variant="info"
        icon={Smartphone}
        title="Invalid link"
        description="No charge selected. Go back to the society page and pick a Pay button."
      />
    );
  }

  if (configQ.isLoading || billsQ.isLoading) {
    return (
      <div className="max-w-2xl space-y-4">
        <Skeleton className="h-12 rounded-lg" />
        <Skeleton className="h-72 rounded-2xl" />
      </div>
    );
  }

  if (!configQ.data) {
    return (
      <EmptyState
        variant="info"
        icon={Smartphone}
        title="Society not set up"
        description="The owner hasn't enabled society maintenance for your building."
      />
    );
  }

  if (!row) {
    return (
      <EmptyState
        variant="info"
        icon={Smartphone}
        title="Charge not found"
        description="This payment link may be stale. Refresh your society page and try again."
        action={
          <Button asChild variant="outline">
            <Link to="/app/society">← Back to society</Link>
          </Button>
        }
      />
    );
  }

  const cfg = configQ.data;
  const categoryLabel = row.category ? CATEGORY_LABELS[row.category] : "Other";
  const isPaid = row.status === "PAID";
  const canPayUpi = !!cfg.upiId;

  return (
    <div className="animate-fade-in max-w-2xl">
      <PageHeader
        title={`Pay ${formatINR(row.monthAmount)}`}
        description={`${categoryLabel} · Flat ${row.flatNumber} · ${row.forMonth}`}
        actions={
          <Button asChild variant="ghost" size="sm">
            <Link to="/app/society">
              <ArrowLeft className="size-4" /> Back to society
            </Link>
          </Button>
        }
      />

      {/* Summary card */}
      <Card className="mb-4">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <Badge variant="secondary" className="text-[10px] mb-2">
                {categoryLabel}
              </Badge>
              <p className="text-sm text-muted-foreground">
                Flat {row.flatNumber} · {row.forMonth}
              </p>
              {row.notes && (
                <p className="text-xs text-muted-foreground italic mt-2">
                  {row.notes}
                </p>
              )}
            </div>
            <p className="font-display font-bold text-2xl">
              {formatINR(row.monthAmount)}
            </p>
          </div>
        </CardContent>
      </Card>

      {isPaid ? (
        <Card>
          <CardContent className="p-6 text-center">
            <CheckCircle2 className="size-12 text-success mx-auto mb-3" />
            <h3 className="font-display font-semibold text-lg">
              Already paid
            </h3>
            <p className="text-sm text-muted-foreground mt-1">
              {row.paidOn
                ? `Marked paid on ${row.paidOn}.`
                : "The maintainer has marked this charge as paid."}
            </p>
            <Button asChild variant="outline" className="mt-4">
              <Link to="/app/society">Back to society</Link>
            </Button>
          </CardContent>
        </Card>
      ) : canPayUpi ? (
        <UpiPaySection
          cfg={cfg}
          row={row}
          buildingId={buildingId}
          categoryLabel={categoryLabel}
          onMarked={() => navigate("/app/society")}
        />
      ) : (
        <EmptyState
          variant="info"
          icon={Smartphone}
          title="Payment account not configured"
          description="The maintainer hasn't set up a UPI ID for collections yet. Ask them to fill in the Common bank account on their society page so this button starts working."
        />
      )}
    </div>
  );
}

function UpiPaySection({
  cfg,
  row,
  buildingId,
  onMarked,
}: {
  cfg: SocietyConfig;
  row: FlatMaintenanceRow;
  buildingId: string;
  /** `categoryLabel` is rendered upstream — we keep it out of this
   *  component's prop surface so the QR section stays focused on the
   *  scan / bank / "I've paid" flow. */
  categoryLabel?: string;
  onMarked: () => void;
}) {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [reference, setReference] = useState("");

  const txnNote = `${row.category ?? "Maintenance"} ${row.forMonth} Flat ${row.flatNumber}`;
  const upiUri =
    `upi://pay?pa=${encodeURIComponent(cfg.upiId ?? "")}` +
    `&pn=${encodeURIComponent(cfg.payeeName ?? cfg.societyDisplayName ?? "Society")}` +
    `&am=${encodeURIComponent(String(row.monthAmount))}` +
    `&cu=INR` +
    `&tn=${encodeURIComponent(txnNote)}`;

  const copyUpi = () => {
    navigator.clipboard.writeText(cfg.upiId ?? "");
    toast({ title: "UPI ID copied" });
  };

  // "I've paid" → patches the collection row's notes with the
  // tenant-reported UPI reference. The row stays in its current
  // status (DUE) until the maintainer manually verifies; this is
  // intentional — we don't want tenants self-marking PAID without
  // verification.
  const markMut = useMutation({
    mutationFn: () => {
      const existingNotes = row.notes ?? "";
      const stamp = new Date().toISOString().slice(0, 16).replace("T", " ");
      const tag = `[self-reported ${stamp}] tenant paid via UPI, ref: ${reference || "(none provided)"}`;
      const merged = existingNotes
        ? `${existingNotes}\n${tag}`
        : tag;
      return societyApi.upsertFlatCollection(buildingId, row.flatId, {
        forMonth: row.forMonth,
        amountDue: row.monthAmount,
        category: row.category ?? "MAINTENANCE",
        // Don't change status — the maintainer verifies before
        // flipping PAID. We only update notes to surface the
        // tenant's reference number on the maintainer dashboard.
        notes: merged,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["tenant-society-bills"] });
      toast({
        title: "Thanks — we'll notify the maintainer",
        description:
          "Your reference number is now visible on their dashboard. They'll mark this charge paid after verifying receipt in their bank app.",
      });
      onMarked();
    },
    onError: (err) =>
      toast({
        title: "Couldn't record payment",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  return (
    <>
      {/* QR */}
      <Card className="mb-4">
        <CardContent className="p-6 text-center">
          <p className="text-xs uppercase tracking-wider text-muted-foreground mb-3">
            Scan to pay {formatINR(row.monthAmount)}
          </p>
          <div className="inline-block rounded-xl border-2 border-border/60 bg-white p-4">
            <QRCodeSVG value={upiUri} size={220} includeMargin={false} />
          </div>
          <p className="text-xs text-muted-foreground mt-3">
            Scan with any UPI app — GPay, PhonePe, Paytm, BHIM.
          </p>
          <a
            href={upiUri}
            className="inline-block mt-2 text-sm text-primary underline underline-offset-2"
          >
            Or tap here to open your UPI app
          </a>
        </CardContent>
      </Card>

      {/* Bank fallback */}
      <Card className="mb-4">
        <CardContent className="p-5">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
            Bank transfer (alternative)
          </h4>
          <div className="space-y-2 text-sm">
            <Row label="UPI ID" value={cfg.upiId!} mono>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-6 px-2"
                onClick={copyUpi}
              >
                <Copy className="size-3.5" />
              </Button>
            </Row>
            <Row label="Payee" value={cfg.payeeName ?? "—"} />
            {cfg.accountNumber && (
              <Row label="A/c no." value={cfg.accountNumber} mono />
            )}
            {cfg.ifscCode && <Row label="IFSC" value={cfg.ifscCode} mono />}
            <Row
              label="Reference"
              value={txnNote}
              note="Use this in your payment note so the maintainer can match the transfer."
            />
          </div>
        </CardContent>
      </Card>

      {/* I've paid */}
      <Card>
        <CardContent className="p-5">
          <h4 className="text-sm font-semibold mb-2">After you've paid</h4>
          <p className="text-xs text-muted-foreground mb-3">
            Drop your UPI transaction reference here so the maintainer can
            find the payment in their bank statement. The charge will be
            marked PAID by the maintainer once they verify receipt.
          </p>
          <Label htmlFor="ref">UPI reference / transaction ID</Label>
          <Input
            id="ref"
            placeholder="optional — e.g. 419823456712"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            className="mt-1.5"
          />
          <Button
            variant="gradient"
            className="mt-3 w-full"
            disabled={markMut.isPending}
            onClick={() => markMut.mutate()}
          >
            {markMut.isPending ? (
              <>
                <Loader2 className="size-4 animate-spin" /> Sending…
              </>
            ) : (
              "I've paid — notify maintainer"
            )}
          </Button>
        </CardContent>
      </Card>
    </>
  );
}

function Row({
  label,
  value,
  mono,
  note,
  children,
}: {
  label: string;
  value: string;
  mono?: boolean;
  note?: string;
  children?: React.ReactNode;
}) {
  return (
    <div>
      <div className="flex items-center gap-2">
        <span className="text-[10px] uppercase tracking-wider text-muted-foreground w-20 shrink-0">
          {label}
        </span>
        <span
          className={`${mono ? "font-mono text-xs" : "text-sm"} flex-1 truncate`}
          title={value}
        >
          {value}
        </span>
        {children}
      </div>
      {note && (
        <p className="text-[10px] text-muted-foreground mt-0.5 ml-22">{note}</p>
      )}
    </div>
  );
}

function lastNMonths(n: number): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 0; i < n; i++) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}
