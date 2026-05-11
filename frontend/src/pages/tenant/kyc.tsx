import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldCheck, AlertTriangle, CheckCircle2, Loader2 } from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { kycApi } from "@/lib/api/kyc";
import { isKycDisabled } from "@/lib/feature-flags";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/page-header";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import type { KycStatus } from "@/types/api";

export function KycPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [showInitiate, setShowInitiate] = useState(false);

  // KYC Service stores the user identifier opaquely, so we use authUserId
  // directly. This avoids a cross-service round-trip to user-service that
  // can fail/lag on legacy tenants and leave the "Start KYC" button
  // permanently disabled — the original bug. authUserId is on the JWT and
  // available the moment the user is logged in.
  const userId = authUserId ?? undefined;

  // When KYC is paused platform-wide we still let the page mount
  // (the route-level FeatureDisabledOutlet handles the overlay) but
  // skip every backing query so the disabled-page UX doesn't fire
  // requests at a service the team has nominally turned off.
  const kycPaused = isKycDisabled();

  const statusQ = useQuery({
    queryKey: ["kyc", userId],
    queryFn: () => kycApi.status(userId!),
    enabled: !!userId && !kycPaused,
    retry: false,
  });

  const reportQ = useQuery({
    queryKey: ["kyc-report", userId],
    queryFn: () => kycApi.report(userId!),
    enabled:
      !!userId && !kycPaused && statusQ.data?.verificationStatus === "VERIFIED",
    retry: false,
  });

  const initiateM = useMutation({
    mutationFn: (body: {
      aadhaarNumber: string;
      fullName: string;
      panNumber?: string;
    }) =>
      kycApi.initiate(userId!, {
        aadhaarNumber: body.aadhaarNumber,
        fullName: body.fullName,
        panNumber: body.panNumber || undefined,
        consentText:
          "I consent to RentGenius using my Aadhaar / PAN data to verify my identity, " +
          "in line with the DPDP Act 2023.",
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["kyc", userId] });
      setShowInitiate(false);
      toast({
        title: "KYC initiated",
        description: "We've sent a verification request. You'll get an update shortly.",
      });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't start KYC",
        description: extractErrorMessage(e),
      }),
  });

  const verifyPanM = useMutation({
    mutationFn: (body: { panNumber: string; panHolderName: string }) =>
      kycApi.verifyPan({
        userId: userId!,
        panNumber: body.panNumber,
        panHolderName: body.panHolderName,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["kyc", userId] });
      toast({ title: "PAN verified" });
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "PAN verification failed",
        description: extractErrorMessage(e),
      }),
  });

  if (!userId) {
    // Not logged in — shouldn't reach this route via ProtectedRoute, but
    // render a tiny loader rather than crashing on undefined.
    return <Skeleton className="h-72 rounded-2xl max-w-3xl" />;
  }

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Identity verification"
        description="Aadhaar + PAN verification to unlock all RentGenius features."
      />

      <Card className="mb-6">
        <CardContent className="p-6 sm:p-8">
          <div className="flex items-start gap-4">
            <StatusIcon status={statusQ.data?.verificationStatus} />
            <div className="flex-1">
              <h3 className="font-display font-semibold text-lg flex items-center gap-2">
                Status
                <StatusBadge status={statusQ.data?.verificationStatus} />
              </h3>
              <p className="text-sm text-muted-foreground mt-1">
                {captionFor(statusQ.data?.verificationStatus)}
              </p>
              {statusQ.data?.failureReason && (
                <div className="mt-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                  <strong>{statusQ.data.failureCode}:</strong>{" "}
                  {statusQ.data.failureReason}
                </div>
              )}
            </div>
          </div>

          {statusQ.data && (
            <div className="grid sm:grid-cols-2 gap-3 mt-6">
              <Tile
                label="Aadhaar"
                value={statusQ.data.aadhaarVerified ? "Verified" : "Pending"}
                ok={statusQ.data.aadhaarVerified}
              />
              <Tile
                label="PAN"
                value={
                  statusQ.data.panMasked ?? (statusQ.data.panVerified ? "Verified" : "Not provided")
                }
                ok={statusQ.data.panVerified}
              />
              <Tile
                label="DigiLocker"
                value={statusQ.data.digilockerLinked ? "Linked" : "Not linked"}
                ok={statusQ.data.digilockerLinked}
              />
              <Tile
                label="Provider"
                value={statusQ.data.kycProvider ?? "—"}
                ok
              />
            </div>
          )}

          {(!statusQ.data || statusQ.data.verificationStatus !== "VERIFIED") && (
            <div className="mt-6">
              {showInitiate ? (
                <InitiateForm
                  pending={initiateM.isPending}
                  onCancel={() => setShowInitiate(false)}
                  onSubmit={(b) => initiateM.mutate(b)}
                />
              ) : (
                <Button
                  variant="gradient"
                  onClick={() => setShowInitiate(true)}
                  disabled={!userId}
                >
                  <ShieldCheck className="size-4" />
                  Start KYC
                </Button>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardContent className="p-6 sm:p-8">
          <h3 className="font-display font-semibold text-lg">PAN-only verification</h3>
          <p className="text-sm text-muted-foreground mt-1">
            Need to verify just your PAN (e.g. for GST invoices)? Enter it here.
          </p>
          <PanForm
            pending={verifyPanM.isPending}
            onSubmit={(b) => verifyPanM.mutate(b)}
            disabled={!userId}
          />
        </CardContent>
      </Card>

      {reportQ.data && (
        <Card>
          <CardContent className="p-6 sm:p-8">
            <h3 className="font-display font-semibold text-lg">Compliance report</h3>
            <div className="grid sm:grid-cols-2 gap-3 mt-4 text-sm">
              <Row label="Provider" value={reportQ.data.kycProvider ?? "—"} />
              <Row label="Confidence" value={reportQ.data.confidenceLevel} />
              <Row
                label="Verified at"
                value={
                  reportQ.data.verifiedAt
                    ? new Date(reportQ.data.verifiedAt).toLocaleString()
                    : "—"
                }
              />
              <Row
                label="Generated at"
                value={new Date(reportQ.data.generatedAt).toLocaleString()}
              />
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/* ---------- inner components ---------- */

function StatusIcon({ status }: { status?: KycStatus }) {
  if (status === "VERIFIED") {
    return <CheckCircle2 className="size-10 text-emerald-500" />;
  }
  if (status === "FAILED") {
    return <AlertTriangle className="size-10 text-destructive" />;
  }
  if (status === "INITIATED") {
    return <Loader2 className="size-10 text-amber-500 animate-spin" />;
  }
  return <ShieldCheck className="size-10 text-muted-foreground" />;
}

function StatusBadge({ status }: { status?: KycStatus }) {
  if (status === "VERIFIED")
    return <Badge variant="success">Verified</Badge>;
  if (status === "FAILED")
    return <Badge variant="destructive">Failed</Badge>;
  if (status === "INITIATED")
    return <Badge variant="warning">In progress</Badge>;
  return <Badge variant="secondary">Not started</Badge>;
}

function captionFor(status?: KycStatus) {
  switch (status) {
    case "VERIFIED":
      return "Identity verified. You're all set.";
    case "INITIATED":
      return "Verification request submitted. We'll update this page when the provider responds.";
    case "FAILED":
      return "Verification didn't go through. Try again with the right details below.";
    default:
      return "Run KYC once to unlock paying rent, signing leases, and submitting maintenance requests.";
  }
}

function Tile({
  label,
  value,
  ok,
}: {
  label: string;
  value: string;
  ok?: boolean;
}) {
  return (
    <div className="rounded-xl border bg-secondary/30 p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium text-sm mt-0.5 flex items-center gap-1.5">
        {value}
        {ok && <CheckCircle2 className="size-3.5 text-emerald-500" />}
      </p>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b border-border/40 pb-1.5">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}

function InitiateForm({
  pending,
  onSubmit,
  onCancel,
}: {
  pending: boolean;
  onSubmit: (b: { aadhaarNumber: string; fullName: string; panNumber?: string }) => void;
  onCancel: () => void;
}) {
  return (
    <form
      className="space-y-3 border rounded-xl p-4 bg-background"
      onSubmit={(e) => {
        e.preventDefault();
        const fd = new FormData(e.currentTarget);
        onSubmit({
          aadhaarNumber: String(fd.get("aadhaar") ?? ""),
          fullName: String(fd.get("fullName") ?? ""),
          panNumber: String(fd.get("pan") ?? "") || undefined,
        });
      }}
    >
      <div>
        <Label htmlFor="aadhaar">Aadhaar number</Label>
        <Input
          id="aadhaar"
          name="aadhaar"
          inputMode="numeric"
          pattern="[0-9]{12}"
          maxLength={12}
          required
          placeholder="12 digits"
          className="mt-1.5"
        />
      </div>
      <div>
        <Label htmlFor="fullName">Full name (as on Aadhaar)</Label>
        <Input id="fullName" name="fullName" required className="mt-1.5" />
      </div>
      <div>
        <Label htmlFor="pan">PAN (optional)</Label>
        <Input
          id="pan"
          name="pan"
          pattern="[A-Z]{5}[0-9]{4}[A-Z]"
          placeholder="AAAAA9999A"
          className="mt-1.5"
        />
      </div>
      <p className="text-xs text-muted-foreground">
        By continuing you consent to RentGenius verifying these details with the
        government's Aadhaar / PAN system. Your Aadhaar number is hashed before
        storage — we never persist it in plain text.
      </p>
      <div className="flex gap-2 justify-end pt-2">
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" variant="gradient" disabled={pending}>
          {pending ? "Submitting…" : "Continue"}
        </Button>
      </div>
    </form>
  );
}

function PanForm({
  pending,
  onSubmit,
  disabled,
}: {
  pending: boolean;
  onSubmit: (b: { panNumber: string; panHolderName: string }) => void;
  disabled: boolean;
}) {
  return (
    <form
      className="grid sm:grid-cols-3 gap-3 mt-4"
      onSubmit={(e) => {
        e.preventDefault();
        const fd = new FormData(e.currentTarget);
        onSubmit({
          panNumber: String(fd.get("panNumber") ?? "").toUpperCase(),
          panHolderName: String(fd.get("panHolderName") ?? ""),
        });
      }}
    >
      <Input
        name="panNumber"
        required
        pattern="[A-Z]{5}[0-9]{4}[A-Z]"
        placeholder="PAN (AAAAA9999A)"
      />
      <Input name="panHolderName" required placeholder="PAN holder's name" />
      <Button type="submit" variant="outline" disabled={disabled || pending}>
        {pending ? "Verifying…" : "Verify PAN"}
      </Button>
    </form>
  );
}
