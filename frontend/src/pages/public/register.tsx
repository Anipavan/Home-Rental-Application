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

export function RegisterPage() {
  const navigate = useNavigate();
  const [role, setRole] = useState<Role>("TENANT");

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
    const fd = new FormData(e.currentTarget);
    mutation.mutate({
      userName: String(fd.get("userName") ?? ""),
      userPassword: String(fd.get("password") ?? ""),
      userRole: role,
      email: String(fd.get("email") ?? ""),
      firstName: String(fd.get("firstName") ?? ""),
      lastName: String(fd.get("lastName") ?? ""),
      phone: String(fd.get("phone") ?? ""),
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
              <Field label="Phone" name="phone" type="tel" />
            </div>
            <Field label="Email" name="email" type="email" required />
            <Field label="Password" name="password" type="password" required minLength={6} />

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
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
  minLength?: number;
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
        className="mt-1.5"
      />
    </div>
  );
}
