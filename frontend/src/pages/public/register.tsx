import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import {
  Building2,
  Check,
  Eye,
  EyeOff,
  Home,
  Loader2,
  Search,
  Users,
} from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog";
import { TermsAndConditionsContent } from "@/components/auth/terms-content";
import { authApi } from "@/lib/api/auth";
import { claimsApi } from "@/lib/api/claims";
import { propertiesApi } from "@/lib/api/properties";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/auth-store";
import { cn } from "@/lib/utils";
import type {
  BuildingResponseDTO,
  MembershipClaimRole,
  Role,
} from "@/types/api";

/**
 * Audit H19: India-tolerant phone regex. Accepts:
 *   +91-9876543210
 *   919876543210
 *   9876543210     (assumed Indian 10-digit mobile)
 *   +1 555 123 4567
 * Rejects anything with letters or fewer than 10 digits.
 *
 * The backend already runs a similar regex
 * ({@code ^\+?[0-9\- ]{7,20}$}) — this mirror catches typos before
 * the round-trip.
 */
const PHONE_REGEX = /^\+?[0-9][0-9\s\-]{8,18}[0-9]$/;

/**
 * Audit H18: passwords must (a) match confirm-password, (b) meet the
 * backend's strength rule (1 upper, 1 lower, 1 digit, 8+ chars). The
 * backend enforces the rule too; mirroring here gives the user an
 * inline error instead of a round-trip toast.
 */
const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/;

/**
 * Sentinel for "no selection yet" in optional Select dropdowns.
 * Radix's SelectItem disallows an empty-string value, so we use a
 * placeholder marker and strip it back to undefined before sending
 * the request. The backend treats null and missing as equivalent.
 */
const UNSELECTED = "__none__";

/**
 * Top-level choice on the signup form. SOCIETY is a virtual bucket
 * that resolves to either a MAINTAINER or RESIDENT claim once the
 * user picks the sub-flavour. The account itself is always created
 * as a TENANT (role = TENANT in auth-service); the claim approval
 * later swaps role to MAINTAINER for the maintainer path, or binds
 * the user as a flat tenant for the resident path.
 */
type SignupChoice = "TENANT" | "OWNER" | "SOCIETY";

