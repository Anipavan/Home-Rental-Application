import { useEffect, useMemo, useRef, useState } from "react";
import { AlertCircle, CheckCircle2, Loader2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { paymentGateway } from "@/lib/api/payment-gateway";
import { isRazorpayPaymentsDisabled } from "@/lib/feature-flags";
import type { VpaValidationResponse } from "@/types/api";
import { cn } from "@/lib/utils";

/**
 * Standard UPI VPA format. Local part: 2-256 chars of letters, digits,
 * dot, hyphen, underscore. PSP part: letter-led, 2-64 chars of letters,
 * digits, dot, hyphen. Same regex the backend uses to fail-fast malformed
 * inputs before calling Razorpay.
 */
const VPA_FORMAT_RE = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9.\-]{1,63}$/;

/** ms to wait after the last keystroke before firing the validate call. */
const DEBOUNCE_MS = 600;

/**
 * Process-local cache so the same VPA isn't re-validated as the user
 * tabs across forms. 5-minute TTL covers a typical "open profile, edit,
 * save" session without going stale for too long.
 */
const cache = new Map<string, { result: VpaValidationResponse; at: number }>();
const CACHE_TTL_MS = 5 * 60 * 1000;

export type VpaState =
  | { kind: "empty" }
  | { kind: "format-bad"; reason: string }
  | { kind: "checking" }
  | { kind: "valid"; customerName: string }
  | { kind: "invalid"; reason: string };

/**
 * UPI VPA input with live server-side validation + name preview.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Every keystroke updates the local value and runs a regex format
 *       check. Pre-formatted strings keep the previous state until the
 *       user starts typing a fresh value.</li>
 *   <li>Once the format passes, a {@link #DEBOUNCE_MS}ms timer kicks
 *       off a call to {@code GET /payments/vpa/validate}. Further
 *       keystrokes reset the timer.</li>
 *   <li>The result is cached for {@link #CACHE_TTL_MS}ms so the user
 *       can leave + come back to the same VPA without burning another
 *       gateway call.</li>
 *   <li>Parent is notified via {@code onStateChange} so the surrounding
 *       form can gate Save / Pay buttons on a {@code "valid"} state.</li>
 * </ol>
 *
 * <p>Visual states:
 * <ul>
 *   <li><b>idle / empty</b> — neutral input, helper text.</li>
 *   <li><b>format-bad</b> — red ring, "format" message under the input.</li>
 *   <li><b>checking</b> — spinner badge on the right edge.</li>
 *   <li><b>valid</b> — green ring + a small "Verified · NAME" badge.</li>
 *   <li><b>invalid</b> — red ring + failure reason from the backend.</li>
 * </ul>
 */
