import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2,
  CheckCircle2,
  Download,
  FileText,
  Plus,
  Stamp,
} from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { complianceApi } from "@/lib/api/compliance";
import { propertiesApi } from "@/lib/api/properties";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
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
import { formatDate } from "@/lib/utils";
import type { ReraStatus } from "@/types/api";

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

export function OwnerCompliancePage() {
  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Compliance"
        description="RERA registration and GST invoicing — keep your rental income compliant."
      />

      <Tabs defaultValue="rera">
        <TabsList>
          <TabsTrigger value="rera">RERA</TabsTrigger>
          <TabsTrigger value="gst">GST invoices</TabsTrigger>
        </TabsList>

        <TabsContent value="rera" className="mt-4">
          <ReraTab />
        </TabsContent>
        <TabsContent value="gst" className="mt-4">
          <GstTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

/* ----------------- RERA tab ----------------- */

function ReraTab() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [registerOpen, setRegisterOpen] = useState(false);

  // List buildings for the owner; per-property RERA status comes via
  // /compliance/rera/status/{propertyId} for each.
  const buildingsQ = useQuery({
    queryKey: ["buildings-for-rera", authUserId],
    queryFn: () => propertiesApi.buildings.byOwner(authUserId!),
    enabled: !!authUserId,
  });

  const registerM = useMutation({
    mutationFn: (b: { propertyId: string; ownerId: string; state: string }) =>
      complianceApi.registerRera(b),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["rera-status", vars.propertyId] });
      setRegisterOpen(false);
      toast({ title: "RERA registration submitted" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't register",
        description: extractErrorMessage(e),
      }),
  });

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <div className="flex items-center justify-between flex-wrap gap-3 mb-5">
          <div>
            <h3 className="font-display font-semibold text-lg">
              RERA registrations
            </h3>
            <p className="text-sm text-muted-foreground mt-0.5">
              Each state runs its own RERA portal. Register every property in
              the state where it's located.
            </p>
          </div>
          <Button variant="gradient" onClick={() => setRegisterOpen(true)}>
            <Plus /> Register property
          </Button>
        </div>

        {buildingsQ.isLoading ? (
          <div className="space-y-3">
            {[1, 2].map((i) => (
              <Skeleton key={i} className="h-20 rounded-xl" />
            ))}
          </div>
        ) : !buildingsQ.data || buildingsQ.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            You haven't added any buildings yet — start there before registering with RERA.
          </p>
        ) : (
          <div className="space-y-3">
            {buildingsQ.data.map((b) => (
              <ReraBuildingRow key={b.buildingId} buildingId={b.buildingId} buildingName={b.buildingName} buildingAddress={b.buildingAddress} buildingState={b.buildingState} />
            ))}
          </div>
        )}
      </CardContent>

      <Dialog open={registerOpen} onOpenChange={setRegisterOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Register a property on RERA</DialogTitle>
            <DialogDescription>
              We'll submit the registration request to the state RERA portal
              and notify you when the number is issued.
            </DialogDescription>
          </DialogHeader>
          <form
            id="rera-register-form"
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              const fd = new FormData(e.currentTarget);
              registerM.mutate({
                propertyId: String(fd.get("propertyId") ?? ""),
                ownerId: authUserId ?? "",
                state: String(fd.get("state") ?? ""),
              });
            }}
          >
            <div>
              <label className="text-sm font-medium">Property</label>
              <Select name="propertyId">
                <SelectTrigger className="mt-1.5">
                  <SelectValue placeholder="Pick a building" />
                </SelectTrigger>
                <SelectContent>
                  {(buildingsQ.data ?? []).map((b) => (
                    <SelectItem key={b.buildingId} value={b.buildingId}>
                      {b.buildingName} — {b.buildingCity}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-sm font-medium">State</label>
              <Select name="state" defaultValue="KARNATAKA">
                <SelectTrigger className="mt-1.5">
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
            </div>
          </form>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setRegisterOpen(false)}>
              Cancel
            </Button>
            <Button
              form="rera-register-form"
              type="submit"
              variant="gradient"
              disabled={registerM.isPending}
            >
              {registerM.isPending ? "Submitting…" : "Submit"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function ReraBuildingRow({
  buildingId,
  buildingName,
  buildingAddress,
  buildingState,
}: {
  buildingId: string;
  buildingName: string;
  buildingAddress: string;
  buildingState: string;
}) {
  const q = useQuery({
    queryKey: ["rera-status", buildingId],
    queryFn: () => complianceApi.reraStatus(buildingId),
    retry: false,
  });
  const regs = q.data ?? [];
  const primary = regs[0];

  return (
    <div className="rounded-xl border bg-secondary/30 p-4 flex flex-wrap items-center gap-4">
      <div className="size-11 rounded-lg bg-primary/10 grid place-items-center shrink-0">
        <Building2 className="size-5 text-primary" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <p className="font-medium">{buildingName}</p>
          {primary ? (
            <ReraBadge status={primary.registrationStatus} />
          ) : (
            <Badge variant="secondary" className="text-[10px]">Not registered</Badge>
          )}
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">
          {buildingAddress} · {buildingState}
        </p>
        {primary?.reraRegistrationNumber && (
          <p className="text-xs mt-1.5 flex items-center gap-1.5">
            <Stamp className="size-3.5 text-emerald-500" />
            <span className="font-medium">{primary.reraRegistrationNumber}</span>
            {primary.expiryDate && (
              <span className="text-muted-foreground">
                · valid till {formatDate(primary.expiryDate)}
              </span>
            )}
          </p>
        )}
      </div>
    </div>
  );
}

function ReraBadge({ status }: { status: ReraStatus }) {
  if (status === "REGISTERED")
    return <Badge variant="success" className="text-[10px]"><CheckCircle2 className="size-3" /> Registered</Badge>;
  if (status === "EXPIRED")
    return <Badge variant="destructive" className="text-[10px]">Expired</Badge>;
  return <Badge variant="warning" className="text-[10px]">Pending</Badge>;
}

/* ----------------- GST tab ----------------- */

function GstTab() {
  const [searchId, setSearchId] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);

  const invoiceQ = useQuery({
    queryKey: ["gst-invoice", activeId],
    queryFn: () => complianceApi.getGstInvoice(activeId!),
    enabled: !!activeId,
    retry: false,
  });

  async function onDownload(id: string) {
    try {
      const blob = await complianceApi.downloadGstInvoicePdf(id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `gst-invoice-${id}.pdf`;
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
    <Card>
      <CardContent className="p-6 sm:p-8 space-y-5">
        <div>
          <h3 className="font-display font-semibold text-lg">GST invoices</h3>
          <p className="text-sm text-muted-foreground mt-0.5">
            We auto-generate a GST invoice for every settled rent payment.
            GST applies only when annualised rent exceeds ₹20 lakh.
          </p>
        </div>

        <form
          className="flex flex-wrap gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setActiveId(searchId || null);
          }}
        >
          <input
            type="text"
            value={searchId}
            onChange={(e) => setSearchId(e.target.value)}
            placeholder="Look up by invoice id…"
            className="flex h-10 rounded-lg border bg-background px-3 text-sm flex-1 min-w-[260px]"
          />
          <Button type="submit" variant="outline">
            Find
          </Button>
        </form>

        {invoiceQ.isLoading && <Skeleton className="h-32 rounded-xl" />}

        {invoiceQ.isError && activeId && (
          <p className="text-sm text-destructive">No invoice found for that id.</p>
        )}

        {invoiceQ.data && (
          <div className="rounded-xl border bg-background p-5">
            <div className="flex items-start gap-4">
              <div className="size-11 rounded-lg bg-primary/10 grid place-items-center shrink-0">
                <FileText className="size-5 text-primary" />
              </div>
              <div className="flex-1">
                <p className="font-medium">{invoiceQ.data.invoiceNumber}</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {formatDate(invoiceQ.data.invoiceDate)} · payment{" "}
                  <code>{invoiceQ.data.paymentId}</code>
                </p>
                <div className="grid sm:grid-cols-3 gap-3 mt-4 text-sm">
                  <KV label="Rent" value={`₹${invoiceQ.data.rentAmount}`} />
                  <KV
                    label={`GST${
                      invoiceQ.data.gstApplicable
                        ? ` @ ${invoiceQ.data.gstRatePercent}%`
                        : ""
                    }`}
                    value={
                      invoiceQ.data.gstApplicable
                        ? `₹${invoiceQ.data.gstAmount ?? 0}`
                        : "Not applicable"
                    }
                  />
                  <KV label="Total" value={`₹${invoiceQ.data.totalAmount}`} />
                </div>
              </div>
              <Button variant="outline" onClick={() => onDownload(invoiceQ.data!.id)}>
                <Download /> PDF
              </Button>
            </div>
          </div>
        )}

        <div className="rounded-xl border bg-amber-50/40 dark:bg-amber-500/5 border-amber-500/30 p-4 text-sm">
          <p>
            <strong>Tip:</strong> invoice ids are emitted on the{" "}
            <code>compliance-events</code> Kafka topic when a payment completes.
            Once you wire up the activity feed, the latest GST invoice will
            appear here automatically.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

function KV({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-secondary/30 p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium mt-0.5">{value}</p>
    </div>
  );
}
