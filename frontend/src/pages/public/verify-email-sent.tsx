import { useLocation, Link } from "react-router-dom";
import { Mail, Loader2 } from "lucide-react";
import { useState } from "react";
import { emailVerificationApi } from "@/lib/api/email-verification";
import { extractErrorMessage } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { toast } from "@/hooks/use-toast";

/**
 * Landing page rendered immediately after signup when the
 * email-verification toggle is ON. The email is passed via
 * react-router location state: `navigate("/verify-email-sent",
 * { state: { email } })`. Falls back to a generic copy when state
 * isn't present (direct navigation, page refresh, etc.).
 */
export function VerifyEmailSentPage() {
  const loc = useLocation();
  const email = (loc.state as { email?: string } | null)?.email ?? null;
  const [resending, setResending] = useState(false);

  async function handleResend() {
    if (!email) return;
    setResending(true);
    try {
      await emailVerificationApi.resend(email);
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
      <Card className="w-full max-w-md p-8 text-center space-y-4">
        <div className="mx-auto rounded-full bg-primary/10 p-4 w-fit">
          <Mail className="size-10 text-primary" />
        </div>
        <h1 className="font-display text-xl font-semibold">
          Check your inbox
        </h1>
        <p className="text-sm text-muted-foreground">
          We sent a verification link
          {email ? (
            <>
              {" "}to <b>{email}</b>
            </>
          ) : null}
          . Click it from your email to activate your account, then sign in.
        </p>
        <p className="text-xs text-muted-foreground">
          The link expires in 24 hours. Didn't get it? Check spam, or click below
          to send a fresh one.
        </p>
        {email ? (
          <Button
            variant="outline"
            onClick={handleResend}
            disabled={resending}
            className="w-full"
          >
            {resending && <Loader2 className="size-4 animate-spin mr-2" />}
            Resend verification email
          </Button>
        ) : null}
        <Link
          to="/login"
          className="block text-xs text-muted-foreground hover:underline"
        >
          Back to sign-in
        </Link>
      </Card>
    </div>
  );
}
