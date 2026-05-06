import { Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/lib/api/auth";
import { extractErrorMessage } from "@/lib/api/client";
import { toast } from "@/hooks/use-toast";

export function ForgotPasswordPage() {
  const mutation = useMutation({
    mutationFn: (email: string) => authApi.forgotPassword(email),
    onSuccess: () =>
      toast({
        title: "Check your inbox",
        description: "If an account exists, we've emailed a reset link.",
      }),
    onError: (e) =>
      toast({
        variant: "destructive",
        title: "Could not send reset link",
        description: extractErrorMessage(e),
      }),
  });

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    mutation.mutate(String(fd.get("email") ?? ""));
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-secondary/30">
      <div className="w-full max-w-md">
        <div className="flex justify-center mb-6">
          <Logo size="lg" />
        </div>
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-2xl font-bold tracking-tight">
            Reset your password
          </h1>
          <p className="text-muted-foreground mt-1.5 text-sm">
            Enter your email and we'll send a link to reset your password.
          </p>
          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            <div>
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                name="email"
                type="email"
                required
                className="mt-1.5"
                placeholder="you@example.com"
              />
            </div>
            <Button
              type="submit"
              size="lg"
              variant="gradient"
              className="w-full"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? <Loader2 className="animate-spin" /> : null}
              Send reset link
            </Button>
          </form>
        </Card>
        <p className="text-sm text-muted-foreground text-center mt-6">
          Remembered it?{" "}
          <Link to="/login" className="text-primary font-medium hover:underline">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
