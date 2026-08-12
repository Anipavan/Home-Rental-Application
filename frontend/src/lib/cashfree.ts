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
  const result = await cf.checkout({
    paymentSessionId,
    redirectTarget: "_self",
  });
  // The redirect flow doesn't resolve with an error under normal
  // circumstances (the browser navigates away). If we DO get an error
  // back it means the SDK couldn't even start the redirect — surface
  // it so the caller can toast + fall back.
  if (result?.error) {
    throw new Error(result.error.message ?? "Cashfree Checkout failed to open");
  }
}
