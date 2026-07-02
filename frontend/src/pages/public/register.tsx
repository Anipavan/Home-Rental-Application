import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { useAuthStore } from "@/stores/auth-store";

/**
 * Audit H19: India-tolerant phone regex. Accepts:
 *   +91-9876543210
 *   919876543210
 *   9876543210     (assumed Indian 10-digit mobile)
 *   +1 555 123 4567
 */
const PHONE_REGEX = /^\+?[0-9][0-9\s\-]{8,18}[0-9]$/;

/**
 * Audit H18: password strength — 1 upper, 1 lower, 1 digit, 8+ chars.
 * The backend enforces the same rule; mirroring here gives the user
 * an inline error instead of a round-trip toast.
 */
const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/;

/**
 * Phase 4 — Unified signup.
 *
 * <p>Every account is created as TENANT. Post-signup the user lands
 * on {@code /welcome} where they pick "what brings you here" — that
 * screen calls {@code POST /auth/me/role} to upgrade to OWNER for
 * owners / society founders, or submits a MAINTAINER claim.
 *
 * <p>Removed from this page vs. the pre-Phase-4 version:
 * <ul>
 *   <li>The 4-card role selector (moved to /welcome)</li>
 *   <li>OwnerModePanel (EXISTING_FLAT flow — the FLAT_OWNER claim
 *       is now submitted from within the owner dashboard rather
 *       than at signup time)</li>
 *   <li>SocietyClaimPanel + residency-preview check (moved to
 *       /welcome for the MAINTAINER path)</li>
 *   <li>Paid-maintainer registerPending flow — the paywall now
 *       triggers post-signup when the user picks MAINTAINER on
 *       /welcome (deferred wiring; today toggle is OFF so
 *       maintainer claims are free).</li>
 * </ul>
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [termsOpen, setTermsOpen] = useState(false);
  const [clientError, setClientError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: async (req: {
      firstName: string;
      lastName: string;
      userName: string;
      email: string;
      phone: string | null;
      password: string;
    }) => {
      // Step 1 — create the account as TENANT (the safest default).
      // The user picks their real role on /welcome next.
      await authApi.register({
        userName: req.userName,
        userPassword: req.password,
        userRole: "TENANT",
        email: req.email,
        firstName: req.firstName,
        lastName: req.lastName,
        phone: req.phone ?? undefined,
        gender: undefined,
        address: undefined,
        dateOfBirth: undefined,
        maritalStatus: undefined,
        tenantType: undefined,
      });

      // Step 2 — try auto-login. If the admin has enabled email
      // verification, login will reject with EMAIL_VERIFICATION_REQUIRED;
      // that error path routes the user to /verify-email-sent to
      // check their inbox.
      try {
        const auth = await authApi.login({
          userName: req.userName,
          password: req.password,
        });
        setSession(auth);
        return { kind: "welcome" as const };
      } catch (err) {
        const errorCode = (
          err as { response?: { data?: { errorCode?: string } } }
        )?.response?.data?.errorCode;
        if (errorCode === "EMAIL_VERIFICATION_REQUIRED") {
          return { kind: "verify-email" as const, email: req.email };
        }
        // Auto-login failed for another reason — route to /login so
        // they can complete sign-in manually.
        return { kind: "login" as const };
      }
    },
    onSuccess: (result) => {
      if (result.kind === "verify-email") {
        navigate("/verify-email-sent", { state: { email: result.email } });
        return;
      }
      if (result.kind === "login") {
        toast({
          title: "Account created",
          description: "Sign in with your new credentials.",
        });
        navigate("/login");
        return;
      }
      toast({ title: "Welcome — one quick question next" });
      navigate("/welcome");
    },
    onError: (err) => {
      toast({
        variant: "destructive",
        title: "Couldn't create account",
        description: extractErrorMessage(err),
      });
    },
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setClientError(null);
    const fd = new FormData(e.currentTarget);

    const firstName = String(fd.get("firstName") ?? "").trim();
    const lastName = String(fd.get("lastName") ?? "").trim();
    const userName = String(fd.get("userName") ?? "").trim();
    const email = String(fd.get("email") ?? "").trim();
    const phoneRaw = String(fd.get("phone") ?? "").trim();
    const password = String(fd.get("password") ?? "");
    const confirmPassword = String(fd.get("confirmPassword") ?? "");

    if (!firstName || !lastName || !userName || !email || !password) {
      setClientError("Fill in every required field.");
      return;
    }
    if (password !== confirmPassword) {
      setClientError("Passwords don't match.");
      return;
    }
    if (!PASSWORD_REGEX.test(password)) {
      setClientError(
        "Password needs at least 8 characters, including one uppercase, one lowercase, and one digit.",
      );
      return;
    }
    if (phoneRaw && !PHONE_REGEX.test(phoneRaw)) {
      setClientError(
        "Phone number looks off. Include country code, e.g. +91-9876543210.",
      );
      return;
    }
    if (!acceptedTerms) {
      setClientError("Please accept the terms to continue.");
      return;
    }

    mutation.mutate({
      firstName,
      lastName,
      userName,
      email,
      phone: phoneRaw || null,
      password,
    });
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted/30 p-4 py-10">
      <div className="w-full max-w-lg">
        <div className="flex justify-center mb-6">
          <Logo />
        </div>
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-2xl font-bold tracking-tight">
            Create your account
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Two minutes. Then you're in — we'll ask what you're here for on
            the next screen.
          </p>

          <form onSubmit={onSubmit} className="space-y-4 mt-6">
            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="firstName">First name</Label>
                <Input
                  id="firstName"
                  name="firstName"
                  required
                  className="mt-1.5"
                />
              </div>
              <div>
                <Label htmlFor="lastName">Last name</Label>
                <Input
                  id="lastName"
                  name="lastName"
                  required
                  className="mt-1.5"
                />
              </div>
            </div>

            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="userName">Username</Label>
                <Input
                  id="userName"
                  name="userName"
                  autoComplete="username"
                  required
                  className="mt-1.5"
                />
              </div>
              <div>
                <Label htmlFor="phone">
                  Phone{" "}
                  <span className="text-xs text-muted-foreground">
                    (optional)
                  </span>
                </Label>
                <Input
                  id="phone"
                  name="phone"
                  type="tel"
                  placeholder="+91-9876543210"
                  className="mt-1.5"
                />
                <p className="text-[11px] text-muted-foreground mt-1">
                  Used for SMS / WhatsApp alerts.
                </p>
              </div>
            </div>

            <div>
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                className="mt-1.5"
              />
            </div>

            <div>
              <Label htmlFor="password">Password</Label>
              <div className="relative mt-1.5">
                <Input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  required
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
              <p className="text-[11px] text-muted-foreground mt-1">
                8+ characters, 1 upper, 1 lower, 1 digit.
              </p>
            </div>

            <div>
              <Label htmlFor="confirmPassword">Confirm password</Label>
              <div className="relative mt-1.5">
                <Input
                  id="confirmPassword"
                  name="confirmPassword"
                  type={showConfirm ? "text" : "password"}
                  autoComplete="new-password"
                  required
                  className="pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm((s) => !s)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  aria-label={showConfirm ? "Hide password" : "Show password"}
                >
                  {showConfirm ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
            </div>

            <label className="flex items-start gap-2 text-sm cursor-pointer select-none">
              <input
                type="checkbox"
                className="mt-1"
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
              />
              <span>
                I accept the{" "}
                <button
                  type="button"
                  className="text-primary hover:underline"
                  onClick={() => setTermsOpen(true)}
                >
                  Terms &amp; Conditions
                </button>
                .
              </span>
            </label>

            {clientError && (
              <p className="text-sm text-destructive">{clientError}</p>
            )}

            <Button
              type="submit"
              size="lg"
              variant="gradient"
              className="w-full"
              disabled={mutation.isPending || !acceptedTerms}
            >
              {mutation.isPending && (
                <Loader2 className="size-4 animate-spin mr-2" />
              )}
              Create account
            </Button>
          </form>

          <p className="text-sm text-muted-foreground text-center mt-6">
            Already have an account?{" "}
            <Link
              to="/login"
              className="text-primary font-medium hover:underline"
            >
              Sign in
            </Link>
          </p>
        </Card>
      </div>

      <Dialog open={termsOpen} onOpenChange={setTermsOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Terms &amp; Conditions</DialogTitle>
            <DialogDescription>
              What signing up gets you — and what it doesn't.
            </DialogDescription>
          </DialogHeader>
          <TermsAndConditionsContent />
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
              I agree
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
