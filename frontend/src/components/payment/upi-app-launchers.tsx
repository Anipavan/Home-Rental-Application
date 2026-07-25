import { Smartphone } from "lucide-react";

/**
 * Tap-to-open UPI-app launcher grid.
 *
 * <p><b>Why intent:// instead of app-specific schemes.</b>
 * The vendor schemes ({@code phonepe://}, {@code tez://},
 * {@code paytmmp://}) trigger each app's "external browser link"
 * flow. PhonePe caps unverified merchants on that flow at ₹2,000,
 * so a rent payment above ₹2K opens PhonePe in a mode with no
 * Pay button — just DISMISS. Users hit a dead end.
 *
 * <p>The Android {@code intent://} URL with {@code scheme=upi} +
 * {@code package=<app>} tells the OS "open this specific app
 * with a native UPI intent, not an external browser link". The
 * target app treats it as a first-class UPI transaction — no cap.
 * When the named package isn't installed the browser falls back
 * to {@code S.browser_fallback_url}, a plain {@code upi://…} that
 * triggers Android's system-wide UPI chooser.
 *
 * <p>iOS + desktop don't honour {@code intent://}. Those users
 * copy the receiver details rendered below the launcher grid.
 */
export interface UpiAppLaunchersProps {
  /**
   * Fully-formed {@code upi://pay?...} intent URL with payee VPA,
   * name, amount, currency, and note. Every per-app link is
   * derived by re-wrapping the query string into an intent:// URL.
   */
  upiUri: string;
  /**
   * Fires when the user taps a launcher. The parent uses this to
   * arm the "did you complete the payment?" prompt that fires on
   * tab-return (see DirectUpiPayCard's visibility-change flow).
   */
  onLaunch?: () => void;
}

/**
 * Popular Indian UPI apps by combined market share. Each entry
 * carries the Android package id (drives the intent:// package=
 * hint), a display name, and Tailwind classes for the tile.
 * Colors are the brand-associated palette but rendered as plain
 * gradients with typography — no logo reproduction.
 */
const APPS: Array<{
  name: string;
  androidPackage: string;
  bgClass: string;
  textClass: string;
  accentLetter: string;
}> = [
  {
    name: "Google Pay",
    androidPackage: "com.google.android.apps.nbu.paisa.user",
    bgClass:
      "bg-white border border-slate-200 hover:border-slate-300",
    textClass: "text-slate-900",
    accentLetter: "G",
  },
  {
    name: "PhonePe",
    androidPackage: "com.phonepe.app",
    bgClass:
      "bg-gradient-to-br from-[#5F259F] to-[#3d1670] border border-[#5F259F]/40",
    textClass: "text-white",
    accentLetter: "पे",
  },
  {
    name: "Paytm",
    androidPackage: "net.one97.paytm",
    bgClass:
      "bg-gradient-to-br from-[#00BAF2] to-[#0088c9] border border-[#00BAF2]/40",
    textClass: "text-white",
    accentLetter: "₹",
  },
];

/**
 * Build the Android intent:// URL for a target app. Passes the
 * UPI query string with scheme=upi so the target app treats the
 * open as a native UPI intent (no browser-external caps). Falls
 * back to the plain upi:// URL when the package isn't installed.
 */
function intentUrlFor(upiUri: string, androidPackage: string): string {
  const qIdx = upiUri.indexOf("?");
  const query = qIdx >= 0 ? upiUri.slice(qIdx + 1) : "";
  const fallback = encodeURIComponent(upiUri);
  return (
    `intent://pay?${query}` +
    `#Intent;scheme=upi;package=${androidPackage};` +
    `S.browser_fallback_url=${fallback};end`
  );
}

export function UpiAppLaunchers({
  upiUri,
  onLaunch,
}: UpiAppLaunchersProps) {
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        {APPS.map((app) => {
          const href = intentUrlFor(upiUri, app.androidPackage);
          return (
            <a
              key={app.name}
              href={href}
              onClick={onLaunch}
              className={
                `group flex items-center gap-2.5 rounded-xl px-3 py-2.5 ` +
                `${app.bgClass} ${app.textClass} ` +
                `shadow-sm hover:shadow-lift transition-all hover:-translate-y-0.5`
              }
            >
              <span
                className={
                  `size-9 shrink-0 grid place-items-center rounded-lg ` +
                  `bg-white/15 font-display font-bold text-base ` +
                  `${app.textClass}`
                }
                aria-hidden="true"
              >
                {app.accentLetter}
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold leading-tight truncate">
                  {app.name}
                </span>
                <span className="block text-[10px] opacity-80 leading-tight mt-0.5">
                  Tap to pay
                </span>
              </span>
            </a>
          );
        })}
      </div>
      <p className="text-[11px] text-muted-foreground text-center flex items-center justify-center gap-1.5">
        <Smartphone className="size-3" />
        On iPhone or desktop? Copy the UPI ID below and paste it in
        your UPI app manually.
      </p>
    </div>
  );
}
