import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Loader2, CheckCircle2, Eye, EyeOff } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/lib/api/auth";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token") ?? "";

  const [show, setShow] = useState(false);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (newPassword: string) => authApi.resetPassword(token, newPassword),
    onSuccess: () => {
      setDone(true);
      toast({ title: "Password updated", description: "Sign in with your new password." });
      setTimeout(() => navigate("/login"), 1800);
    },
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Couldn't reset password",
        description: extractErrorMessage(e),
      }),
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!token) {
      toast({
        variant: "destructive",
        title: "Reset link is missing",
        description: "Use the link we emailed you, or request a new one.",
      });
      return;
    }
    const fd = new FormData(e.currentTarget);
    const pw = String(fd.get("password") ?? "");
    const confirm = String(fd.get("confirm") ?? "");
    if (pw.length < 6) {
      toast({ variant: "destructive", title: "Password too short" });
      return;
    }
    if (pw !== confirm) {
      toast({ variant: "destructive", title: "Passwords don't match" });
      return;
    }
    mutation.mutate(pw);
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-secondary/30">
      <div className="w-full max-w-md">
        <div className="flex justify-center mb-6">
          <Logo size="lg" />
        </div>
        <Card className="p-6 sm:p-8">
          {done ? (
            <div className="text-center py-2">
              <div className="size-14 rounded-full bg-success/15 grid place-items-center mx-auto">
                <CheckCircle2 className="size-7 text-success" />
              </div>
              <h1 className="font-display text-xl font-bold mt-4">
                Password updated
              </h1>
              <p className="text-muted-foreground text-sm mt-1">
                Redirecting to sign in…
              </p>
            </div>
          ) : (
            <>
              <h1 className="font-display text-2xl font-bold tracking-tight">
                Choose a new password
              </h1>
              <p className="text-muted-foreground mt-1.5 text-sm">
                Make it unique. At least 6 characters.
              </p>
              {!token && (
                <p className="mt-4 rounded-md bg-warning/10 border border-warning/20 text-warning px-3 py-2 text-xs">
                  This link is missing the reset token. Request a new one from
                  the forgot-password page.
                </p>
              )}
              <form onSubmit={onSubmit} className="mt-6 space-y-4">
                <div>
                  <Label htmlFor="password">New password</Label>
                  <div className="relative mt-1.5">
                    <Input
                      id="password"
                      name="password"
                      type={show ? "text" : "password"}
                      required
                      minLength={6}
                      className="pr-10"
                    />
                    <button
                      type="button"
                      onClick={() => setShow((s) => !s)}
                      aria-label={show ? "Hide password" : "Show password"}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    >
                      {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                    </button>
                  </div>
                </div>
                <div>
                  <Label htmlFor="confirm">Confirm password</Label>
                  <Input
                    id="confirm"
                    name="confirm"
                    type={show ? "text" : "password"}
                    required
                    minLength={6}
                    className="mt-1.5"
                  />
                </div>
                <Button
                  type="submit"
                  size="lg"
                  variant="gradient"
                  className="w-full"
                  disabled={mutation.isPending || !token}
                >
                  {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
                  Update password
                </Button>
              </form>
            </>
          )}
        </Card>
        <p className="text-sm text-muted-foreground text-center mt-6">
          <Link to="/login" className="text-primary font-medium hover:underline">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
