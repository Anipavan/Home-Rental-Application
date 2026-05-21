import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Banknote,
  Building2,
  Check,
  CreditCard,
  Edit2,
  Loader2,
  Pencil,
  ShieldCheck,
  Trash2,
  X,
} from "lucide-react";
import {
  bankAccountsApi,
  type BankAccountRequest,
} from "@/lib/api/bank-accounts";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  UpiIdField,
  isVpaUsable,
  type VpaState,
} from "@/components/payment/upi-id-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

/**
 * Self-managed bank-account section for the profile page.
 *
 * <p>Like Amazon's "Manage your payments → bank accounts" pattern:
 * one bank account on file per user, masked account number in read
 * mode, full form in edit mode. Save behaves as an upsert
 * (server-side `findByUserId().orElseGet(builder)`), so the same form
 * is used for first-time setup and subsequent edits.
 *
 * <p>The component is purposely self-contained — it manages its own
 * query, mutations, and edit/read toggle so any profile page can
 * embed it with a single `<BankAccountSection authUserId={...} />`.
 */
export function BankAccountSection({
  authUserId,
}: {
  /** auth-user id of the signed-in user; same value stored in auth-store. */
  authUserId: string;
}) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  // Live VPA validation state — populated by the UpiIdField child. We
  // gate Save on this being either {@code empty} (optional + blank) or
  // {@code valid} so we never persist a typo'd VPA.
  const [vpaState, setVpaState] = useState<VpaState>({ kind: "empty" });

  const q = useQuery({
    queryKey: ["bank-account", authUserId],
    queryFn: () => bankAccountsApi.getByUserId(authUserId),
    enabled: !!authUserId,
  });

  // Pre-populate the form from the saved row each time editing
  // toggles on, so "Edit" lands on the existing values (not blank).
  // Account number is hidden from the FE for security, so the form
  // requires the user to re-enter it — same UX as Amazon, which
  // forces a fresh entry on every edit.
  const [form, setForm] = useState<BankAccountRequest>({
    accountHolderName: "",
    bankName: "",
    accountNumber: "",
    ifscCode: "",
    branch: "",
    accountType: "SAVINGS",
    upiId: "",
  });

  useEffect(() => {
    if (editing && q.data) {
      setForm({
        accountHolderName: q.data.accountHolderName ?? "",
        bankName: q.data.bankName ?? "",
        // Don't pre-fill — backend never returns the full number; making
        // the user re-type it is the standard fintech edit pattern.
        accountNumber: "",
        ifscCode: q.data.ifscCode ?? "",
        branch: q.data.branch ?? "",
        accountType:
          (q.data.accountType as "SAVINGS" | "CURRENT" | null) ?? "SAVINGS",
        upiId: q.data.upiId ?? "",
      });
    }
    if (editing && !q.data) {
      // Brand-new entry — leave the form fully blank.
      setForm({
        accountHolderName: "",
        bankName: "",
        accountNumber: "",
        ifscCode: "",
        branch: "",
        accountType: "SAVINGS",
        upiId: "",
      });
    }
  }, [editing, q.data]);

  const saveM = useMutation({
    mutationFn: () =>
      bankAccountsApi.upsert(authUserId, {
        ...form,
        ifscCode: form.ifscCode.toUpperCase().trim(),
        // Backend rejects blank optional fields with @Pattern violations
        // if they're sent as empty strings, so normalize to undefined.
        branch: form.branch?.trim() || undefined,
        upiId: form.upiId?.trim() || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-account", authUserId] });
      toast({
        title: "Bank details saved",
        description:
          "Your payout / refund account is on file. You can update or remove it any time.",
      });
      setEditing(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't save bank details",
        description: extractErrorMessage(e),
      }),
  });

  const deleteM = useMutation({
    mutationFn: () => bankAccountsApi.remove(authUserId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["bank-account", authUserId] });
      toast({
        title: "Bank details removed",
        description: "We've cleared your saved account from our records.",
      });
      setConfirmDeleteOpen(false);
      setEditing(false);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't remove bank details",
        description: extractErrorMessage(e),
      }),
  });

  const canSave =
    form.accountHolderName.trim().length > 0 &&
    form.bankName.trim().length > 0 &&
    /^\d{9,18}$/.test(form.accountNumber.trim()) &&
    /^[A-Z]{4}0[A-Z0-9]{6}$/.test(form.ifscCode.trim().toUpperCase()) &&
    // VPA is optional — empty is fine. If it's filled, it must validate.
    // We pass `optional=true` to the field, so {@code isVpaUsable} returns
    // true on empty + true on valid — exactly the gate we want here.
    isVpaUsable(vpaState, true) &&
    !saveM.isPending;

  return (
    <Card>
      <CardContent className="p-6 sm:p-8">
        <header className="flex items-start justify-between gap-3 mb-5 flex-wrap">
          <div className="flex items-start gap-3">
            <div className="size-10 rounded-xl bg-primary/10 text-primary grid place-items-center shrink-0">
              <Banknote className="size-5" />
            </div>
            <div>
              <h2 className="font-display text-lg font-semibold">
                Bank details
              </h2>
              <p className="text-xs text-muted-foreground mt-0.5 max-w-md">
                Used by Anirudh Homes for rent payouts (owners) and deposit /
                cancellation refunds (tenants). Visible only to you.
              </p>
            </div>
          </div>
          {q.data && !editing && (
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => setEditing(true)}
              >
                <Pencil className="size-4" /> Edit
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setConfirmDeleteOpen(true)}
                className="text-destructive hover:text-destructive"
              >
                <Trash2 className="size-4" /> Remove
              </Button>
            </div>
          )}
        </header>

        {q.isLoading && (
          <p className="text-sm text-muted-foreground">
            Loading your bank details…
          </p>
        )}

        {/* Empty state — first-time setup. */}
        {!q.isLoading && !q.data && !editing && (
          <div className="rounded-xl border border-dashed border-border bg-secondary/30 p-6 text-center">
            <CreditCard className="size-7 mx-auto text-muted-foreground" />
            <p className="font-medium mt-2">No bank account on file</p>
            <p className="text-xs text-muted-foreground mt-1 max-w-sm mx-auto">
              Add your account so we can credit refunds, payouts, and
              rent settlements straight to your bank.
            </p>
            <Button
              size="sm"
              variant="gradient"
              className="mt-4"
              onClick={() => setEditing(true)}
            >
              <Edit2 className="size-4" /> Add bank details
            </Button>
          </div>
        )}

        {/* Read mode — saved details, masked account number. */}
        {!q.isLoading && q.data && !editing && (
          <div className="grid gap-3 sm:grid-cols-2 text-sm">
            <Field
              label="Account holder"
              value={q.data.accountHolderName}
            />
            <Field label="Bank" value={q.data.bankName} />
            <Field
              label="Account number"
              value={q.data.accountNumberMasked}
              mono
            />
            <Field label="IFSC" value={q.data.ifscCode} mono />
            <Field label="Branch" value={q.data.branch ?? "—"} />
            <Field
              label="Account type"
              value={q.data.accountType ?? "—"}
            />
            {q.data.upiId && (
              <Field label="UPI ID" value={q.data.upiId} mono />
            )}
          </div>
        )}

        {/* Edit / create form */}
        {editing && (
          <form
            className="grid gap-4 sm:grid-cols-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (canSave) saveM.mutate();
            }}
          >
            <div className="sm:col-span-2">
              <Label htmlFor="accountHolderName">
                Account holder name
              </Label>
              <Input
                id="accountHolderName"
                value={form.accountHolderName}
                onChange={(e) =>
                  setForm({ ...form, accountHolderName: e.target.value })
                }
                placeholder="As printed on the cheque book"
                className="mt-1.5"
                maxLength={120}
                required
              />
            </div>
            <div className="sm:col-span-2">
              <Label htmlFor="bankName">Bank name</Label>
              <Input
                id="bankName"
                value={form.bankName}
                onChange={(e) =>
                  setForm({ ...form, bankName: e.target.value })
                }
                placeholder="State Bank of India, HDFC Bank, …"
                className="mt-1.5"
                maxLength={120}
                required
              />
            </div>
            <div>
              <Label htmlFor="accountNumber">Account number</Label>
              <Input
                id="accountNumber"
                value={form.accountNumber}
                onChange={(e) =>
                  setForm({
                    ...form,
                    accountNumber: e.target.value.replace(/\D/g, ""),
                  })
                }
                placeholder="9-18 digits"
                className="mt-1.5 font-mono"
                inputMode="numeric"
                maxLength={18}
                required
              />
              <p className="text-[11px] text-muted-foreground mt-1">
                Double-check the number — we save what you type here.
              </p>
            </div>
            <div>
              <Label htmlFor="ifscCode">IFSC code</Label>
              <Input
                id="ifscCode"
                value={form.ifscCode}
                onChange={(e) =>
                  setForm({
                    ...form,
                    ifscCode: e.target.value.toUpperCase(),
                  })
                }
                placeholder="e.g. SBIN0001234"
                className="mt-1.5 font-mono uppercase"
                maxLength={11}
                required
              />
            </div>
            <div>
              <Label htmlFor="branch">Branch (optional)</Label>
              <Input
                id="branch"
                value={form.branch ?? ""}
                onChange={(e) =>
                  setForm({ ...form, branch: e.target.value })
                }
                placeholder="MG Road, Bengaluru"
                className="mt-1.5"
                maxLength={200}
              />
            </div>
            <div>
              <Label htmlFor="accountType">Account type</Label>
              <Select
                value={form.accountType ?? "SAVINGS"}
                onValueChange={(v) =>
                  setForm({
                    ...form,
                    accountType: v as "SAVINGS" | "CURRENT",
                  })
                }
              >
                <SelectTrigger className="mt-1.5" id="accountType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="SAVINGS">Savings</SelectItem>
                  <SelectItem value="CURRENT">Current</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="sm:col-span-2">
              {/* Live-validated UPI field. As the user types, we debounce
                  for 600ms and call /payments/vpa/validate which round-
                  trips through Razorpay's NPCI lookup. On a hit we render
                  a green "Verified · NAME" pill so the owner can confirm
                  the bank-registered name matches the one they typed in
                  Account holder above. Save is gated on this being either
                  empty (it's optional) or valid. */}
              <UpiIdField
                id="upiId"
                label="UPI ID"
                optional
                value={form.upiId ?? ""}
                onChange={(v) => setForm({ ...form, upiId: v })}
                onStateChange={setVpaState}
                helper="Faster than NEFT for small payouts. We'll show your tenants the bank-registered name when they pay."
              />
            </div>

            <div className="sm:col-span-2 flex flex-col-reverse sm:flex-row sm:justify-end gap-2 pt-1">
              <Button
                type="button"
                variant="ghost"
                onClick={() => setEditing(false)}
                disabled={saveM.isPending}
              >
                <X className="size-4" /> Cancel
              </Button>
              <Button
                type="submit"
                variant="gradient"
                disabled={!canSave}
              >
                {saveM.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Check className="size-4" />
                )}
                Save bank details
              </Button>
            </div>
          </form>
        )}

        {/* Trust-line — small reassurance footer like Amazon shows
            under saved payment methods. */}
        {q.data && !editing && (
          <p className="text-[11px] text-muted-foreground mt-5 flex items-center gap-1.5">
            <ShieldCheck className="size-3.5 text-success" />
            Stored securely. We never share these details with other
            tenants or owners.
          </p>
        )}
      </CardContent>

      <Dialog open={confirmDeleteOpen} onOpenChange={setConfirmDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Remove bank details?</DialogTitle>
            <DialogDescription>
              Your account number will be wiped from our records. We
              won't be able to send refunds or payouts to this account
              after this.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => setConfirmDeleteOpen(false)}
              disabled={deleteM.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => deleteM.mutate()}
              disabled={deleteM.isPending}
            >
              {deleteM.isPending && (
                <Loader2 className="size-4 animate-spin" />
              )}
              Remove
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

/* ---------- internal ---------- */

function Field({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="rounded-lg border bg-secondary/30 p-3">
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground flex items-center gap-1">
        <Building2 className="size-3" />
        {label}
      </p>
      <p
        className={
          "mt-1 font-medium " + (mono ? "font-mono text-sm" : "text-sm")
        }
      >
        {value || "—"}
      </p>
    </div>
  );
}
