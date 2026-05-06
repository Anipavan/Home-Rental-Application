import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, Download } from "lucide-react";
import { paymentsApi } from "@/lib/api/payments";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { formatINR, formatDate } from "@/lib/utils";
import type { PaymentResponse, PaymentStatus } from "@/types/api";

export function AdminPaymentsPage() {
  const [q, setQ] = useState("");

  const pageQ = useQuery({
    queryKey: ["admin", "payments"],
    queryFn: () => paymentsApi.list(0, 200),
  });

  const all = pageQ.data?.content ?? [];

  const filtered = useMemo(() => {
    if (!q) return all;
    const n = q.toLowerCase();
    return all.filter(
      (p) =>
        p.tenantId?.toLowerCase().includes(n) ||
        p.ownerId?.toLowerCase().includes(n) ||
        String(p.flatId).includes(q) ||
        p.transactionId?.toLowerCase().includes(n),
    );
  }, [all, q]);

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
        <Stat label="Outstanding" value={formatINR(stats.outstanding)} tone="warning" />
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
          <Table loading={pageQ.isLoading} payments={filtered} />
        </TabsContent>
        <TabsContent value="overdue">
          <Table loading={pageQ.isLoading} payments={overdue} />
        </TabsContent>
        <TabsContent value="pending">
          <Table loading={pageQ.isLoading} payments={pending} />
        </TabsContent>
        <TabsContent value="paid">
          <Table loading={pageQ.isLoading} payments={paid} />
        </TabsContent>
      </Tabs>
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
}: {
  loading?: boolean;
  payments: PaymentResponse[];
}) {
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
          <div
            key={p.id}
            className="grid grid-cols-2 sm:grid-cols-[100px_1.4fr_1.4fr_120px_120px_100px_100px] gap-3 px-5 py-3.5 text-sm items-center"
          >
            <span className="font-mono">#{p.flatId}</span>
            <span className="truncate text-muted-foreground hidden sm:block">
              {p.tenantId}
            </span>
            <span className="truncate text-muted-foreground hidden sm:block">
              {p.ownerId}
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
          </div>
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
