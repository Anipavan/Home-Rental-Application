import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle2, AlertTriangle, Loader2 } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { kycApi } from "@/lib/api/kyc";
import { extractErrorMessage } from "@/lib/api/client";
import { DIGILOCKER_STATE_STORAGE_KEY } from "./kyc";

/**
 * Landing page DigiLocker redirects the user back to after they grant
 * consent. URL shape: {@code /app/kyc/callback?code=…&state=…}.
 *
 * <p>Responsibilities, in order:
 * <ol>
 *   <li>Pull {@code code} and {@code state} from the query string.</li>
 *   <li>Cross-check {@code state} against the one we stashed in
 *       sessionStorage when we launched the flow. This is a defence-in-depth
 *       check — the server also validates state against the persisted KYC
 *       record before exchanging the code.</li>
 *   <li>POST {@code {code, state}} to /kyc/digilocker/callback. Our
 *       backend does the token exchange + eAadhaar fetch + VERIFY
 *       transactionally — the response is the new KYC record.</li>
 *   <li>Show a result panel (success / failure) and let the user
 *       navigate back to /app/kyc to see the verified state.</li>
 * </ol>
 *
 * <p>Uses a {@code useRef} guard so React 18 StrictMode's double-render
 * doesn't fire the exchange twice — DigiLocker invalidates the code on
 * first use, so the second call would always 400.
 */
export function KycCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<"working" | "success" | "error">("working");
  const [error, setError] = useState<string | null>(null);
  const hasFiredRef = useRef(false);

  useEffect(() => {
    // StrictMode + dev refresh both re-fire this effect; bail unless this
    // is the first run. The auth code is one-shot — a second call always
    // 400s, which would mislead the user into thinking the flow failed.
    if (hasFiredRef.current) return;
    hasFiredRef.current = true;

    const code = params.get("code");
    const state = params.get("state");
    const errorParam = params.get("error");
    const errorDescription = params.get("error_description");

    // DigiLocker's own error path — user denied consent, scopes mismatch, etc.
    if (errorParam) {
      setPhase("error");
      setError(
        errorDescription
          ? `${errorParam}: ${errorDescription}`
          : `DigiLocker returned: ${errorParam}`,
      );
      return;
    }
    if (!code || !state) {
      setPhase("error");
      setError("DigiLocker callback was missing required parameters.");
      return;
    }

    // Defence-in-depth CSRF check. The server is the source of truth (it
    // validates state against the DB-persisted token + TTL), but mismatch
    // here means something is wrong with the user's browser session — we
    // surface a clean message rather than rounding-tripping to find out.
    let sessionState: string | null = null;
    try {
      sessionState = sessionStorage.getItem(DIGILOCKER_STATE_STORAGE_KEY);
    } catch {
      // sessionStorage can throw in private-browsing modes — let the server
      // catch state mismatches in that case.
    }
    if (sessionState && sessionState !== state) {
      setPhase("error");
      setError("Session state mismatch — please start the verification again.");
      return;
    }

    kycApi
      .digilockerCallback({ code, state })
      .then(() => {
        // Clear the state token — it's single-use now.
        try {
          sessionStorage.removeItem(DIGILOCKER_STATE_STORAGE_KEY);
        } catch {
          /* noop */
        }
        setPhase("success");
      })
      .catch((e) => {
        setPhase("error");
        setError(extractErrorMessage(e));
      });
  }, [params]);

  return (
    <div className="animate-fade-in max-w-xl mx-auto">
      <Card>
        <CardContent className="p-8 text-center">
          {phase === "working" && (
            <>
              <div className="size-14 rounded-full bg-emerald-500/15 grid place-items-center mx-auto">
                <Loader2 className="size-6 text-emerald-600 animate-spin" />
              </div>
              <h2 className="font-display text-xl font-semibold mt-4">
                Finishing your DigiLocker verification…
              </h2>
              <p className="text-sm text-muted-foreground mt-2">
                We're securely fetching your Aadhaar e-document from DigiLocker.
                This usually takes a few seconds — please don't close this tab.
              </p>
            </>
          )}

          {phase === "success" && (
            <>
              <div className="size-14 rounded-full bg-emerald-500/15 grid place-items-center mx-auto">
                <CheckCircle2 className="size-6 text-emerald-600" />
              </div>
              <h2 className="font-display text-xl font-semibold mt-4">
                You're verified
              </h2>
              <p className="text-sm text-muted-foreground mt-2">
                Your identity has been verified via DigiLocker. Every part of
                Anirudh Homes — paying rent, signing leases, raising
                maintenance — is now unlocked for you.
              </p>
              <div className="mt-6 flex gap-2 justify-center">
                <Button
                  variant="gradient"
                  onClick={() => navigate("/app/kyc", { replace: true })}
                >
                  See my verification
                </Button>
                <Button
                  variant="outline"
                  onClick={() => navigate("/app", { replace: true })}
                >
                  Back to dashboard
                </Button>
              </div>
            </>
          )}

          {phase === "error" && (
            <>
              <div className="size-14 rounded-full bg-destructive/15 grid place-items-center mx-auto">
                <AlertTriangle className="size-6 text-destructive" />
              </div>
              <h2 className="font-display text-xl font-semibold mt-4">
                Verification didn't complete
              </h2>
              <p className="text-sm text-muted-foreground mt-2">
                {error ??
                  "Something went wrong while finishing your DigiLocker verification."}{" "}
                Your Aadhaar wasn't stored — you can try again any time.
              </p>
              <div className="mt-6 flex gap-2 justify-center">
                <Button
                  variant="gradient"
                  onClick={() => navigate("/app/kyc", { replace: true })}
                >
                  Try again
                </Button>
                <Button
                  variant="outline"
                  onClick={() => navigate("/app", { replace: true })}
                >
                  Back to dashboard
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
