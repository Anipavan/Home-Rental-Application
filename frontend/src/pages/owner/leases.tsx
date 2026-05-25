import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ScrollText,
  Plus,
  Download,
  RefreshCw,
  XCircle,
  CheckCircle2,
  Stamp,
  AlertTriangle,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { leaseApi } from "@/lib/api/lease";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { formatDate, formatINR } from "@/lib/utils";
import type {
  CreateLeaseRequest,
  LeaseResponse,
  LeaseStatus,
} from "@/types/api";

const INDIA_STATES = [
  "KARNATAKA",
  "MAHARASHTRA",
  "TAMIL_NADU",
  "TELANGANA",
  "DELHI",
  "GUJARAT",
  "WEST_BENGAL",
  "UTTAR_PRADESH",
  "RAJASTHAN",
  "KERALA",
];

export function OwnerLeasesPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [activeId, setActiveId] = useState<string | null>(null);

  // Pull all flats for the owner so we can resolve which lease belongs to which flat
  // and offer a dropdown when creating a lease. Properties Service exposes
  // buildings-by-owner + flats-by-building, so we chain them.
  const flatsQ = useQuery({
    queryKey: ["owner-flats", authUserId],
    queryFn: async () => {
      const buildings = await propertiesApi.buildings.byOwner(authUserId!);
      const lists = await Promise.all(
        buildings.map((b) => propertiesApi.flats.byBuilding(b.buildingId)),
      );
      return lists.flat();
    },
    enabled: !!authUserId,
  });

  // Fetch leases per-flat and flatten. Lease Service doesn't expose a
  // by-owner endpoint yet, so we query per-flat.
  const flatIds = flatsQ.data?.map((f) => f.id) ?? [];
  const leasesQ = useQuery({
    queryKey: ["owner-leases", flatIds.join(",")],
    queryFn: async () => {
      const all = await Promise.all(flatIds.map((id) => leaseApi.byFlat(id)));
      return all.flat();
    },
    enabled: flatIds.length > 0,
  });

  const expiringQ = useQuery({
    queryKey: ["owner-leases-expiring"],
    queryFn: () => leaseApi.expiring(60),
  });

  const leases = leasesQ.data ?? [];
  const expiring = expiringQ.data ?? [];
  const activeLease = useMemo(
    () => leases.find((l) => l.id === activeId) ?? null,
    [leases, activeId],
  );

  const createM = useMutation({
    mutationFn: (b: CreateLeaseRequest) => leaseApi.create(b),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-leases"] });
      setCreateOpen(false);
      toast({ title: "Lease created" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't create lease",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Leases"
        description="Create, sign, and renew lease agreements with RERA-compliant deeds."
        actions={
          <Button variant="gradient" onClick={() => setCreateOpen(true)}>
            <Plus /> New lease
          </Button>
        }
      />

      {expiring.length > 0 && (
        <Card className="mb-6 border-amber-500/40 bg-amber-50/40 dark:bg-amber-500/5">
          <CardContent className="p-4 sm:p-5 flex items-start gap-3">
            <AlertTriangle className="size-5 text-amber-600 mt-0.5 shrink-0" />
            <div className="flex-1">
              <p className="font-medium">
                {expiring.length} lease{expiring.length === 1 ? "" : "s"} expiring within 60 days
              </p>
              <p className="text-sm text-muted-foreground mt-0.5">
                We've notified the tenant{expiring.length === 1 ? "" : "s"} via email. Click into a lease to renew.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg">
            Your leases{leasesQ.data ? ` (${leasesQ.data.length})` : ""}
          </h3>

          {leasesQ.isLoading || flatsQ.isLoading ? (
            <div className="mt-4 space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-20 rounded-xl" />
              ))}
            </div>
          ) : leases.length === 0 ? (
            <div className="mt-4">
              <EmptyState
                variant="info"
                icon={ScrollText}
                title="No leases yet"
                description='Click "New lease" above to draft one for any of your flats. We&apos;ll generate an e-signable agreement and a printable PDF.'
              />
            </div>
          ) : (
            <div className="mt-4 space-y-3">
              {leases.map((l) => (
                <button
                  key={l.id}
                  onClick={() => setActiveId(l.id)}
                  className="w-full text-left rounded-xl border bg-secondary/30 p-4 flex flex-wrap items-center gap-4 hover:bg-secondary/60 transition-colors"
                >
                  <div className="size-11 rounded-lg bg-primary/10 grid place-items-center shrink-0">
                    <ScrollText className="size-5 text-primary" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="font-medium">{l.leaseNumber}</p>
                      <LeaseStatusBadge status={l.status} />
                      {l.reraAgreementNumber && (
                        <Badge variant="success" className="text-[10px]">
                          <Stamp className="size-3" /> RERA
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {formatDate(l.startDate)} → {formatDate(l.endDate)} ·{" "}
                      {formatINR(l.rentAmount)}/mo
                    </p>
                  </div>
                </button>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <NewLeaseDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        flats={flatsQ.data ?? []}
        ownerId={authUserId ?? ""}
        pending={createM.isPending}
        onSubmit={(b) => createM.mutate(b)}
      />

      {activeLease && (
        <LeaseDetailDialog
          lease={activeLease}
          onClose={() => setActiveId(null)}
        />
      )}
    </div>
  );
}

/* ---------- subcomponents ---------- */

function LeaseStatusBadge({ status }: { status: LeaseStatus }) {
  switch (status) {
    case "ACTIVE":
      return <Badge variant="success">Active</Badge>;
    case "EXPIRED":
      return <Badge variant="warning">Expired</Badge>;
    case "TERMINATED":
      return <Badge variant="destructive">Terminated</Badge>;
    default:
      return <Badge variant="secondary">Draft</Badge>;
  }
}

function NewLeaseDialog({
  open,
  onOpenChange,
  flats,
  ownerId,
  pending,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  flats: { id: string; flatNumber: string; tenantId?: string; rentAmount: number }[];
  ownerId: string;
  pending: boolean;
  onSubmit: (b: CreateLeaseRequest) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New lease</DialogTitle>
          <DialogDescription>
            Drafts a lease and (when state is set) requests a RERA stamp from
            Compliance Service.
          </DialogDescription>
        </DialogHeader>
        <form
          id="new-lease-form"
          onSubmit={(e) => {
            e.preventDefault();
            const fd = new FormData(e.currentTarget);
            const flatId = String(fd.get("flatId") ?? "");
            const flat = flats.find((f) => f.id === flatId);
            onSubmit({
              tenantId: String(fd.get("tenantId") ?? ""),
              flatId,
              ownerId,
              startDate: String(fd.get("startDate") ?? ""),
              endDate: String(fd.get("endDate") ?? ""),
              rentAmount: Number(fd.get("rentAmount") ?? 0),
              securityDeposit: Number(fd.get("securityDeposit") ?? 0) || undefined,
              rentIncrementPercent: Number(fd.get("incPct") ?? 0) || undefined,
              state: String(fd.get("state") ?? "") || undefined,
            });
            void flat;
          }}
          className="space-y-3"
        >
          <div>
            <Label htmlFor="flatId">Flat</Label>
            <Select name="flatId">
              <SelectTrigger className="mt-1.5">
                <SelectValue placeholder="Pick a flat" />
              </SelectTrigger>
              <SelectContent>
                {flats.map((f) => (
                  <SelectItem key={f.id} value={f.id}>
                    {f.flatNumber} — {formatINR(f.rentAmount)}/mo
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label htmlFor="tenantId">Tenant ID</Label>
            <Input id="tenantId" name="tenantId" required className="mt-1.5" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="startDate">Start date</Label>
              <Input
                id="startDate"
                name="startDate"
                type="date"
                required
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="endDate">End date</Label>
              <Input
                id="endDate"
                name="endDate"
                type="date"
                required
                className="mt-1.5"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="rentAmount">Monthly rent (₹)</Label>
              <Input
                id="rentAmount"
                name="rentAmount"
                type="number"
                step="0.01"
                required
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="securityDeposit">Deposit (₹)</Label>
              <Input
                id="securityDeposit"
                name="securityDeposit"
                type="number"
                step="0.01"
                className="mt-1.5"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="incPct">Annual rent increment (%)</Label>
              <Input
                id="incPct"
                name="incPct"
                type="number"
                step="0.01"
                defaultValue={5}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="state">State (for RERA stamp)</Label>
              <Select name="state">
                <SelectTrigger className="mt-1.5">
                  <SelectValue placeholder="Optional" />
                </SelectTrigger>
                <SelectContent>
                  {INDIA_STATES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
            Cancel
          </Button>
          <Button
            form="new-lease-form"
            type="submit"
            variant="gradient"
            disabled={pending}
          >
            {pending ? "Creating…" : "Create lease"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function LeaseDetailDialog({
  lease,
  onClose,
}: {
  lease: LeaseResponse;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const { authUserId } = useAuthStore();

  const signM = useMutation({
    mutationFn: () =>
      leaseApi.sign(lease.id, {
        signatureProvider: "MOCK",
        signedBy: authUserId ?? "owner",
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-leases"] });
      toast({ title: "Lease signed", description: "Status flipped to ACTIVE." });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Sign failed",
        description: extractErrorMessage(e),
      }),
  });

  const terminateM = useMutation({
    mutationFn: (reason: string) =>
      leaseApi.terminate(lease.id, { terminationReason: reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-leases"] });
      toast({ title: "Lease terminated" });
      onClose();
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Terminate failed",
        description: extractErrorMessage(e),
      }),
  });

  const renewM = useMutation({
    mutationFn: (b: { newEndDate: string; newRent?: number }) =>
      leaseApi.renew(lease.id, b),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-leases"] });
      toast({ title: "Lease renewed" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Renew failed",
        description: extractErrorMessage(e),
      }),
  });

  const reraM = useMutation({
    mutationFn: (state: string) => leaseApi.generateRera(lease.id, state),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["owner-leases"] });
      toast({ title: "RERA-stamped deed generated" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "RERA stamp failed",
        description: extractErrorMessage(e),
      }),
  });

  async function onDownload() {
    try {
      const blob = await leaseApi.downloadDocument(lease.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lease-${lease.leaseNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Download failed",
        description: extractErrorMessage(e),
      });
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {lease.leaseNumber}
            <LeaseStatusBadge status={lease.status} />
          </DialogTitle>
          <DialogDescription>
            {formatDate(lease.startDate)} → {formatDate(lease.endDate)} ·{" "}
            {formatINR(lease.rentAmount)}/month
          </DialogDescription>
        </DialogHeader>

        <div className="grid sm:grid-cols-3 gap-3 text-sm">
          <KV label="Tenant" value={lease.tenantId} />
          <KV label="Flat" value={lease.flatId} />
          <KV
            label="Deposit"
            value={lease.securityDeposit ? formatINR(lease.securityDeposit) : "—"}
          />
          <KV
            label="Rent increment"
            value={lease.rentIncrementPercent ? `${lease.rentIncrementPercent}%` : "—"}
          />
          <KV
            label="Signature"
            value={lease.digitalSignatureStatus ?? "PENDING"}
          />
          <KV label="RERA" value={lease.reraAgreementNumber ?? "Not stamped"} />
        </div>

        <div className="grid sm:grid-cols-2 gap-2 mt-4">
          {lease.status === "DRAFT" && (
            <Button
              variant="gradient"
              onClick={() => signM.mutate()}
              disabled={signM.isPending}
            >
              <CheckCircle2 /> Sign &amp; activate
            </Button>
          )}
          {lease.status !== "TERMINATED" && (
            <RenewButton
              currentEnd={lease.endDate}
              currentRent={lease.rentAmount}
              onSubmit={(b) => renewM.mutate(b)}
              disabled={renewM.isPending}
            />
          )}
          {lease.status !== "TERMINATED" && (
            <Button
              variant="outline"
              onClick={() => terminateM.mutate("EARLY_TERMINATION")}
              disabled={terminateM.isPending}
            >
              <XCircle /> Terminate
            </Button>
          )}
          <ReraButton
            disabled={reraM.isPending}
            onSubmit={(state) => reraM.mutate(state)}
          />
          {/* Always offer the deed download. Lease Service renders the
              PDF on-demand if no documentUrl is set yet. */}
          <Button variant="ghost" onClick={onDownload}>
            <Download /> Deed PDF
          </Button>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RenewButton({
  currentEnd,
  currentRent,
  onSubmit,
  disabled,
}: {
  currentEnd: string;
  currentRent: number;
  onSubmit: (b: { newEndDate: string; newRent?: number }) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  // Default to extending by 1 year.
  const default1y = useMemo(() => {
    const d = new Date(currentEnd);
    d.setFullYear(d.getFullYear() + 1);
    return d.toISOString().slice(0, 10);
  }, [currentEnd]);
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)} disabled={disabled}>
        <RefreshCw /> Renew
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Renew lease</DialogTitle>
            <DialogDescription>
              Pick the new end date. Rent stays the same unless you change it.
            </DialogDescription>
          </DialogHeader>
          <form
            id="renew-form"
            onSubmit={(e) => {
              e.preventDefault();
              const fd = new FormData(e.currentTarget);
              onSubmit({
                newEndDate: String(fd.get("newEndDate") ?? ""),
                newRent: Number(fd.get("newRent") ?? 0) || undefined,
              });
              setOpen(false);
            }}
            className="space-y-3"
          >
            <div>
              <Label htmlFor="newEndDate">New end date</Label>
              <Input
                id="newEndDate"
                name="newEndDate"
                type="date"
                required
                defaultValue={default1y}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="newRent">New rent (optional)</Label>
              <Input
                id="newRent"
                name="newRent"
                type="number"
                step="0.01"
                placeholder={String(currentRent)}
                className="mt-1.5"
              />
            </div>
          </form>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button form="renew-form" type="submit" variant="gradient">
              Renew
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function ReraButton({
  onSubmit,
  disabled,
}: {
  onSubmit: (state: string) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [state, setState] = useState("KARNATAKA");
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)} disabled={disabled}>
        <Stamp /> RERA-stamp
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Generate RERA-stamped deed</DialogTitle>
            <DialogDescription>
              Compliance Service will fetch the registration number for this
              property in the chosen state and embed it in a fresh PDF.
            </DialogDescription>
          </DialogHeader>
          <Select value={state} onValueChange={setState}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {INDIA_STATES.map((s) => (
                <SelectItem key={s} value={s}>
                  {s}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="gradient"
              onClick={() => {
                onSubmit(state);
                setOpen(false);
              }}
            >
              Generate deed
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function KV({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border bg-secondary/30 p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium text-sm mt-0.5 truncate">{value}</p>
    </div>
  );
}
