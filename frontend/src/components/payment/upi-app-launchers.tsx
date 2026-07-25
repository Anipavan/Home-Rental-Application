import { Smartphone } from "lucide-react";

/**
 * Colourful, app-branded launcher grid for a UPI intent URL. Deep-
 * links straight into Google Pay / PhonePe / Paytm / BHIM by
 * swapping the {@code upi://} scheme for each app's private scheme;
 * the "Any UPI app" tile keeps the standard {@code upi://} scheme
 * which triggers the Android OS-wide UPI app chooser.
 *
 * <p>Only Android reliably honours these schemes — iOS and desktop
 * don't route them. The caller is expected to render the receiver's
 * UPI ID + bank details BELOW the launcher grid so users on those
 * platforms can copy-paste into their app manually. This component
 * intentionally does NOT show a QR — the QR belongs in an optional
 * "Show QR" disclosure below the launchers.
 *
 * <p>The {@code amount} field in the upi:// URL is enforced by
 * NPCI's UPI spec, so tapping a launcher opens the app with the
 * receiver, amount, and note pre-filled — the user only confirms.
 */
export interface UpiAppLaunchersProps {
  /**
   * Fully-formed {@code upi://pay?...} intent URL (payee VPA, name,
   * amount, currency, note). Each per-app link is derived by
   * swapping the scheme.
   */
  upiUri: string;
}

/**
 * (Display name, target scheme with trailing '/' if needed, gradient
 * classes, text-color classes). Ordered by usage in India — GPay
 * and PhonePe combined cover ~85% of UPI transactions.
 */
const APPS: Array<{
  name: string;
  scheme: string;
  gradient: string;
  textCls: string;
}> = [
  {
    name: "Google Pay",
    scheme: "tez://upi/",
    gradient: "from-white to-slate-50 border border-slate-200",
    textCls: "text-slate-900",
  },
  {
    name: "PhonePe",
    scheme: "phonepe://",
    gradient: "from-indigo-600 to-purple-700",
    textCls: "text-white",
  },
  {
    name: "Paytm",
    scheme: "paytmmp://",
    gradient: "from-sky-500 to-blue-600",
    textCls: "text-white",
  },
  {
    name: "BHIM",
    scheme: "bhim://",
    gradient: "from-orange-500 to-orange-600",
    textCls: "text-white",
  },
];

/**
 * Convert a {@code upi://pay?...} URL to the same URL under a
 * different app's scheme. Preserves the entire query string so
 * receiver / amount / note stay intact.
 */
function swapScheme(upiUri: string, targetScheme: string): string {
  const idx = upiUri.indexOf("upi://");
  if (idx !== 0) return upiUri;
  return `${targetScheme}${upiUri.slice("upi://".length)}`;
}

export function UpiAppLaunchers({ upiUri }: UpiAppLaunchersProps) {
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {APPS.map((app) => {
          const href = swapScheme(upiUri, app.scheme);
          return (
            <a
              key={app.name}
              href={href}
              className={
                `group relative overflow-hidden rounded-xl px-3 py-3 text-center ` +
                `bg-gradient-to-br ${app.gradient} ${app.textCls} ` +
                `shadow-sm hover:shadow-lift transition-all hover:-translate-y-0.5 ` +
                `text-sm font-semibold`
              }
            >
              <span className="block leading-tight">{app.name}</span>
              <span className="block text-[10px] font-normal opacity-80 mt-0.5">
                Tap to open
              </span>
            </a>
          );
        })}
      </div>
      {/* Fallback catches any UPI app installed on the device via
          the OS-wide UPI chooser. Kept full-width below the branded
          tiles so it never crowds them. */}
      <a
        href={upiUri}
        className={
          "group flex items-center justify-center gap-2 rounded-xl " +
          "border border-primary/30 bg-primary/5 text-primary " +
          "px-3 py-2.5 text-sm font-medium " +
          "hover:bg-primary hover:text-primary-foreground " +
          "transition-colors"
        }
      >
        <Smartphone className="size-4" />
        Any other UPI app
      </a>
      <p className="text-[11px] text-muted-foreground text-center">
        Not on Android? Copy the UPI ID below and paste it in your
        UPI app manually.
      </p>
    </div>
  );
}
