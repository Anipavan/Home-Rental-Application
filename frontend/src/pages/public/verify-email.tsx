import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CheckCircle2, Loader2, MailWarning } from "lucide-react";
import { emailVerificationApi } from "@/lib/api/email-verification";
import { extractErrorMessage } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "@/hooks/use-toast";
import type { VerifyEmailResponse } from "@/types/api";

type VerifyState =
  | { kind: "loading" }
  | { kind: "success"; result: VerifyEmailResponse }
  | { kind: "error"; message: string };

/**
 * Landing page for the magic link emailed at signup. Reads the token
 * from the URL, calls POST /auth/verify-email on mount, and renders
 * one of three states:
 *
 *  - loading   : verifying the token
 *  - success   : email confirmed; show "go to login" CTA
 *  - error     : invalid / expired / consumed; show "resend" form
 *
 * The page works whether or not the user is logged in — verification
 * is gated by the token, not by session.
 */
export function VerifyEmailPage() {
  const { token } = useParams<{ token: string }>();
  const [state, setState] = useState<VerifyState>({ kind: "loading" });
  const [resendEmail, setResendEmail] = useState("");
  const [resending, setResending] = useState(false);

  useEffect(() => {
    if (!token) {
      setState({
        kind: "error",
        message: "Verification link is missing the token. Open the link from your email again.",
      });
      return;
    }
    emailVerificationApi
      .verify(token)
      .then((result) => setState({ kind: "success", result }))
      .catch((err) =>
        setState({ kind: "error", message: extractErrorMessage(err) }),
      );
  }, [token]);

  async function handleResend(e: React.FormEvent) {
    e.preventDefault();
    if (!resendEmail.trim()) return;
    setResending(true);
    try {
      await emailVerificationApi.resend(resendEmail.trim());
      toast({
        title: "Verification email sent",
        description: "Check your inbox for a fresh link.",
      });
    } catch (err) {
      toast({
        variant: "destructive",
        title: "Couldn't send verification",
        description: extractErrorMessage(err),
      });
    } finally {
      setResending(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-muted/30 p-4">
      <Card className="w-full max-w-md p-8">
        {state.kind === "loading" && (
          <div className="flex flex-col items-center text-center gap-4">
            <Loader2 className="size-10 text-primary animate-spin" />
            <h1 className="font-display text-xl font-semibold">
              Verifying your email…
            </h1>
            <p className="text-sm text-muted-foreground">
              Just a moment while we confirm the link.
            </p>
          </div>
        )}

        {state.kind === "success" && (
          <div className="flex flex-col items-center text-center gap-4">
            <CheckCircle2 className="size-12 text-green-600" />
            <h1 className="font-display text-xl font-semibold">
              You're verified
            </h1>
            <p className="text-sm text-muted-foreground">
              <b>{state.result.email}</b> is confirmed. You can sign in now.
            </p>
            <Button asChild variant="gradient" className="mt-2 w-full">
              <Link to="/login">Sign in</Link>
            </Button>
          </div>
        )}

        {state.kind === "error" && (
          <div className="flex flex-col items-center text-center gap-4">
            <MailWarning className="size-12 text-amber-600" />
            <h1 className="font-display text-xl font-semibold">
              Couldn't verify
            </h1>
            <p className="text-sm text-muted-foreground">{state.message}</p>

            <form onSubmit={handleResend} className="w-full mt-2 space-y-2 text-left">
              <Label htmlFor="resend-email">Send a fresh link</Label>
              <Input
                id="resend-email"
                type="email"
                placeholder="you@example.com"
                value={resendEmail}
                onChange={(e) => setResendEmail(e.target.value)}
                required
              />
              <Button
                type="submit"
                variant="gradient"
                className="w-full"
                disabled={resending}
              >
                {resending && <Loader2 className="size-4 animate-spin mr-2" />}
                Resend verification email
              </Button>
            </form>

            <Link to="/login" className="text-xs text-muted-foreground hover:underline">
              Back to sign-in
            </Link>
          </div>
        )}
      </Card>
    </div>
  );
}
