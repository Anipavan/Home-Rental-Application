import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuthStore } from "@/stores/auth-store";
import { authApi } from "@/lib/api/auth";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };
  const setSession = useAuthStore((s) => s.setSession);
  const [show, setShow] = useState(false);

  const mutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      setSession(data);
      const dest =
        location.state?.from ??
        (data.role === "OWNER" ? "/owner" : data.role === "ADMIN" ? "/admin" : "/app");
      toast({ title: `Welcome back, ${data.userName}` });
      navigate(dest, { replace: true });
    },
    onError: (e) => {
      toast({
        variant: "destructive",
        title: "Sign-in failed",
        description: extractErrorMessage(e, "Check your username and password."),
      });
    },
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    mutation.mutate({
      userName: String(fd.get("userName") ?? ""),
      password: String(fd.get("password") ?? ""),
    });
  }

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      <div className="flex flex-col p-6 sm:p-10">
        <Logo />
        <div className="flex-1 flex items-center justify-center py-12">
          <div className="w-full max-w-md">
            <h1 className="font-display text-3xl font-bold tracking-tight">
              Welcome back
            </h1>
            <p className="text-muted-foreground mt-1.5">
              Sign in to manage your home, payments and maintenance.
            </p>

            <Card className="mt-8 p-6 sm:p-7">
              <form onSubmit={onSubmit} className="space-y-4">
                <div>
                  <Label htmlFor="userName">Username</Label>
                  <Input
                    id="userName"
                    name="userName"
                    autoComplete="username"
                    required
                    className="mt-1.5"
                    placeholder="aanya.mehta"
                  />
                </div>
                <div>
                  <div className="flex items-center justify-between">
                    <Label htmlFor="password">Password</Label>
                    <Link
                      to="/forgot-password"
                      className="text-xs text-primary hover:underline"
                    >
                      Forgot?
                    </Link>
                  </div>
                  <div className="relative mt-1.5">
                    <Input
                      id="password"
                      name="password"
                      type={show ? "text" : "password"}
                      autoComplete="current-password"
                      required
                      className="pr-10"
                    />
                    <button
                      type="button"
                      onClick={() => setShow((s) => !s)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      aria-label={show ? "Hide password" : "Show password"}
                    >
                      {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                    </button>
                  </div>
                </div>
                <Button
                  type="submit"
                  size="lg"
                  variant="gradient"
                  className="w-full"
                  disabled={mutation.isPending}
                >
                  {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
                  Sign in
                </Button>
              </form>
            </Card>

            <p className="text-sm text-muted-foreground text-center mt-6">
              New to Hearth?{" "}
              <Link to="/register" className="text-primary font-medium hover:underline">
                Create an account
              </Link>
            </p>
          </div>
        </div>
      </div>

      <div className="hidden lg:block relative bg-gradient-to-br from-indigo-700 via-violet-700 to-fuchsia-700 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,rgba(255,255,255,0.18),transparent_60%)]" />
        <div className="absolute inset-0 bg-grid-light bg-[size:48px_48px] opacity-10" />
        <div className="relative h-full flex flex-col justify-end p-12 text-white">
          <blockquote className="max-w-md">
            <p className="font-display text-2xl leading-snug">
              "I paid my deposit through PhonePe and got the keys the next
              morning. Hearth made renting feel like a Sunday."
            </p>
            <footer className="mt-5 flex items-center gap-3">
              <div className="size-10 rounded-full bg-white/20 grid place-items-center font-semibold">
                AM
              </div>
              <div>
                <div className="font-semibold">Aanya Mehta</div>
                <div className="text-sm text-white/70">Tenant · Bengaluru</div>
              </div>
            </footer>
          </blockquote>
        </div>
      </div>
    </div>
  );
}
