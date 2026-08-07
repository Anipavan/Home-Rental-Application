import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertCircle } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { authApi } from "@/lib/api/auth";
import { Button } from "@/components/ui/button";
import { toast } from "@/hooks/use-toast";

/**
 * Two policies enforced from one component:
 *  1. **Idle timeout**: 30 min of no user activity → logout.
 *  2. **Access-token expiry**: when the token is within {@link WARNING_LEAD_MS}
 *     of expiring, show a banner. When it expires, force a logout.
 *
 * Mounted once inside `AppShell` (so only authenticated users get it).
 *
 * Activity is detected via mousemove / mousedown / keydown / touchstart /
 * scroll on the window. Updates throttle to once every 30 s — we don't need
 * millisecond accuracy for a 30-min timer.
 */
const IDLE_TIMEOUT_MS = 30 * 60 * 1000;     // 30 minutes
const WARNING_LEAD_MS = 60 * 1000;          // show banner 60 s before expiry
const ACTIVITY_THROTTLE_MS = 30 * 1000;     // update store at most every 30 s
const TICK_INTERVAL_MS = 10 * 1000;         // re-check every 10 s

export function IdleTimer() {
  const navigate = useNavigate();
  const { lastActivityAt, accessTokenExpiresAt, isAuthenticated, touchActivity, clear } =
    useAuthStore();
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null);
  const lastTouchedRef = useRef<number>(Date.now());

  // ── Activity tracking ───────────────────────────────────────────────
  useEffect(() => {
    const onActivity = () => {
      const now = Date.now();
      if (now - lastTouchedRef.current > ACTIVITY_THROTTLE_MS) {
        lastTouchedRef.current = now;
        touchActivity();
      }
    };
    const events: (keyof WindowEventMap)[] = [
      "mousemove",
      "mousedown",
      "keydown",
      "touchstart",
      "scroll",
    ];
    events.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));
    return () => events.forEach((e) => window.removeEventListener(e, onActivity));
  }, [touchActivity]);

  // ── Idle + expiry tick ──────────────────────────────────────────────
  useEffect(() => {
    const tick = () => {
      const now = Date.now();
      const idle = lastActivityAt ? now - lastActivityAt : 0;
      const expiresIn = accessTokenExpiresAt ? accessTokenExpiresAt - now : null;

      // Hard idle logout
      if (lastActivityAt && idle >= IDLE_TIMEOUT_MS) {
        forceLogout("idle");
        return;
      }
      // Hard expiry logout — we can no longer check refreshToken client-
      // side (it's in the HttpOnly cookie). Instead: when the access
      // token is expired, silently attempt a refresh; if that fails,
      // the response interceptor will already have redirected to /login.
      // The banner logic below still runs so the user gets warned.
      if (expiresIn !== null && expiresIn <= 0 && !isAuthenticated) {
        forceLogout("expired");
        return;
      }
      // Banner countdown
      if (expiresIn !== null && expiresIn <= WARNING_LEAD_MS && expiresIn > 0) {
        setSecondsLeft(Math.ceil(expiresIn / 1000));
      } else {
        setSecondsLeft(null);
      }
    };

    const id = window.setInterval(tick, TICK_INTERVAL_MS);
    tick(); // run once immediately
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastActivityAt, accessTokenExpiresAt, isAuthenticated]);

  function forceLogout(reason: "idle" | "expired") {
    // Best-effort revoke — the hra_refresh cookie is sent automatically
    // (withCredentials:true). Don't await before clearing local state.
    authApi.logout().catch(() => {});
    clear();
    toast({
      variant: "destructive",
      title:
        reason === "idle"
          ? "Signed out for inactivity"
          : "Your session expired",
      description: "Please sign in again to continue.",
    });
    navigate("/login");
  }

  // Click handler for the "Stay signed in" button. Explicitly mints a
  // new access token via /auth/refresh — the browser attaches the
  // hra_refresh cookie, backend rotates and returns a fresh access
  // token. The tick then sees plenty of headroom and hides the banner.
  const [extending, setExtending] = useState(false);

  async function extendSession() {
    setExtending(true);
    try {
      const resp = await authApi.refresh();
      useAuthStore.getState().setTokens(
        resp.accessToken,
        resp.accessTokenExpiresInSeconds,
      );
      useAuthStore.getState().touchActivity();
      setSecondsLeft(null);
    } catch {
      // Refresh failed (server expired the refresh token, or auth
      // service is down). Best to logout cleanly so the user gets
      // pushed to /login instead of staring at a stuck banner.
      forceLogout("expired");
    } finally {
      setExtending(false);
    }
  }

  if (secondsLeft === null) return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 max-w-sm rounded-xl border border-amber-500/40 bg-amber-50 dark:bg-amber-500/10 shadow-lg p-4 flex items-start gap-3">
      <AlertCircle className="size-5 text-amber-600 mt-0.5 shrink-0" />
      <div className="flex-1">
        <p className="font-medium text-sm">Session expiring soon</p>
        <p className="text-xs text-muted-foreground mt-0.5">
          You'll be signed out in <strong>{secondsLeft}s</strong>.
        </p>
        <Button
          size="sm"
          variant="outline"
          className="mt-2"
          onClick={extendSession}
          disabled={extending}
        >
          {extending ? "Extending…" : "Stay signed in"}
        </Button>
      </div>
    </div>
  );
}
