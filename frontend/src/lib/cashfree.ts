/**
 * On-demand loader for Cashfree's Checkout SDK.
 *
 * <p>The SDK is loaded from Cashfree's CDN only when the tenant hits
 * the Pay page AND the owner is payout-ready — no cost to bundle size
 * for direct-UPI payments. The script is memoised so a second Pay
 * page visit in the same session reuses the already-attached global.
 *
 * <p>The environment flag ({@code sandbox} | {@code production}) is
 * baked into the SDK's runtime via {@code Cashfree.load}. Sandbox
 * points at their test payment endpoints so no real money moves.
 *
 * <p>CSP: the source URL is on the allowlist in vite.config.ts's
 * {@code script-src}, and the frame Cashfree opens is on
 * {@code frame-src}. Loading from anywhere else will silently fail.
 */

const SDK_URL = "https://sdk.cashfree.com/js/v3/cashfree.js";

/**
 * Minimal typing for the Cashfree SDK surface we actually call. The
 * upstream SDK exposes far more; we only need {@code checkout} for
 * the hosted-page redirect flow.
 */
interface CashfreeInstance {
  checkout: (opts: {
    paymentSessionId: string;
    /** "_self" redirects the current tab; "_modal" opens their modal. */
    redirectTarget?: "_self" | "_blank" | "_modal";
    returnUrl?: string;
  }) => Promise<{ error?: { message?: string }; redirect?: boolean; paymentDetails?: unknown }>;
}

/**
 * {@code window.Cashfree} may expose EITHER shape depending on the SDK
 * variant that ends up loaded:
 * <ul>
 *   <li>CDN {@code /js/v3/cashfree.js} → {@code window.Cashfree} is a
 *       factory function: {@code Cashfree({ mode }) → instance}.</li>
 *   <li>NPM {@code @cashfreepayments/cashfree-js} bundled the same way
 *       → the object exposes {@code Cashfree.load({ mode })}.</li>
 * </ul>
 * We type it as the union and probe at runtime so a Cashfree-side
 * change to which shape the CDN ships doesn't break checkout.
 */
type CashfreeSdk =
  | ((opts: { mode: "sandbox" | "production" }) => CashfreeInstance | Promise<CashfreeInstance>)
  | { load: (opts: { mode: "sandbox" | "production" }) => Promise<CashfreeInstance> };

declare global {
  interface Window {
    Cashfree?: CashfreeSdk;
  }
}

let loadingPromise: Promise<CashfreeSdk> | null = null;

/**
 * Load Cashfree's Checkout SDK. Idempotent — subsequent calls return
 * the cached script's global. Rejects if the script fails to load
 * (network / CSP block) so callers can gracefully fall back to
 * direct-UPI instead of hanging on a never-resolving promise.
 */
export function loadCashfreeSdk(): Promise<CashfreeSdk> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("Cashfree SDK requires a browser"));
  }
  if (window.Cashfree) return Promise.resolve(window.Cashfree);
  if (loadingPromise) return loadingPromise;

  loadingPromise = new Promise<CashfreeSdk>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${SDK_URL}"]`,
    );
    if (existing && window.Cashfree) {
      resolve(window.Cashfree);
      return;
    }
    const script = document.createElement("script");
    script.src = SDK_URL;
    script.async = true;
    script.onload = () => {
      if (!window.Cashfree) {
        reject(new Error("Cashfree SDK loaded but window.Cashfree is undefined"));
        return;
      }
      resolve(window.Cashfree);
    };
    script.onerror = () => {
      loadingPromise = null;  // let a retry try again
      reject(new Error("Failed to load Cashfree SDK (network / CSP blocked?)"));
    };
    document.head.appendChild(script);
  });
  return loadingPromise;
}

/**
 * Open Cashfree Checkout with a session id — the whole payment UI
 * (UPI apps, cards, netbanking, wallets) lives inside Cashfree's
 * hosted page. Uses {@code _self} redirect so the tenant leaves our
 * SPA, completes payment on Cashfree, and comes back to
 * {@code /app/payments/:id/return}.
 *
 * <p>The environment argument picks between sandbox (test money,
 * "TEST" banner across the top of the checkout page) and production.
 * Defaults to sandbox so a misconfigured build stays visibly harmless.
 */
