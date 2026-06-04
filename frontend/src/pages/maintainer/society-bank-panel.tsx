import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Banknote, Pencil, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { societyApi } from "@/lib/api/society";
import { extractErrorMessage } from "@/lib/api/client";
import type { SocietyConfig } from "@/types/api";

/**
 * "Common bank account" panel used on the maintainer flats page AND
 * on the owner society page. Shows whatever's currently configured
 * for the society's collection account (UPI ID + payee name + account
 * number + IFSC) and lets owner OR maintainer edit it via a dialog.
 *
 * <p>The actual write goes through {@code PUT /society/{buildingId}}
 * (existing updateConfig endpoint) — V5 added the four nullable
 * fields, the backend service treats absent fields as "no change".
 *
 * <p>Without any of these fields set, the tenant Pay-Now button on
 * /app/society stays hidden (no UPI → no QR target). The display in
 * the panel reflects that — operators see a clear "Set up to enable
 * Pay" empty state and a primary CTA.
 */
export function SocietyBankPanel({
  buildingId,
  config,
}: {
  buildingId: string;
  config: SocietyConfig;
}) {
  const hasUpi = !!config.upiId;
  return (
    <Card className="mb-6">
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3 mb-3">
          <div className="flex items-center gap-2">
            <Banknote className="size-4 text-primary" />
            <h3 className="font-display font-semibold text-sm">
              Common bank account
            </h3>
          </div>
          <EditBankDialog buildingId={buildingId} config={config} />
        </div>

        {hasUpi ? (
          <div className="grid sm:grid-cols-2 gap-3 text-sm">
            <BankField label="UPI ID" value={config.upiId!} mono />
            <BankField label="Payee name" value={config.payeeName ?? "—"} />
            <BankField
              label="Account number"
              value={config.accountNumber ?? "—"}
              mono
            />
            <BankField
              label="IFSC"
              value={config.ifscCode ?? "—"}
              mono
            />
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">
            Set a UPI ID + payee name and the tenant Pay-Now button on
            /app/society will start generating a QR pointed at this
            account. Account number / IFSC are optional fall-backs for
            tenants who prefer bank-transfer.
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function BankField({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p
        className={`mt-0.5 ${mono ? "font-mono text-xs" : "text-sm"} truncate`}
        title={value}
      >
        {value}
      </p>
    </div>
  );
}

function EditBankDialog({
  buildingId,
  config,
}: {
  buildingId: string;
  config: SocietyConfig;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    upiId: config.upiId ?? "",
    payeeName: config.payeeName ?? "",
    accountNumber: config.accountNumber ?? "",
    ifscCode: config.ifscCode ?? "",
  });

  const saveMut = useMutation({
    mutationFn: () =>
      societyApi.update(buildingId, {
        // updateConfig's @NotNull on defaultPerFlatAmount means we
        // need to echo the existing value; everything else is
        // optional and treated as "no change" when omitted.
        defaultPerFlatAmount: config.defaultPerFlatAmount,
        upiId: form.upiId,
        payeeName: form.payeeName,
        accountNumber: form.accountNumber,
        // IFSC uppercased to match the backend @Pattern validator;
        // empty string is fine (treated as clear-the-field).
        ifscCode: form.ifscCode.toUpperCase(),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["society", buildingId] });
      qc.invalidateQueries({ queryKey: ["my-societies"] });
      toast({ title: "Bank account updated." });
      setOpen(false);
    },
    onError: (err) =>
      toast({
        title: "Couldn't save",
        description: extractErrorMessage(err),
        variant: "destructive",
      }),
  });

  // Loose-validate IFSC client-side so the toast on submit is local,
  // not a round-trip 400.
  const ifscOk =
    form.ifscCode === "" || /^[A-Z]{4}0[A-Z0-9]{6}$/i.test(form.ifscCode);

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (o) {
          setForm({
            upiId: config.upiId ?? "",
            payeeName: config.payeeName ?? "",
            accountNumber: config.accountNumber ?? "",
            ifscCode: config.ifscCode ?? "",
          });
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Pencil className="size-3.5" />
          {config.upiId ? "Edit" : "Set up"}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Common bank account</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          Tenants pay into this account via UPI scan or bank transfer.
          The Pay-Now button on the tenant's society page stays hidden
          until at least the UPI ID is set.
        </p>

        <div className="space-y-3">
          <div>
            <Label>UPI ID</Label>
            <Input
              placeholder="e.g. anirudh@oksbi"
              value={form.upiId}
              onChange={(e) => setForm({ ...form, upiId: e.target.value })}
            />
            <p className="text-[10px] text-muted-foreground mt-0.5">
              The handle the QR resolves to. Required for Pay-Now.
            </p>
          </div>

          <div>
            <Label>Payee name (shown in tenant's UPI app)</Label>
            <Input
              placeholder="e.g. Anirudh Residency Welfare Fund"
              value={form.payeeName}
              onChange={(e) =>
                setForm({ ...form, payeeName: e.target.value })
              }
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Account number</Label>
              <Input
                placeholder="optional"
                value={form.accountNumber}
                onChange={(e) =>
                  setForm({ ...form, accountNumber: e.target.value })
                }
              />
            </div>
            <div>
              <Label>IFSC</Label>
              <Input
                placeholder="HDFC0001234"
                value={form.ifscCode}
                onChange={(e) =>
                  setForm({ ...form, ifscCode: e.target.value })
                }
              />
              {!ifscOk && (
                <p className="text-[10px] text-destructive mt-0.5">
                  Format: 4 letters + '0' + 6 alphanumerics
                </p>
              )}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="gradient"
            disabled={!ifscOk || saveMut.isPending}
            onClick={() => saveMut.mutate()}
          >
            <Save className="size-4" />
            {saveMut.isPending ? "Saving…" : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
