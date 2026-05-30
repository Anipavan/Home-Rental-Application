import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ShieldCheck,
  AlertTriangle,
  CheckCircle2,
  Loader2,
} from "lucide-react";
import { useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { kycApi } from "@/lib/api/kyc";
import { usersApi } from "@/lib/api/users";
import { ContactAdminForKycDialog } from "@/components/kyc/contact-admin-dialog";
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

/**
 * sessionStorage key for the DigiLocker OAuth state token. Kept here
 * (not deleted) so the dormant DigiLocker callback page still compiles —
 * we'll flip the active flow back to DigiLocker once Anirudh Homes is
 * incorporated and the partner integration is approved.
 */
export const DIGILOCKER_STATE_STORAGE_KEY = "anirudhhomes.kyc.digilocker.state";

/**
 * Verbatim consent text — persisted on the KYC record so a future
 * compliance audit can replay exactly what the user agreed to.
 * Aligned with DPDP Act 2023 §6 (notice + purpose) and Income Tax Act
 * §139A (PAN-based identity).
 */
const PAN_KYC_CONSENT_TEXT =
  "I authorise Anirudh Homes to verify my PAN against the NSDL database " +
  "for identity verification, in line with the DPDP Act 2023. My PAN is " +
  "stored only in masked form (last 4 digits) and never shared with third " +
  "parties beyond the verification call.";

export function KycPage() {
  const { authUserId } = useAuthStore();
  const qc = useQueryClient();
  const [accepted, setAccepted] = useState(false);
  // Drives the "contact admin" dialog — opens when the server returns
  // a vendor-unavailable failure (billing/quota hit on Sandbox).
  // Separated from the inline status display so the user gets a
  // foreground escalation prompt instead of a quiet red banner.
  const [contactDialogOpen, setContactDialogOpen] = useState(false);

  // KYC Service stores the user identifier opaquely, so we use authUserId
  // directly. This avoids a cross-service round-trip to user-service that
  // can fail/lag on legacy tenants and leave the verify button permanently
  // disabled. authUserId is on the JWT and available the moment the user
  // is logged in.
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

  // Pull the user-service profile so we can pre-fill the PAN form with
  // the user's name (firstName + lastName) and date of birth. Cuts the
  // typing they'd otherwise have to do and reduces "name didn't match
  // NSDL" failures from minor typos. Read-only — the user can still
  // edit either field if it's wrong / outdated.
  const profileQ = useQuery({
    queryKey: ["me", userId],
    queryFn: () => usersApi.byAuthId(userId!),
    enabled: !!userId && !kycPaused,
    staleTime: 5 * 60_000,
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response
        ?.status;
      return status !== 404 && failureCount < 1;
    },
  });

  /**
   * Primary KYC path — PAN verification via Sandbox.co.in.
   *
   * <p>The /verify-pan call is terminal in PAN-only mode:
   * server-side, a successful PAN check flips the record to VERIFIED
   * and publishes kyc.verified. Local query invalidation handles the
   * UI refresh.
   */
  const verifyPanM = useMutation({
    mutationFn: (body: { panNumber: string; panHolderName: string; dateOfBirth: string }) =>
      kycApi.verifyPan({
        userId: userId!,
        panNumber: body.panNumber.toUpperCase().trim(),
        panHolderName: body.panHolderName.trim(),
        // Backend expects ISO yyyy-MM-dd — which is exactly what
        // the HTML <input type="date"> value already is.
        dateOfBirth: body.dateOfBirth.trim(),
      }),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["kyc", userId] });
      qc.invalidateQueries({ queryKey: ["kyc-report", userId] });
      if (data.verificationStatus === "VERIFIED") {
        toast({
          title: "You're verified",
          description: data.nameOnAadhaar
            ? `Welcome, ${data.nameOnAadhaar}.`
            : "Your PAN was successfully verified.",
        });
      } else if (data.failureCode === "VENDOR_UNAVAILABLE") {
        // Server-side vendor outage / billing alert — open the
        // contact-admin dialog instead of the inline error banner.
        // The user can't fix this themselves; they need a phone call
        // to complete verification manually.
        setContactDialogOpen(true);
      } else {
        toast({
          variant: "destructive",
          title: "Verification didn't complete",
          description:
            data.failureReason ?? "Please check your PAN and name and try again.",
        });
      }
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

  const isVerified = statusQ.data?.verificationStatus === "VERIFIED";

  return (
    <div className="animate-fade-in max-w-3xl">
      <PageHeader
        title="Identity verification"
        description="Verify your PAN to unlock all Anirudh Homes features. Takes ~5 seconds."
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
                label="PAN"
                value={
                  statusQ.data.panMasked ??
                  (statusQ.data.panVerified ? "Verified" : "Not provided")
                }
                ok={statusQ.data.panVerified}
              />
              <Tile
                label="Name on PAN"
                value={statusQ.data.nameOnAadhaar ?? "—"}
                ok={!!statusQ.data.nameOnAadhaar}
              />
              <Tile
                label="Provider"
                value={statusQ.data.kycProvider ?? "—"}
                ok
              />
              <Tile
                label="Verified at"
                value={
                  statusQ.data.verifiedAt
                    ? new Date(statusQ.data.verifiedAt).toLocaleString()
                    : "—"
                }
                ok={!!statusQ.data.verifiedAt}
              />
            </div>
          )}

          {!isVerified && (
            <PanVerifyPanel
              accepted={accepted}
              setAccepted={setAccepted}
              pending={verifyPanM.isPending}
              onSubmit={(b) => verifyPanM.mutate(b)}
              disabled={!userId}
              // Pre-fill name + DOB from the user-service profile so
              // the user doesn't have to retype what we already have.
              // They can edit either if their PAN card has a different
              // spelling.
              defaultName={
                profileQ.data
                  ? `${profileQ.data.firstName ?? ""} ${profileQ.data.lastName ?? ""}`
                      .trim() || undefined
                  : undefined
              }
              defaultDob={profileQ.data?.dateOfBirth ?? undefined}
            />
          )}
        </CardContent>
      </Card>

      <ContactAdminForKycDialog
        open={contactDialogOpen}
        onOpenChange={setContactDialogOpen}
        reason={statusQ.data?.failureReason}
      />

      {reportQ.data && (
        <Card>
          <CardContent className="p-6 sm:p-8">
            <h3 className="font-display font-semibold text-lg">
              Compliance report
            </h3>
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
  if (status === "VERIFIED") return <Badge variant="success">Verified</Badge>;
  if (status === "FAILED") return <Badge variant="destructive">Failed</Badge>;
  // Both INITIATED (user has tried at least once but verification didn't
  // complete) and PENDING (server-side default for a record that exists
  // but hasn't been processed yet) read as "in progress" to the user.
  // Earlier versions only handled INITIATED; PENDING fell through to
  // "Not started" which contradicted any error message rendered above
  // it (the user clearly HAD started — that's why a failureReason was
  // showing). Treating both states the same fixes the contradictory UI.
  if (status === "INITIATED" || status === "PENDING")
    return <Badge variant="warning">Needs retry</Badge>;
  return <Badge variant="secondary">Not started</Badge>;
}

function captionFor(status?: KycStatus) {
  switch (status) {
    case "VERIFIED":
      return "Identity verified. You're all set.";
    case "INITIATED":
    case "PENDING":
      return "Your last attempt didn't complete. The details from your previous try are pre-filled below — adjust if needed and retry.";
    case "FAILED":
      return "Last PAN check didn't go through. Double-check your PAN and the name as it appears on your card.";
    default:
      return "Run a quick PAN check to unlock paying rent, signing leases, and submitting maintenance requests.";
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

/**
 * The headline PAN verification card. Shows the consent disclosure
 * inline, gates the submit on the explicit checkbox, and posts to
 * /kyc/verify-pan via {@link kycApi.verifyPan}. In PAN-only mode (active
 * today via Sandbox.co.in), a successful PAN check is the WHOLE KYC —
 * the server flips status to VERIFIED and publishes kyc.verified.
 */
function PanVerifyPanel({
  accepted,
  setAccepted,
  pending,
  onSubmit,
  disabled,
  defaultName,
  defaultDob,
}: {
  accepted: boolean;
  setAccepted: (b: boolean) => void;
  pending: boolean;
  onSubmit: (b: {
    panNumber: string;
    panHolderName: string;
    dateOfBirth: string;
  }) => void;
  disabled: boolean;
  /** Pre-filled name from the user-service profile, if available. */
  defaultName?: string;
  /** Pre-filled DOB from the user-service profile, ISO yyyy-MM-dd. */
  defaultDob?: string;
}) {
  // Today minus 18 years — sensible upper bound for the date input
  // (PAN holders must be 18+ to have applied). The lower bound is
  // intentionally permissive; NSDL itself rejects invalid DOBs upstream.
  const maxDob = new Date(Date.now() - 18 * 365 * 24 * 60 * 60 * 1000)
    .toISOString()
    .slice(0, 10);
  return (
    <div className="mt-6 rounded-xl border bg-gradient-to-br from-emerald-50/60 to-background p-5 dark:from-emerald-950/20">
      <div className="flex items-start gap-3">
        <div className="size-10 rounded-lg bg-emerald-500/15 grid place-items-center shrink-0">
          <ShieldCheck className="size-5 text-emerald-600" />
        </div>
        <div className="flex-1">
          <h4 className="font-semibold text-base">Verify your PAN</h4>
          <p className="text-sm text-muted-foreground mt-1">
            We'll cross-check your PAN against the NSDL database (the same
            source banks use). Your PAN is stored only in masked form — we
            never persist the full number.
          </p>

          <form
            className="grid sm:grid-cols-2 gap-3 mt-4"
            onSubmit={(e) => {
              e.preventDefault();
              const fd = new FormData(e.currentTarget);
              onSubmit({
                panNumber: String(fd.get("panNumber") ?? ""),
                panHolderName: String(fd.get("panHolderName") ?? ""),
                dateOfBirth: String(fd.get("dateOfBirth") ?? ""),
              });
            }}
          >
            <div className="sm:col-span-1">
              <Label htmlFor="panNumber">PAN number</Label>
              <Input
                id="panNumber"
                name="panNumber"
                required
                pattern="[A-Z]{5}[0-9]{4}[A-Z]"
                placeholder="AAAAA9999A"
                className="mt-1.5 font-mono uppercase"
                maxLength={10}
                autoComplete="off"
                spellCheck={false}
                onChange={(e) => {
                  e.currentTarget.value = e.currentTarget.value.toUpperCase();
                }}
              />
            </div>
            <div className="sm:col-span-1">
              <Label htmlFor="panHolderName">Name as on PAN card</Label>
              <Input
                id="panHolderName"
                name="panHolderName"
                required
                placeholder="As printed on your PAN card"
                className="mt-1.5"
                maxLength={120}
                autoComplete="off"
                // Pre-fill from the user-service profile (if available)
                // so the user doesn't retype their name. They can edit
                // if their PAN card uses a different spelling. defaultValue
                // (not value) so the field remains uncontrolled — keeps
                // the existing FormData-based onSubmit pattern.
                defaultValue={defaultName ?? ""}
              />
            </div>

            <div className="sm:col-span-2">
              <Label htmlFor="dateOfBirth">Date of birth (as on PAN)</Label>
              <Input
                id="dateOfBirth"
                name="dateOfBirth"
                type="date"
                required
                className="mt-1.5"
                max={maxDob}
                defaultValue={defaultDob ?? ""}
              />
              <p className="text-[11px] text-muted-foreground mt-1.5">
                NSDL matches (PAN + DOB) together — without the DOB the
                verification can't proceed.
              </p>
            </div>

            <label className="sm:col-span-2 mt-2 flex items-start gap-2 text-sm select-none cursor-pointer">
              <input
                type="checkbox"
                className="mt-0.5 size-4 rounded border-border accent-emerald-600"
                checked={accepted}
                onChange={(e) => setAccepted(e.target.checked)}
              />
              <span className="text-muted-foreground">
                {PAN_KYC_CONSENT_TEXT}
              </span>
            </label>

            <div className="sm:col-span-2 mt-1">
              <Button
                type="submit"
                variant="gradient"
                disabled={disabled || pending || !accepted}
              >
                {pending ? (
                  <>
                    <Loader2 className="size-4 animate-spin" />
                    Verifying with NSDL…
                  </>
                ) : (
                  <>
                    <ShieldCheck className="size-4" />
                    Verify PAN
                  </>
                )}
              </Button>
              <p className="text-[11px] text-muted-foreground mt-2">
                Takes ~5 seconds. We'll show you the registered name on your
                PAN before marking you verified.
              </p>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