export async function openCashfreeCheckout(
  paymentSessionId: string,
  environment: "sandbox" | "production" = "sandbox",
): Promise<void> {
  const sdk = await loadCashfreeSdk();
  // Probe whether window.Cashfree is the factory function (CDN shape)
  // or a namespace with .load() (NPM-alike shape). Either way, await
  // normalises the sync-vs-async return.
  const cf: CashfreeInstance = await (typeof sdk === "function"
    ? sdk({ mode: environment })
    : sdk.load({ mode: environment }));

  // Mobile-safety timeout: on some mobile Chrome sessions the SDK's
  // checkout() call hangs silently — the tenant sees a blank page
  // with a chrome-mobile "failed to load" sad-icon and no way to
  // recover. Race the SDK's redirect against a 3-second timeout; if
  // the SDK hasn't navigated the tab away by then, submit a hidden
  // form-POST to Cashfree's hosted-checkout URL. This mirrors what
  // the SDK does internally — Cashfree's endpoint requires POST, not
  // GET (previous window.location.href fallback 404'd with
  // "endpoint or method is not valid") — but bypasses the SDK's
  // mobile-specific choreography (hidden iframe / overlay) that
  // seems to be what's failing on some devices.
  const timeoutId = window.setTimeout(() => {
    // eslint-disable-next-line no-console
    console.warn(
      "[cashfree] SDK checkout didn't navigate within 3s — falling back to form-POST",
    );
    submitCheckoutForm(paymentSessionId, environment);
  }, 3000);

  try {
    const result = await cf.checkout({
      paymentSessionId,
      redirectTarget: "_self",
    });
    // The redirect flow doesn't resolve with an error under normal
    // circumstances (the browser navigates away). If we DO get an
    // error back it means the SDK couldn't even start the redirect.
    // Take over with our own form-POST — same destination, no SDK.
    if (result?.error) {
      window.clearTimeout(timeoutId);
      // eslint-disable-next-line no-console
      console.warn(
        "[cashfree] SDK checkout returned error, falling back to form-POST:",
        result.error.message,
      );
      submitCheckoutForm(paymentSessionId, environment);
      return;
    }
    // Normal happy path: the browser is already navigating. Timer
    // will fire the form-POST fallback if we're still here in 3s
    // (only happens on the mobile-hang failure mode).
  } catch (err) {
    window.clearTimeout(timeoutId);
    // Same story — the SDK threw before starting the redirect. Try
    // our own form-POST before surfacing the error.
    // eslint-disable-next-line no-console
    console.warn("[cashfree] SDK checkout threw, falling back to form-POST:", err);
    submitCheckoutForm(paymentSessionId, environment);
  }
}

/**
 * Submit a hidden form-POST to Cashfree's hosted-checkout endpoint.
 * Mirrors what the SDK does internally — the endpoint requires POST
 * with {@code paymentSessionId} as a form field — but bypasses the
 * SDK's mobile-Chrome-hanging redirect flow.
 *
 * <p>Path shape confirmed from the CSP form-action logs (commit
 * 9468b7a) and Cashfree's own error response on GET: the correct URL
 * is {@code /pg/view/sessions/checkout} on sandbox.cashfree.com or
 * api.cashfree.com depending on environment.
 */
function submitCheckoutForm(
  paymentSessionId: string,
  environment: "sandbox" | "production",
): void {
  const host =
    environment === "production"
      ? "https://api.cashfree.com"
      : "https://sandbox.cashfree.com";
  const form = document.createElement("form");
  form.action = `${host}/pg/view/sessions/checkout`;
  form.method = "POST";
  form.style.display = "none";

  const input = document.createElement("input");
  input.type = "hidden";
  input.name = "paymentSessionId";
  input.value = paymentSessionId;
  form.appendChild(input);

  document.body.appendChild(form);
  form.submit();
}
