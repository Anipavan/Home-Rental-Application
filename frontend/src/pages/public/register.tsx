import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Eye, EyeOff, Loader2, Home, Building2 } from "lucide-react";
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
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import type { Role } from "@/types/api";

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

export function RegisterPage() {
  const navigate = useNavigate();
  const [role, setRole] = useState<Role>("TENANT");
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

  const mutation = useMutation({
    mutationFn: authApi.register,
    onSuccess: () => {
      toast({
        title: "Account created",
        description: "Sign in with your new credentials.",
      });
      navigate("/login");
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
    // Defence-in-depth: the Create-Account button is disabled when
    // !acceptedTerms, but a determined user could re-enable it in
    // devtools. Block on submit too.
    if (!acceptedTerms) {
      setClientError(
        "Please read and accept the Terms & Conditions to continue.",
      );
      return;
    }

    mutation.mutate({
      userName: String(fd.get("userName") ?? ""),
      userPassword: password,
      userRole: role,
      email: String(fd.get("email") ?? ""),
      firstName: String(fd.get("firstName") ?? ""),
      lastName: String(fd.get("lastName") ?? ""),
      phone,
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

          <div className="grid grid-cols-2 gap-3 mt-6">
            <RoleCard
              label="I'm renting"
              desc="Find a home and pay rent online"
              icon={Home}
              active={role === "TENANT"}
              onClick={() => setRole("TENANT")}
            />
            <RoleCard
              label="I'm an owner"
              desc="List my property and manage tenants"
              icon={Building2}
              active={role === "OWNER"}
              onClick={() => setRole("OWNER")}
            />
          </div>

          <form onSubmit={onSubmit} className="mt-6 space-y-4">
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

            {/* ── Optional profile fields ───────────────────────────
                Both selects + the address textarea are optional.
                User can leave them blank during signup and complete
                them later from /app/profile (tenant) or /owner/profile.
                We still gather them here because (a) most users fill
                them in, and (b) tenant-search filtering by tenantType
                / maritalStatus needs them populated to be useful. */}
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

            {/* Tenant-type is only relevant for TENANT users — owners
                don't categorise themselves as bachelor/family. Hiding
                it for owners keeps the form short and the data clean
                (no spurious tenantType=BACHELOR rows for owners). */}
            {role === "TENANT" && (
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
              disabled={mutation.isPending || !acceptedTerms}
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