export function UpiIdField({
  value,
  onChange,
  onStateChange,
  label = "UPI ID",
  helper,
  optional = false,
  id,
  disabled,
}: {
  value: string;
  onChange: (v: string) => void;
  /** Called whenever validation state changes. Parent uses this to gate Save. */
  onStateChange?: (state: VpaState) => void;
  /** Label rendered above the input. Defaults to "UPI ID". */
  label?: string;
  /** Helper text under the input shown in the neutral/idle state. */
  helper?: string;
  /** When true, an empty value is reported as {@code empty} (treat as ok). */
  optional?: boolean;
  /** Optional id — falls back to a stable local one. */
  id?: string;
  disabled?: boolean;
}) {
  const inputId = id ?? "upi-id-field";
  const [state, setState] = useState<VpaState>(
    optional && !value ? { kind: "empty" } : initialFor(value),
  );
  const timerRef = useRef<number | null>(null);
  const lastFiredVpaRef = useRef<string | null>(null);

  // Surface state up to the parent on every change. Effect is fine here —
  // the dependency is the *narrow* state shape, not the parent callback.
  useEffect(() => {
    onStateChange?.(state);
  }, [state, onStateChange]);

  // Compute the format-validation result for the current value.
  const formatOk = useMemo(() => VPA_FORMAT_RE.test(value.trim()), [value]);

  useEffect(() => {
    // Cleanup the existing timer on every value change.
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }

    const trimmed = value.trim();

    if (!trimmed) {
      // Empty input. For optional fields that's fine; otherwise show a
      // neutral idle state — the parent decides whether to disable Save.
      setState(optional ? { kind: "empty" } : { kind: "empty" });
      return;
    }

    if (!formatOk) {
      setState({
        kind: "format-bad",
        reason:
          "Use the form name@bank (e.g. anirudh@oksbi, 9876543210@paytm).",
      });
      return;
    }

    // When Razorpay-mediated payments are disabled (RAZORPAY_PAYMENTS_
    // DISABLED), we can't rely on Razorpay's verifyVpa: test-mode rejects
    // real VPAs, and in live mode we're no longer routing payments
    // through Razorpay anyway. Format is enough — tenants scan a QR
    // that carries this VPA verbatim, and their own UPI app does the
    // real resolution at pay time. Pretend the check succeeded so
    // Save unblocks; skip showing a name preview since we don't have
    // one from the server.
    if (isRazorpayPaymentsDisabled()) {
      setState({ kind: "valid", customerName: "" });
      return;
    }

    // Cache hit? Resolve synchronously — don't fire another server call.
    const cached = cache.get(trimmed);
    if (cached && Date.now() - cached.at < CACHE_TTL_MS) {
      setState(toState(cached.result));
      return;
    }

    // Pending state immediately so the user sees feedback while debounce ticks.
    setState({ kind: "checking" });

    timerRef.current = window.setTimeout(() => {
      // Re-check the cache inside the timer — another field instance may
      // have populated it during the debounce window.
      const fresh = cache.get(trimmed);
      if (fresh && Date.now() - fresh.at < CACHE_TTL_MS) {
        setState(toState(fresh.result));
        return;
      }

      // Avoid duplicate in-flight requests for the same VPA across React's
      // StrictMode double-render — only fire when we haven't fired this
      // exact value yet, or when the previous fire was a different value.
      if (lastFiredVpaRef.current === trimmed) {
        return;
      }
      lastFiredVpaRef.current = trimmed;

      paymentGateway
        .validateVpa(trimmed)
        .then((res) => {
          cache.set(trimmed, { result: res, at: Date.now() });
          setState(toState(res));
        })
        .catch(() => {
          // Network / 5xx — degrade gracefully. Format already passed,
          // so we surface a "couldn't verify" message but don't block.
          setState({
            kind: "invalid",
            reason:
              "We couldn't verify this UPI ID right now. You can still save — your owner / tenant will see the same name preview later.",
          });
        });
    }, DEBOUNCE_MS);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [value, formatOk, optional]);

  return (
    <div className="space-y-1.5">
      <Label htmlFor={inputId}>
        {label}
        {optional && (
          <span className="text-muted-foreground font-normal ml-1">
            (optional)
          </span>
        )}
      </Label>
      <div className="relative">
        <Input
          id={inputId}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="username@bank"
          inputMode="email"
          autoComplete="off"
          spellCheck={false}
          maxLength={120}
          disabled={disabled}
          className={cn(
            "font-mono pr-10 transition-colors",
            state.kind === "valid" &&
              "border-emerald-500 focus-visible:ring-emerald-500/40",
            (state.kind === "invalid" || state.kind === "format-bad") &&
              "border-destructive focus-visible:ring-destructive/40",
          )}
        />
        <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none">
          {state.kind === "checking" && (
            <Loader2 className="size-4 animate-spin text-muted-foreground" />
          )}
          {state.kind === "valid" && (
            <CheckCircle2 className="size-4 text-emerald-500" />
          )}
          {(state.kind === "invalid" || state.kind === "format-bad") && (
            <AlertCircle className="size-4 text-destructive" />
          )}
        </div>
      </div>

      {/* State-specific status line. The idle / empty path falls back to
          the {@code helper} prop so call sites can customise the hint. */}
      {state.kind === "valid" && (
        <div className="flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/5 px-2.5 py-1.5">
          <CheckCircle2 className="size-3.5 text-emerald-600 shrink-0" />
          <p className="text-xs">
            {state.customerName ? (
              <>
                <span className="font-medium">Verified</span>{" "}
                <span className="text-muted-foreground">·</span>{" "}
                <span className="font-mono">{state.customerName}</span>
              </>
            ) : (
              <span className="font-medium">Format looks good</span>
            )}
          </p>
        </div>
      )}
      {(state.kind === "invalid" || state.kind === "format-bad") && (
        <p className="text-xs text-destructive flex items-start gap-1.5">
          <AlertCircle className="size-3.5 shrink-0 mt-0.5" />
          {state.reason}
        </p>
      )}
      {state.kind === "checking" && (
        <p className="text-xs text-muted-foreground">
          Checking with your bank…
        </p>
      )}
      {state.kind === "empty" && helper && (
        <p className="text-xs text-muted-foreground">{helper}</p>
      )}
    </div>
  );
}

/* ---------- helpers ---------- */

/** Initial state for a pre-filled value (e.g. on form open from saved data). */
function initialFor(v: string): VpaState {
  if (!v.trim()) return { kind: "empty" };
  if (!VPA_FORMAT_RE.test(v.trim())) {
    return {
      kind: "format-bad",
      reason: "Use the form name@bank (e.g. anirudh@oksbi).",
    };
  }
  return { kind: "checking" };
}

/** Map a backend response onto our local discriminated union. */
function toState(res: VpaValidationResponse): VpaState {
  if (res.valid && res.customerName) {
    return { kind: "valid", customerName: res.customerName };
  }
  return {
    kind: "invalid",
    reason: res.failureReason ?? "We couldn't verify this UPI ID.",
  };
}

/** Public helper for parents that want a single boolean "can save?" check. */
export function isVpaUsable(state: VpaState, optional: boolean): boolean {
  if (optional && state.kind === "empty") return true;
  return state.kind === "valid";
}
