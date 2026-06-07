import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Download } from "lucide-react";
import { paymentsApi } from "@/lib/api/payments";
import { authApi } from "@/lib/api/auth";
import { propertiesApi } from "@/lib/api/properties";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { PaymentDetailDialog } from "@/components/admin/payment-detail-dialog";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse, PaymentStatus } from "@/types/api";

export function AdminPaymentsPage() {
  const [q, setQ] = useState("");
  // Selected payment → drives the detail dialog. Lifted to the page so
  // clicking a row in any of the four tabs (All / Overdue / Pending /
  // Paid) reuses the same dialog mount + React Query cache.
  const [selectedPayment, setSelectedPayment] = useState<PaymentResponse | null>(null);
  const [paymentDialogOpen, setPaymentDialogOpen] = useState(false);
  const openPayment = (p: PaymentResponse) => {
    setSelectedPayment(p);
    setPaymentDialogOpen(true);
  };

  const pageQ = useQuery({
    queryKey: ["admin", "payments"],
    queryFn: () => paymentsApi.list(0, 200),
  });

  // Side queries to translate the raw IDs the payments API returns
  // into human-readable labels. Without these the Admin Payments
  // table shows things like "Flat #FLT-47423fd3-04ed-... / Tenant 1
  // / Owner 2" which is uselessly opaque on a demo screen.
  // Cached separately so a payment-status refresh doesn't refetch
  // the users + flats lists.
  const tenantsQ = useQuery({
    queryKey: ["admin", "users-tenant"],
    queryFn: () => authApi.byRole("TENANT"),
    staleTime: 60_000,
  });
  const ownersQ = useQuery({
    queryKey: ["admin", "users-owner"],
    queryFn: () => authApi.byRole("OWNER"),
    staleTime: 60_000,
  });
  const flatsQ = useQuery({
    queryKey: ["admin", "all-flats"],
    queryFn: () => propertiesApi.flats.list(0, 500),
    staleTime: 60_000,
  });

  // Build O(1) lookup maps from auth-user-id → display name and
  // flat-id → "#flatNumber (buildingName)". Resilient to partial
  // data: if a user or flat row is missing we fall back to the
  // raw id so the column never goes blank.
  // AuthUserResponse from auth-service only carries (id, userName,
  // userRole, email, recordCreatedDate, recodeUpdatedDate) — first/
  // last name live in user-service profiles. Use userName as the
  // display label; it's already a human-readable identifier
  // (owner_alice, tenant_dana, etc.) and avoids the extra
  // user-service round-trip.
  const tenantLookup = useMemo(() => {
    const m = new Map<string, string>();
    for (const u of tenantsQ.data ?? []) m.set(String(u.id), u.userName);
    return m;
  }, [tenantsQ.data]);

  const ownerLookup = useMemo(() => {
    const m = new Map<string, string>();
    for (const u of ownersQ.data ?? []) m.set(String(u.id), u.userName);
    return m;
  }, [ownersQ.data]);

  const flatLookup = useMemo(() => {
    const m = new Map<string, { flatNumber: string; buildingName?: string }>();
    for (const f of flatsQ.data?.content ?? []) {
      m.set(String(f.id), {
        flatNumber: f.flatNumber,
        buildingName: f.buildingName,
      });
    }
    return m;
  }, [flatsQ.data]);

  const all = pageQ.data?.content ?? [];

  const filtered = useMemo(() => {
    if (!q) return all;
    const n = q.toLowerCase();
    return all.filter((p) => {
      // Match on the human labels first (what the user actually
      // sees on the row) and fall back to the raw IDs / txnId so
      // power users can paste a flat id directly into search.
      const tenantName = tenantLookup.get(String(p.tenantId)) ?? "";
      const ownerName = ownerLookup.get(String(p.ownerId)) ?? "";
      const flat = flatLookup.get(String(p.flatId));
      return (
        tenantName.toLowerCase().includes(n) ||
        ownerName.toLowerCase().includes(n) ||
        p.tenantId?.toLowerCase().includes(n) ||
        p.ownerId?.toLowerCase().includes(n) ||
        flat?.flatNumber.toLowerCase().includes(n) ||
        flat?.buildingName?.toLowerCase().includes(n) ||
        String(p.flatId).toLowerCase().includes(n) ||
        p.transactionId?.toLowerCase().includes(n)
      );
    });
  }, [all, q, tenantLookup, ownerLookup, flatLookup]);

  const overdue = filtered.filter((p) => p.status === "OVERDUE");
  const pending = filtered.filter((p) => p.status === "PENDING");
  const paid = filtered.filter((p) => p.status === "PAID");

  const stats = useMemo(() => {
    const collected = all
      .filter((p) => p.status === "PAID")
      .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);
    const outstanding = all
      .filter((p) => p.status === "PENDING" || p.status === "OVERDUE")
      .reduce((s, p) => s + Number(p.totalAmount ?? p.amount), 0);
    const failed = all.filter((p) => p.status === "FAILED").length;
    return { collected, outstanding, failed };
  }, [all]);

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Platform payments"
        description="Every transaction across every owner."
        actions={
          <Button variant="outline">
            <Download /> Export CSV
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3 mb-6">
        <Stat label="Collected (lifetime)" value={formatINR(stats.collected)} tone="success" />
        <Stat label="Total Dues" value={formatINR(stats.outstanding)} tone="warning" />
        <Stat label="Failed transactions" value={String(stats.failed)} tone="destructive" />
      </div>

      <Card className="p-3 mb-5">
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input
            placeholder="Search by tenant, owner, flat, txn id…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="pl-10"
          />
        </div>
      </Card>

      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All ({filtered.length})</TabsTrigger>
          <TabsTrigger value="overdue">Overdue ({overdue.length})</TabsTrigger>
          <TabsTrigger value="pending">Pending ({pending.length})</TabsTrigger>
          <TabsTrigger value="paid">Paid ({paid.length})</TabsTrigger>
        </TabsList>
        <TabsContent value="all">
          <Table loading={pageQ.isLoading} payments={filtered} tenantLookup={tenantLookup} ownerLookup={ownerLookup} flatLookup={flatLookup} onOpen={openPayment} />
        </TabsContent>
        <TabsContent value="overdue">
          <Table loading={pageQ.isLoading} payments={overdue} tenantLookup={tenantLookup} ownerLookup={ownerLookup} flatLookup={flatLookup} onOpen={openPayment} />
        </TabsContent>
        <TabsContent value="pending">
          <Table loading={pageQ.isLoading} payments={pending} tenantLookup={tenantLookup} ownerLookup={ownerLookup} flatLookup={flatLookup} onOpen={openPayment} />
        </TabsContent>
        <TabsContent value="paid">
          <Table loading={pageQ.isLoading} payments={paid} tenantLookup={tenantLookup} ownerLookup={ownerLookup} flatLookup={flatLookup} onOpen={openPayment} />
        </TabsContent>
      </Tabs>

      <PaymentDetailDialog
        payment={selectedPayment}
        tenantName={
          selectedPayment
            ? tenantLookup.get(String(selectedPayment.tenantId))
            : undefined
        }
        ownerName={
          selectedPayment
            ? ownerLookup.get(String(selectedPayment.ownerId))
            : undefined
        }
        flatLabel={(() => {
          if (!selectedPayment) return undefined;
          const f = flatLookup.get(String(selectedPayment.flatId));
          if (!f) return undefined;
          return f.buildingName
            ? `#${f.flatNumber} · ${f.buildingName}`
            : `#${f.flatNumber}`;
        })()}
        open={paymentDialogOpen}
        onOpenChange={setPaymentDialogOpen}
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
  tenantLookup,
  ownerLookup,
  flatLookup,
  onOpen,
}: {
  loading?: boolean;
  payments: PaymentResponse[];
  // Lookups passed down from AdminPaymentsPage so the table can show
  // human-readable names instead of raw IDs. Default to empty Maps so
  // older call-sites that haven't been updated still type-check (the
  // cells then fall back to the raw id, same as if the lookup missed).
  tenantLookup?: Map<string, string>;
  ownerLookup?: Map<string, string>;
  flatLookup?: Map<string, { flatNumber: string; buildingName?: string }>;
  /** Click handler — opens the payment detail dialog with this row. */
  onOpen?: (p: PaymentResponse) => void;
}) {
  const tLookup = tenantLookup ?? new Map();
  const oLookup = ownerLookup ?? new Map();
  const fLookup = flatLookup ?? new Map();
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
        No payments here.
      </Card>
    );
  }
  return (
    <Card>
      <div className="hidden sm:grid grid-cols-[100px_1.4fr_1.4fr_120px_120px_100px_100px] gap-3 px-5 py-3 text-xs uppercase tracking-wider text-muted-foreground border-b">
        <span>Flat</span>
        <span>Tenant</span>
        <span>Owner</span>
        <span>Due</span>
        <span>Amount</span>
        <span>Method</span>
        <span>Status</span>
      </div>
      <div className="divide-y">
        {payments.map((p) => (
          // Each row is the click target — opens the payment detail
          // dialog. Hover wash + focus ring give clear affordance for
          // mouse and keyboard users alike. Whole-row click means
          // admins can scan to a row and tap anywhere on it instead
          // of hunting for a small "View" button.
          <button
            type="button"
            key={p.id}
            onClick={() => onOpen?.(p)}
            className="w-full text-left grid grid-cols-2 sm:grid-cols-[100px_1.4fr_1.4fr_120px_120px_100px_100px] gap-3 px-5 py-3.5 text-sm items-center cursor-pointer transition-colors hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:bg-primary/5"
          >
            {/* Flat: prefer "#<number> · <building>" via lookup; fall
                back to the raw flatId when the flats list hasn't
                resolved yet (or this payment references a deleted
                flat). Same fallback pattern below for tenant/owner. */}
            <span className="truncate" title={p.flatId}>
              {(() => {
                const f = fLookup.get(String(p.flatId));
                if (!f) return <span className="font-mono text-xs">#{p.flatId}</span>;
                return (
                  <span>
                    <span className="font-medium">#{f.flatNumber}</span>
                    {f.buildingName && (
                      <span className="text-muted-foreground"> · {f.buildingName}</span>
                    )}
                  </span>
                );
              })()}
            </span>
            <span className="truncate text-muted-foreground hidden sm:block" title={p.tenantId}>
              {tLookup.get(String(p.tenantId)) ?? p.tenantId}
            </span>
            <span className="truncate text-muted-foreground hidden sm:block" title={p.ownerId}>
              {oLookup.get(String(p.ownerId)) ?? p.ownerId}
            </span>
            <span className="text-muted-foreground hidden sm:block">
              {formatDate(p.dueDate)}
            </span>
            <span className="font-medium">
              {formatINR(p.totalAmount ?? p.amount)}
            </span>
            <span className="text-muted-foreground capitalize hidden sm:block">
              {p.paymentMethod?.toLowerCase().replace("_", " ") ?? "—"}
            </span>
            <StatusBadge status={p.status} />
          </button>
        ))}
      </div>
    </Card>
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