export function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [choice, setChoice] = useState<SignupChoice>("TENANT");
  /**
   * SOCIETY only supports MAINTAINER claims now. The earlier RESIDENT
   * ("maintainee") path was redundant — the public ledger URL
   * (/society/view/<token>) already gives residents a read-only view
   * of expenses, fund balance, and the maintainer's contact info
   * without requiring an account. Keeping the variable as a const so
   * the rest of the claim-submission code stays role-aware in case we
   * want to bring residents back later.
   */
  const societyRole: MembershipClaimRole = "MAINTAINER";
  /** Building the SOCIETY or OWNER-of-existing-flat claim targets.
   *  Picked via search. */
  const [pickedBuilding, setPickedBuilding] =
    useState<BuildingResponseDTO | null>(null);
  const [societyFlatNumber, setSocietyFlatNumber] = useState("");
  const [societyNote, setSocietyNote] = useState("");

  /**
   * OWNER signup sub-mode (V8 — per-flat ownership):
   *   NEW_BUILDING — the user is registering a new building from
   *                  scratch (the legacy "I'm an owner" flow).
   *   EXISTING_FLAT — the user already has a flat in a building
   *                  someone else added to the platform; they want
   *                  to be marked as the owner of that specific flat.
   *                  Submits a FLAT_OWNER claim against (buildingId,
   *                  flatNumber) — the building owner approves and
   *                  the swap happens server-side.
   */
  const [ownerMode, setOwnerMode] =
    useState<"NEW_BUILDING" | "EXISTING_FLAT">("NEW_BUILDING");

  const [clientError, setClientError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [termsOpen, setTermsOpen] = useState(false);

  // Controlled selects for the optional dropdowns. Kept as state
  // because Radix's <Select> is not a native <select> — its value
  // doesn't show up in FormData and has to be read off React state.
  const [gender, setGender] = useState<string>(UNSELECTED);
  const [maritalStatus, setMaritalStatus] = useState<string>(UNSELECTED);
  const [tenantType, setTenantType] = useState<string>(UNSELECTED);

  /**
   * Maintainer-claim residency gate (added Jun 2026).
   *
   * The product rule: a maintainer must be an existing resident of
   * the building they want to manage. We can't verify the NEW user
   * actually IS the existing tenant — that's the owner's job at the
   * approval step — but we CAN refuse the obvious cases before
   * burning a /auth/register call:
   *   1. Flat number is mandatory (no more optional metadata).
   *   2. The flat must exist in the picked building.
   *   3. The flat must be currently occupied (have a tenant_id).
   *
   * State machine on the form:
   *   idle       — fields blank or building not picked yet
   *   checking   — fetch in flight to /properties/flats/preview
   *   valid      — flat exists + is occupied; submit is unblocked
   *   missing    — no flat by that number in this building
   *   vacant     — flat exists but has no tenant (applicant clearly
   *                isn't a current resident)
   *   error      — preview call failed (network / 5xx); allow submit
   *                anyway and let the backend's authoritative check
   *                in MembershipClaimServiceImpl.createClaim catch it
   *
   * Backend re-validates with identical rules so a determined client
   * tweaking the form state can't bypass — the gate is defence-in-
   * depth, not the security boundary.
   */
  const [residencyState, setResidencyState] = useState<
    "idle" | "checking" | "valid" | "missing" | "vacant" | "error"
  >("idle");

  // Map the UI choice to the auth-service role that goes into the
  // /auth/register body. SOCIETY users register as TENANT — their
  // promotion to MAINTAINER (when applicable) happens via the claim
  // approval path, not at signup time.
  const authRole: Role = choice === "OWNER" ? "OWNER" : "TENANT";

  /**
   * Debounced preview check — only runs for SOCIETY signups where a
   * building IS picked and a flat number HAS been typed. Resets to
   * "idle" any time either input changes so the user always sees a
   * fresh result; transitions to "checking" while the fetch is in
   * flight; lands on one of the terminal states once the response
   * comes back.
   *
   * Uses propertiesApi.flats.preview which is anonymous-safe — the
   * gateway exposes GET /properties/flats/preview without JWT, and
   * the response is just {exists, occupied} so we leak no PII.
   */
  useEffect(() => {
    // The residency gate only applies to SOCIETY (maintainer) signups.
    // EXISTING_FLAT owner claims and TENANT signups don't need it.
    if (choice !== "SOCIETY") {
      setResidencyState("idle");
      return;
    }
    const trimmed = societyFlatNumber.trim();
    if (!pickedBuilding || !trimmed) {
      setResidencyState("idle");
      return;
    }
    setResidencyState("checking");
    const handle = setTimeout(async () => {
      try {
        const r = await propertiesApi.flats.preview(
          pickedBuilding.buildingId,
          trimmed,
        );
        if (!r.exists) {
          setResidencyState("missing");
        } else if (!r.occupied) {
          setResidencyState("vacant");
        } else {
          setResidencyState("valid");
        }
      } catch {
        // Network / 5xx — fail open so a flaky backend doesn't lock
        // legitimate maintainers out of signup. Backend re-validates
        // server-side in MembershipClaimServiceImpl.createClaim.
        setResidencyState("error");
      }
    }, 400);
    return () => clearTimeout(handle);
  }, [choice, pickedBuilding, societyFlatNumber]);

  /**
   * Signup is a two-step transaction for the SOCIETY path:
   *  1. Create the auth account (POST /auth/register)
   *  2. Log in with the new credentials so we have a JWT
   *  3. POST the membership claim
   *  4. Redirect to the pending-claim screen
   *
   * For TENANT / OWNER paths it's the existing single-step flow.
   *
   * We do not catch step 1 failure separately; if register fails the
   * user sees the error and stays on the form. If steps 2 or 3 fail
   * after a successful register, we still send them to /login with a
   * toast — they can submit the claim from inside the app later.
   */
  // Three submission shapes:
  //   - plain: TENANT or new-building OWNER → register and stop here
  //   - claim: SOCIETY (maintainer) OR EXISTING_FLAT OWNER → register
  //            + auto-login + post the appropriate claim
  // The auth-side register call is identical for both claim shapes;
  // only the claim payload's requestedRole and the success copy differ.
  const submitsClaim =
    choice === "SOCIETY" || (choice === "OWNER" && ownerMode === "EXISTING_FLAT");
  const claimRole: MembershipClaimRole | null =
    choice === "SOCIETY"
      ? "MAINTAINER"
      : choice === "OWNER" && ownerMode === "EXISTING_FLAT"
        ? "FLAT_OWNER"
        : null;

  const mutation = useMutation({
    mutationFn: async (req: Parameters<typeof authApi.register>[0]) => {
      await authApi.register(req);
      if (!submitsClaim || !claimRole) return { kind: "plain" as const };
      // Claim path — auto-login, then submit the appropriate claim.
      const auth = await authApi.login({
        userName: req.userName,
        password: req.userPassword,
      });
      setSession(auth);
      await claimsApi.create({
        buildingId: pickedBuilding!.buildingId,
        requestedRole: claimRole,
        // SOCIETY maintainer: flat number is optional metadata.
        // FLAT_OWNER: flat number is required (backend enforces too).
        claimedFlatNumber: societyFlatNumber.trim() || undefined,
        applicantNote: societyNote.trim() || undefined,
      });
      return { kind: "claim" as const, claimRole };
    },
    onSuccess: (result) => {
      if (result.kind === "claim") {
        toast({
          title: "Claim submitted",
          description:
            "We've notified the building owner. You'll get access as soon as they approve.",
        });
        // /pending-claim is role-agnostic — works for both SOCIETY
        // (TENANT-role) and EXISTING_FLAT (OWNER-role) claimants.
        navigate("/pending-claim");
      } else {
        toast({
          title: "Account created",
          description: "Sign in with your new credentials.",
        });
        navigate("/login");
      }
    },
    onError: (e) => {
      toast({
        variant: "destructive",
        title: "Couldn't create account",
        description: extractErrorMessage(e),
      });
    },
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setClientError(null);
    const fd = new FormData(e.currentTarget);
    const password = String(fd.get("password") ?? "");
    const confirmPassword = String(fd.get("confirmPassword") ?? "");
    const phone = String(fd.get("phone") ?? "").trim();
    const address = String(fd.get("address") ?? "").trim();

    // H18 — password rules + confirm-password match.
    if (!PASSWORD_REGEX.test(password)) {
      setClientError(
        "Password needs at least 8 characters, including an uppercase, a lowercase, and a digit.",
      );
      return;
    }
    if (password !== confirmPassword) {
      setClientError("Passwords don't match — please retype.");
      return;
    }
    // H19 — phone format (optional field, but if provided must be valid).
    if (phone && !PHONE_REGEX.test(phone)) {
      setClientError(
        "Phone number looks off. Use 10 digits, with country code if international (e.g. +91-9876543210).",
      );
      return;
    }
    // SOCIETY / EXISTING_FLAT path — the claim payload has to be
    // complete before we burn the register call. Catching missing
    // fields here means a failed claim doesn't leave an orphaned
    // account.
    if (submitsClaim) {
      if (!pickedBuilding) {
        setClientError("Pick the building from the search results first.");
        return;
      }
      // FLAT_OWNER claims REQUIRE a flat number (backend uses it to
      // find the specific flat to reassign).
      if (
        choice === "OWNER" &&
        ownerMode === "EXISTING_FLAT" &&
        !societyFlatNumber.trim()
      ) {
        setClientError(
          "Flat number is required so we know which flat you're claiming as the owner.",
        );
        return;
      }
      // SOCIETY maintainer — flat number is NOW mandatory + the
      // applicant must be a current resident (flat exists in this
      // building AND has a tenant). Backend re-checks both, but
      // catching them here means a fraudulent or mistaken applicant
      // doesn't leave an orphan auth account behind.
      if (choice === "SOCIETY") {
        if (!societyFlatNumber.trim()) {
          setClientError(
            "Flat number is required — the maintainer must live in the building.",
          );
          return;
        }
        if (residencyState === "checking") {
          setClientError("Hold on — we're verifying your flat details.");
          return;
        }
        if (residencyState === "missing") {
          setClientError(
            `No flat numbered "${societyFlatNumber.trim()}" exists in ${pickedBuilding.buildingName}. Double-check the flat number with the owner.`,
          );
          return;
        }
        if (residencyState === "vacant") {
          setClientError(
            `Flat ${societyFlatNumber.trim()} in ${pickedBuilding.buildingName} is currently vacant. Only a current resident can apply to maintain the society.`,
          );
          return;
        }
        // residencyState === "error" → backend was unreachable; let
        // submit fall through and let the server-side validation
        // surface the real error message.
      }
    }
    // Defence-in-depth: the Create-Account button is disabled when
    // !acceptedTerms, but a determined user could re-enable it in
    // devtools. Block on submit too.
    if (!acceptedTerms) {
      setClientError(
        "Please read and accept the Terms & Conditions to continue.",
      );
      return;
    }

    // Convert blank-strings to undefined so the backend treats them as
    // "field omitted" rather than "field present and invalid". The
    // RegisterRequest has @Pattern annotations on phone / gender /
    // maritalStatus / tenantType that reject an empty string because
    // it doesn't match the pattern. lastName has no @NotBlank so an
    // empty string is technically fine for it, but undefined keeps the
    // payload shape consistent and avoids a Jackson "" -> null surprise
    // on the server side.
    const blank = (v: string | undefined | null): string | undefined => {
      if (v == null) return undefined;
      const t = String(v).trim();
      return t === "" ? undefined : t;
    };

    const submittedUserName = String(fd.get("userName") ?? "");
    // SOCIETY signups don't ask for email (the user is typically a
    // pre-existing resident who'll fill profile bits later). Backend
    // RegisterRequest has @NotBlank @Email on the field though, so we
    // synthesize a placeholder `<userName>@society.anirudhhomes.in`
    // that satisfies the format check. The user can replace it from
    // /app/profile any time after sign-in.
    // Both claim flows (SOCIETY + OWNER-EXISTING_FLAT) hide the email
    // field; synthesise a placeholder that satisfies the backend's
    // @Email validator. Users can replace it later from /profile.
    const submittedEmail = submitsClaim
      ? `${submittedUserName.trim()}@society.anirudhhomes.in`
      : String(fd.get("email") ?? "");

    mutation.mutate({
      userName: submittedUserName,
      userPassword: password,
      userRole: authRole,
      email: submittedEmail,
      firstName: String(fd.get("firstName") ?? ""),
      lastName: blank(String(fd.get("lastName") ?? "")),
      phone: blank(phone),
      // Strip the UNSELECTED sentinel + blank address so the request
      // body matches the backend's "optional → omit" expectation.
      gender: gender === UNSELECTED ? undefined : gender,
      address: address || undefined,
      maritalStatus: maritalStatus === UNSELECTED ? undefined : maritalStatus,
      tenantType: tenantType === UNSELECTED ? undefined : tenantType,
    });
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-secondary/30 relative overflow-hidden">
      {/* One ambient gradient orb — matches the Login page so signup
          and signin feel like the same brand surface, just with the
          orb on the opposite side for visual variety. */}
      <div
        aria-hidden
        className="pointer-events-none absolute -top-32 right-[-10%] size-[480px] rounded-full bg-gradient-to-br from-sky-400/20 via-teal-400/15 to-transparent blur-3xl animate-ambient-drift-slower"
      />
      <div className="w-full max-w-2xl relative animate-fade-in">
        <div className="flex justify-center mb-6">
          <Logo size="lg" />
        </div>

        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-2xl sm:text-3xl font-bold tracking-tight">
            Create your account
          </h1>
          <p className="text-muted-foreground mt-1.5">
            Two minutes. Then you're in.
          </p>

          {/*
            Three-tile role picker. The SOCIETY tile is for users who
            want to manage a building's society books as the maintainer
            — the account is created as TENANT and a MAINTAINER claim
            is filed against a specific building. The building owner
            approves from their dashboard. Residents who just want to
            see the books use the owner-shared public ledger URL
            (/society/view/<token>) — no account needed for that
            read-only view.
          */}
          <div className="grid sm:grid-cols-3 gap-3 mt-6">
            <RoleCard
              label="I'm renting"
              desc="Find a home and pay rent online"
              icon={Home}
              active={choice === "TENANT"}
              onClick={() => setChoice("TENANT")}
            />
            <RoleCard
              label="I'm an owner"
              desc="List my property and manage tenants"
              icon={Building2}
              active={choice === "OWNER"}
              onClick={() => setChoice("OWNER")}
            />
            <RoleCard
              label="I'm a maintainer"
              desc="Manage a society's books, dues, and expenses"
              icon={Users}
              active={choice === "SOCIETY"}
              onClick={() => setChoice("SOCIETY")}
            />
          </div>

          {choice === "SOCIETY" && (
            <SocietyClaimPanel
              picked={pickedBuilding}
              onPick={setPickedBuilding}
              flatNumber={societyFlatNumber}
              onFlatNumberChange={setSocietyFlatNumber}
              note={societyNote}
              onNoteChange={setSocietyNote}
              residencyState={residencyState}
            />
          )}

          {/* OWNER sub-mode toggle. The two paths look identical to the
              backend at this point (auth-side role=OWNER); they only
              diverge at submit time:
                * NEW_BUILDING — straight register, owner lands on /owner
                  and clicks Add building.
                * EXISTING_FLAT — register + FLAT_OWNER claim against
                  (buildingId, flatNumber). Building owner approves and
                  flat.flatOwnerId swaps to this new user. */}
          {choice === "OWNER" && (
            <OwnerModePanel
              mode={ownerMode}
              onModeChange={setOwnerMode}
              picked={pickedBuilding}
              onPick={setPickedBuilding}
              flatNumber={societyFlatNumber}
              onFlatNumberChange={setSocietyFlatNumber}
              note={societyNote}
              onNoteChange={setSocietyNote}
            />
          )}

          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            {/* SOCIETY path renders the minimum the backend needs
                (userName + password + email + firstName) plus the
                claim fields above. Profile bits like phone, gender,
                marital status, tenant type, address can be completed
                later from /app/profile — most society members don't
                need them at signup, and dragging through the full
                tenant form feels heavy for a "just let me join the
                society" intent.

                TENANT / OWNER paths still see the full form because
                they're filling profile data the rest of the app uses
                (search-by-tenant-type, agreement PDF address). */}
            {submitsClaim ? (
              // SOCIETY and OWNER+EXISTING_FLAT both submit a claim and
              // both get the same slim form. Profile data isn't needed
              // up-front for a "just attach me to this flat / society"
              // workflow — they can fill the rest in later from
              // /app/profile (or /owner/profile for flat-owners).
              <>
                <p className="text-xs text-muted-foreground bg-secondary/40 border border-border rounded-md px-3 py-2">
                  Quick signup. You can complete the rest of your
                  profile (email, phone, address, etc.) any time after
                  sign-in.
                </p>
                <div className="grid sm:grid-cols-2 gap-4">
                  <Field label="Username" name="userName" required />
                  <Field label="First name" name="firstName" required />
                </div>
              </>
            ) : (
              <>
                <div className="grid sm:grid-cols-2 gap-4">
                  <Field label="First name" name="firstName" required />
                  <Field label="Last name" name="lastName" required />
                </div>
                <div className="grid sm:grid-cols-2 gap-4">
                  <Field label="Username" name="userName" required />
                  <Field
                    label="Phone"
                    name="phone"
                    type="tel"
                    placeholder="+91-9876543210"
                    hint="Optional. Used for SMS/WhatsApp alerts."
                  />
                </div>
                <Field label="Email" name="email" type="email" required />
              </>
            )}

            {/* ── Optional profile fields ───────────────────────────
                Both selects + the address textarea are optional.
                User can leave them blank during signup and complete
                them later from /app/profile (tenant) or /owner/profile.
                We still gather them here because (a) most users fill
                them in, and (b) tenant-search filtering by tenantType
                / maritalStatus needs them populated to be useful. */}
            {/* Gender / marital / tenant type / address — collected
                for TENANT and OWNER signups because the rest of the
                app uses them (tenant-search filters, KYC + lease PDF
                addresses, etc.). Skipped for SOCIETY because society
                members typically don't need these at signup; they
                can complete the profile post-approval from
                /app/profile if they want. */}
            {!submitsClaim && (
              <>
                <div className="grid sm:grid-cols-2 gap-4">
                  <div>
                    <Label htmlFor="gender">Gender</Label>
                    <Select value={gender} onValueChange={setGender}>
                      <SelectTrigger id="gender" className="mt-1.5">
                        <SelectValue placeholder="Select" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UNSELECTED}>Prefer not to say</SelectItem>
                        <SelectItem value="MALE">Male</SelectItem>
                        <SelectItem value="FEMALE">Female</SelectItem>
                        <SelectItem value="OTHER">Other</SelectItem>
                      </SelectContent>
                    </Select>
                    <p className="text-[11px] text-muted-foreground mt-1">
                      Optional.
                    </p>
                  </div>
                  <div>
                    <Label htmlFor="maritalStatus">Marital status</Label>
                    <Select value={maritalStatus} onValueChange={setMaritalStatus}>
                      <SelectTrigger id="maritalStatus" className="mt-1.5">
                        <SelectValue placeholder="Select" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UNSELECTED}>Prefer not to say</SelectItem>
                        <SelectItem value="SINGLE">Single</SelectItem>
                        <SelectItem value="MARRIED">Married</SelectItem>
                        <SelectItem value="DIVORCED">Divorced</SelectItem>
                        <SelectItem value="WIDOWED">Widowed</SelectItem>
                      </SelectContent>
                    </Select>
                    <p className="text-[11px] text-muted-foreground mt-1">
                      Optional.
                    </p>
                  </div>
                </div>

                {/* Tenant-type is only relevant for TENANT users —
                    owners don't categorise themselves as bachelor/
                    family. */}
                {choice === "TENANT" && (
                  <div>
                    <Label htmlFor="tenantType">Tenant type</Label>
                    <Select value={tenantType} onValueChange={setTenantType}>
                      <SelectTrigger id="tenantType" className="mt-1.5">
                        <SelectValue placeholder="Select" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UNSELECTED}>Prefer not to say</SelectItem>
                        <SelectItem value="BACHELOR">
                          Bachelor (looking for PG / shared accommodation)
                        </SelectItem>
                        <SelectItem value="FAMILY">
                          Family (looking for whole-flat tenancy)
                        </SelectItem>
                      </SelectContent>
                    </Select>
                    <p className="text-[11px] text-muted-foreground mt-1">
                      Optional. Some listings filter by this in India.
                    </p>
                  </div>
                )}

                <div>
                  <Label htmlFor="address">Address</Label>
                  <Textarea
                    id="address"
                    name="address"
                    className="mt-1.5"
                    placeholder="Current address — street, city, state, PIN"
                    rows={3}
                    maxLength={4000}
                  />
                  <p className="text-[11px] text-muted-foreground mt-1">
                    Optional. Required later for KYC and rental agreements.
                  </p>
                </div>
              </>
            )}

            {/* ── Passwords with show/hide toggles (mirrors the
                login page UX). Each field has its own toggle so the
                user can independently verify the original and confirm. */}
            <div className="grid sm:grid-cols-2 gap-4">
              <PasswordField
                label="Password"
                name="password"
                show={showPassword}
                onToggleShow={() => setShowPassword((s) => !s)}
                hint="8+ chars · upper · lower · digit"
              />
              <PasswordField
                label="Confirm password"
                name="confirmPassword"
                show={showConfirm}
                onToggleShow={() => setShowConfirm((s) => !s)}
              />
            </div>

            {clientError && (
              <p
                role="alert"
                className="text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md px-3 py-2"
              >
                {clientError}
              </p>
            )}

            {/* T&C accept gate — Create Account stays disabled until
                this is checked. The label text contains an inline
                button that opens the T&C modal, so users can read
                before accepting. */}
            <label className="flex items-start gap-2.5 text-sm cursor-pointer">
              <input
                type="checkbox"
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
                className="mt-0.5 size-4 rounded border-input accent-primary cursor-pointer"
              />
              <span className="text-muted-foreground">
                I have read and accept the{" "}
                <button
                  type="button"
                  onClick={() => setTermsOpen(true)}
                  className="text-primary font-medium hover:underline"
                >
                  Terms &amp; Conditions and Privacy Policy
                </button>
                , including consent to share my Aadhaar and other
                identity details for KYC.
              </span>
            </label>

            <Button
              type="submit"
              size="lg"
              variant="gradient"
              className="w-full mt-2"
              disabled={
                mutation.isPending ||
                !acceptedTerms ||
                // SOCIETY signups gate the Create-Account button on
                // the residency check: until the picked flat is
                // confirmed as occupied (or the backend errors and we
                // fall back to letting the server decide), refuse to
                // submit. Idle = no flat entered yet, checking = wait
                // for the debounced fetch, missing/vacant = bad input.
                (choice === "SOCIETY" &&
                  (residencyState === "checking" ||
                    residencyState === "missing" ||
                    residencyState === "vacant" ||
                    residencyState === "idle"))
              }
            >
              {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
              Create account
            </Button>
          </form>
        </Card>

        <p className="text-sm text-muted-foreground text-center mt-6">
          Already have an account?{" "}
          <Link to="/login" className="text-primary font-medium hover:underline">
            Sign in
          </Link>
        </p>
      </div>

      {/* ── T&C modal ─────────────────────────────────────────────
          Scrollable body so we can render the full text without
          forcing a tiny font. "I accept" in the footer also
          checks the box (and closes the modal) as a convenience
          path, so the user doesn't have to scroll back to the
          form to tick. */}
      <Dialog open={termsOpen} onOpenChange={setTermsOpen}>
        <DialogContent className="max-w-2xl max-h-[85vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle>Terms &amp; Conditions and Privacy Policy</DialogTitle>
            <DialogDescription>
              Please read the following before creating your account.
            </DialogDescription>
          </DialogHeader>
          <div className="flex-1 overflow-y-auto pr-2 -mr-2">
            <TermsAndConditionsContent />
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Close</Button>
            </DialogClose>
            <Button
              variant="gradient"
              onClick={() => {
                setAcceptedTerms(true);
                setTermsOpen(false);
              }}
            >
              I accept
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/**
 * Sub-form shown when the user picks "I'm a maintainer". Collects:
 *
 *   1. Building — searched via the unauth /properties/buildings/search
 *      endpoint. The user types, picks a result, we lock in the
 *      buildingId. Free-text is intentionally not allowed — building
 *      names aren't unique across cities, and an owner has to have
 *      already added the building.
 *   2. Flat number — optional metadata for the owner ("I'm in flat
 *      201" gives them a recognisable cross-reference). Not required;
 *      maintainers don't have to live in the building.
 *   3. Note to owner — free-text context.
 *
 * The resident / "maintainee" path was removed: residents see society
 * data via the owner-shared public ledger URL (no account needed for
 * that read-only view), so requiring registration was just friction.
 */
/**
 * Sub-form shown when the user picks "I'm an owner". Carries the
 * Yes/No "do you own a flat in an existing building" toggle plus,
 * when Yes, the BuildingPicker + flat-number + note for the
 * FLAT_OWNER claim. When No, nothing extra renders here — the user
 * goes through the standard new-building owner registration and
 * adds buildings post-signup from their dashboard.
 *
 * <p>V8 semantics: each flat carries its own flat_owner_id. The
 * EXISTING_FLAT path submits a FLAT_OWNER claim that, on approval
 * by the building owner, sets flat.flat_owner_id to this user's
 * authUserId AND (when the flat is currently vacant) binds them as
 * tenant too — owner-occupier default.
 */
function OwnerModePanel({
  mode,
  onModeChange,
  picked,
  onPick,
  flatNumber,
  onFlatNumberChange,
  note,
  onNoteChange,
}: {
  mode: "NEW_BUILDING" | "EXISTING_FLAT";
  onModeChange: (m: "NEW_BUILDING" | "EXISTING_FLAT") => void;
  picked: BuildingResponseDTO | null;
  onPick: (b: BuildingResponseDTO | null) => void;
  flatNumber: string;
  onFlatNumberChange: (v: string) => void;
  note: string;
  onNoteChange: (v: string) => void;
}) {
  return (
    <div className="mt-4 rounded-xl border border-primary/30 bg-primary/5 p-4 space-y-4">
      <div>
        <p className="text-sm font-medium">
          Are you the owner of a flat in an existing building?
        </p>
        <div className="grid grid-cols-2 gap-2 mt-2">
          <button
            type="button"
            onClick={() => onModeChange("EXISTING_FLAT")}
            className={cn(
              "text-left p-3 rounded-lg border-2 transition-all",
              mode === "EXISTING_FLAT"
                ? "border-primary bg-primary/10"
                : "border-border bg-card hover:border-primary/40",
            )}
          >
            <div className="font-semibold text-sm">Yes</div>
            <div className="text-[11px] text-muted-foreground mt-0.5">
              I own a specific flat in a building someone else added
            </div>
          </button>
          <button
            type="button"
            onClick={() => onModeChange("NEW_BUILDING")}
            className={cn(
              "text-left p-3 rounded-lg border-2 transition-all",
              mode === "NEW_BUILDING"
                ? "border-primary bg-primary/10"
                : "border-border bg-card hover:border-primary/40",
            )}
          >
            <div className="font-semibold text-sm">No</div>
            <div className="text-[11px] text-muted-foreground mt-0.5">
              I'm registering a new building / property from scratch
            </div>
          </button>
        </div>
      </div>

      {mode === "EXISTING_FLAT" && (
        <>
          {/* Building picker */}
          <BuildingPicker picked={picked} onPick={onPick} />

          {/* Flat number — REQUIRED for FLAT_OWNER claim */}
          <div>
            <Label htmlFor="owner-flat">
              Flat number <span className="text-destructive">*</span>
            </Label>
            <Input
              id="owner-flat"
              name="claimedFlatNumber"
              value={flatNumber}
              onChange={(e) => onFlatNumberChange(e.target.value)}
              placeholder="e.g. 203"
              className="mt-1.5"
              maxLength={32}
              required
            />
            <p className="text-[11px] text-muted-foreground mt-1">
              The flat you own. The building owner will approve and
              you'll be marked as the owner from then on.
            </p>
          </div>

          {/* Optional note to the building owner */}
          <div>
            <Label htmlFor="owner-note">
              Note to the building owner (optional)
            </Label>
            <Textarea
              id="owner-note"
              value={note}
              onChange={(e) => onNoteChange(e.target.value)}
              placeholder="When you bought the flat, sale-deed reference, anything that helps them recognise the transfer."
              rows={2}
              maxLength={500}
              className="mt-1.5"
            />
          </div>

          <p className="text-[11px] text-muted-foreground border-t border-primary/20 pt-2">
            The building owner sees your request in their dashboard.
            You're marked as the flat owner once they approve.
          </p>
        </>
      )}
    </div>
  );
}

function SocietyClaimPanel({
  picked,
  onPick,
  flatNumber,
  onFlatNumberChange,
  note,
  onNoteChange,
  residencyState,
}: {
  picked: BuildingResponseDTO | null;
  onPick: (b: BuildingResponseDTO | null) => void;
  flatNumber: string;
  onFlatNumberChange: (v: string) => void;
  note: string;
  onNoteChange: (v: string) => void;
  residencyState:
    | "idle"
    | "checking"
    | "valid"
    | "missing"
    | "vacant"
    | "error";
}) {
  return (
    <div className="mt-4 rounded-xl border border-primary/30 bg-primary/5 p-4 space-y-4">
      <div>
        <p className="text-sm font-medium">Apply to maintain a society</p>
        <p className="text-[11px] text-muted-foreground mt-0.5">
          Pick the building you want to manage and the flat you live
          in. The maintainer has to be a current resident. The
          building owner approves your request from their dashboard.
        </p>
      </div>

      {/* Building picker */}
      <BuildingPicker picked={picked} onPick={onPick} />

      {/* Flat number — REQUIRED. The maintainer must live in the
          building (be the current tenant of one of its flats). The
          form gates the Create-Account button on the live preview
          check confirming the flat exists + is occupied. */}
      <div>
        <Label htmlFor="society-flat">
          Flat number you live in{" "}
          <span className="text-destructive">*</span>
        </Label>
        <Input
          id="society-flat"
          name="claimedFlatNumber"
          value={flatNumber}
          onChange={(e) => onFlatNumberChange(e.target.value)}
          placeholder="e.g. 203"
          className="mt-1.5"
          maxLength={32}
          required
          // Style the input based on the residency check result —
          // a green border once verified, red on missing/vacant.
          aria-invalid={
            residencyState === "missing" || residencyState === "vacant"
          }
        />
        {/* Inline residency-check status. Hidden when idle (no input
            yet) or for SOCIETY-irrelevant choices. */}
        <ResidencyHint state={residencyState} flatNumber={flatNumber.trim()} />
      </div>

      {/* Optional applicant note */}
      <div>
        <Label htmlFor="society-note">Note to the owner (optional)</Label>
        <Textarea
          id="society-note"
          value={note}
          onChange={(e) => onNoteChange(e.target.value)}
          placeholder="Anything that helps them recognise you — when you moved in, who referred you, etc."
          rows={2}
          maxLength={500}
          className="mt-1.5"
        />
      </div>

      <p className="text-[11px] text-muted-foreground border-t border-primary/20 pt-2">
        The building owner will see your request in their dashboard.
        You get full access once they approve.
      </p>
    </div>
  );
}

/**
 * Inline status hint for the maintainer-signup flat-number field.
 * Renders nothing for {@code idle} so the form starts quiet; flips to
 * a spinner during {@code checking}, a green check on {@code valid},
 * and a red error message on {@code missing} / {@code vacant}.
 * {@code error} (preview endpoint unreachable) is shown as a soft
 * warning — submit is still allowed, the backend will catch real
 * problems server-side.
 */
function ResidencyHint({
  state,
  flatNumber,
}: {
  state: "idle" | "checking" | "valid" | "missing" | "vacant" | "error";
  flatNumber: string;
}) {
  if (state === "idle") {
    return (
      <p className="text-[11px] text-muted-foreground mt-1">
        Enter the flat you currently live in. We'll verify it before
        creating your account.
      </p>
    );
  }
  if (state === "checking") {
    return (
      <p className="text-[11px] text-muted-foreground mt-1 inline-flex items-center gap-1.5">
        <Loader2 className="size-3 animate-spin" />
        Verifying flat {flatNumber}…
      </p>
    );
  }
  if (state === "valid") {
    return (
      <p className="text-[11px] text-success mt-1 inline-flex items-center gap-1.5">
        <Check className="size-3" />
        Flat {flatNumber} verified — you're a current resident.
      </p>
    );
  }
  if (state === "missing") {
    return (
      <p className="text-[11px] text-destructive mt-1">
        No flat numbered "{flatNumber}" in this building. Double-check
        the number with the owner.
      </p>
    );
  }
  if (state === "vacant") {
    return (
      <p className="text-[11px] text-destructive mt-1">
        Flat {flatNumber} is currently vacant. Only a current resident
        can apply to maintain the society.
      </p>
    );
  }
  // state === "error" — preview endpoint unreachable; let the backend
  // be the authoritative check. We don't block submission here.
  return (
    <p className="text-[11px] text-warning mt-1">
      Couldn't verify the flat right now. You can submit — we'll
      double-check it when your account is created.
    </p>
  );
}

/**
 * Searchable building picker. Debounces the query so we don't hammer
 * /properties/buildings/search on every keystroke; shows up to 8
 * matches in a dropdown list. Picking a row locks the buildingId and
 * collapses the dropdown.
 */
function BuildingPicker({
  picked,
  onPick,
}: {
  picked: BuildingResponseDTO | null;
  onPick: (b: BuildingResponseDTO | null) => void;
}) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<BuildingResponseDTO[]>([]);
  const [searching, setSearching] = useState(false);

  // Debounce: 300ms after the user stops typing.
  useEffect(() => {
    if (!q.trim() || picked) {
      setResults([]);
      return;
    }
    const handle = setTimeout(async () => {
      setSearching(true);
      try {
        const r = await propertiesApi.buildings.search(q.trim(), undefined, 8);
        setResults(r);
      } catch {
        // Silent — building search not finding hits is the same UX as
        // an error here. The user can keep typing or refresh.
        setResults([]);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => clearTimeout(handle);
  }, [q, picked]);

  if (picked) {
    return (
      <div>
        <Label>Building</Label>
        <div className="mt-1.5 flex items-center justify-between gap-2 rounded-md border border-primary/40 bg-primary/10 px-3 py-2">
          <div className="min-w-0">
            <p className="font-semibold text-sm truncate">
              <Check className="size-3.5 inline-block text-success mr-1" />
              {picked.buildingName}
            </p>
            <p className="text-[11px] text-muted-foreground truncate">
              {picked.buildingCity}
              {picked.buildingState ? `, ${picked.buildingState}` : ""}
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onPick(null)}
          >
            Change
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <Label htmlFor="society-building">Building</Label>
      <div className="relative mt-1.5">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
        <Input
          id="society-building"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search by building name, city, area…"
          className="pl-9"
          autoComplete="off"
        />
      </div>
      {q && (
        <div className="mt-2 rounded-md border border-border bg-card max-h-56 overflow-y-auto">
          {searching ? (
            <p className="text-xs text-muted-foreground px-3 py-2">
              <Loader2 className="size-3 inline-block animate-spin mr-1" />
              Searching…
            </p>
          ) : results.length === 0 ? (
            <p className="text-xs text-muted-foreground px-3 py-2">
              No matches. Ask your owner to add the building first.
            </p>
          ) : (
            results.map((b) => (
              <button
                key={b.buildingId}
                type="button"
                onClick={() => {
                  onPick(b);
                  setQ("");
                }}
                className="w-full text-left px-3 py-2 hover:bg-secondary/60 text-sm border-b border-border/40 last:border-b-0"
              >
                <p className="font-medium truncate">{b.buildingName}</p>
                <p className="text-[11px] text-muted-foreground truncate">
                  {b.buildingCity}
                  {b.buildingState ? `, ${b.buildingState}` : ""}
                </p>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function RoleCard({
  label,
  desc,
  icon: Icon,
  active,
  onClick,
}: {
  label: string;
  desc: string;
  icon: React.ComponentType<{ className?: string }>;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "text-left p-4 rounded-xl border-2 transition-all",
        active
          ? "border-primary bg-primary/5 ring-4 ring-primary/10"
          : "border-border hover:border-primary/40 hover:bg-secondary/40",
      )}
    >
      <Icon className={cn("size-5 mb-2", active ? "text-primary" : "text-muted-foreground")} />
      <div className="font-display font-semibold">{label}</div>
      <div className="text-xs text-muted-foreground mt-0.5">{desc}</div>
    </button>
  );
}

function Field({
  label,
  name,
  type = "text",
  required,
  minLength,
  placeholder,
  hint,
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  minLength?: number;
  placeholder?: string;
  hint?: string;
}) {
  return (
    <div>
      <Label htmlFor={name}>{label}</Label>
      <Input
        id={name}
        name={name}
        type={type}
        required={required}
        minLength={minLength}
        placeholder={placeholder}
        className="mt-1.5"
      />
      {hint && (
        <p className="text-[11px] text-muted-foreground mt-1">{hint}</p>
      )}
    </div>
  );
}

/**
 * Password input with an inline show/hide eye toggle. Mirrors the
 * pattern used on the login page so users get a consistent UX
 * across the two auth flows. Always renders `required minLength=8`
 * — the backend has the same constraints, but having them on the
 * native <input> gives the browser its built-in validation UI for
 * free.
 */
function PasswordField({
  label,
  name,
  show,
  onToggleShow,
  hint,
}: {
  label: string;
  name: string;
  show: boolean;
  onToggleShow: () => void;
  hint?: string;
}) {
  return (
    <div>
      <Label htmlFor={name}>{label}</Label>
      <div className="relative mt-1.5">
        <Input
          id={name}
          name={name}
          type={show ? "text" : "password"}
          required
          minLength={8}
          autoComplete={name === "password" ? "new-password" : "new-password"}
          className="pr-10"
        />
        <button
          type="button"
          onClick={onToggleShow}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          aria-label={show ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
        >
          {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      {hint && (
        <p className="text-[11px] text-muted-foreground mt-1">{hint}</p>
      )}
    </div>
  );
}
