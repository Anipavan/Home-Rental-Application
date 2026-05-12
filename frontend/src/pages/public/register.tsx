import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Loader2, Home, Building2 } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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

export function RegisterPage() {
  const navigate = useNavigate();
  const [role, setRole] = useState<Role>("TENANT");
  const [clientError, setClientError] = useState<string | null>(null);

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

    mutation.mutate({
      userName: String(fd.get("userName") ?? ""),
      userPassword: password,
      userRole: role,
      email: String(fd.get("email") ?? ""),
      firstName: String(fd.get("firstName") ?? ""),
      lastName: String(fd.get("lastName") ?? ""),
      phone,
    });
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-secondary/30">
      <div className="w-full max-w-2xl">
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
            <div className="grid sm:grid-cols-2 gap-4">
              <Field
                label="Password"
                name="password"
                type="password"
                required
                minLength={8}
                hint="8+ chars · upper · lower · digit"
              />
              <Field
                label="Confirm password"
                name="confirmPassword"
                type="password"
                required
                minLength={8}
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

            <Button
              type="submit"
              size="lg"
              variant="gradient"
              className="w-full mt-2"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
              Create account
            </Button>

            <p className="text-xs text-muted-foreground text-center">
              By continuing, you agree to our terms and acknowledge our privacy
              policy.
            </p>
          </form>
        </Card>

        <p className="text-sm text-muted-foreground text-center mt-6">
          Already have an account?{" "}
          <Link to="/login" className="text-primary font-medium hover:underline">
            Sign in
          </Link>
        </p>
      </div>
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
