// Payment-method tiles using the official brand SVGs from the
// Simple Icons project (https://simpleicons.org, CC0). Each tile is a
// brand-colored rounded square with the logo centered on top, matching
// the visuals used in the actual app pickers (PhonePe / GPay / Paytm).
//
// Importing from `react-icons/si` keeps the bundle small — Vite tree-
// shakes everything except the icons we use.

import {
  SiPhonepe,
  SiGooglepay,
  SiPaytm,
} from "react-icons/si";
import { Landmark, CreditCard, Banknote } from "lucide-react";

/* ------------------------------------------------------------------ */
/* PhonePe                                                            */
/* ------------------------------------------------------------------ */

export function PhonePeIcon({ className }: { className?: string }) {
  // PhonePe brand purple is #5F259F. The SI logo is a single-path white
  // mark; centring it on the brand tile gives the same look as the
  // PhonePe app icon.
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl ${className ?? ""}`}
      style={{ background: "#5F259F" }}
      aria-label="PhonePe"
    >
      <SiPhonepe color="#ffffff" style={{ width: "60%", height: "60%" }} />
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Google Pay                                                         */
/* ------------------------------------------------------------------ */

export function GPayIcon({ className }: { className?: string }) {
  // GPay's brand identity uses a white background with the multi-coloured
  // "G". Simple Icons ships the GPay mark as a monochrome path though, so
  // we render the whole tile in the GPay blue (#4285F4) on a white surface
  // — which matches the latest "G Pay" pill design.
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl bg-white border ${className ?? ""}`}
      style={{ borderColor: "#dadce0" }}
      aria-label="Google Pay"
    >
      <SiGooglepay color="#4285F4" style={{ width: "70%", height: "70%" }} />
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Paytm                                                              */
/* ------------------------------------------------------------------ */

export function PaytmIcon({ className }: { className?: string }) {
  // Paytm cyan #00BAF2; the SI mark is the lowercase "p" + wordmark.
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl ${className ?? ""}`}
      style={{ background: "#00BAF2" }}
      aria-label="Paytm"
    >
      <SiPaytm color="#ffffff" style={{ width: "70%", height: "70%" }} />
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Generic UPI (collect-flow / "Other UPI")                           */
/* ------------------------------------------------------------------ */

export function UPIIcon({ className }: { className?: string }) {
  // NPCI's UPI mark — orange-and-green chevrons. Hand-pathed because
  // the official mark isn't in Simple Icons.
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl bg-white border ${className ?? ""}`}
      style={{ borderColor: "#dadce0" }}
      aria-label="UPI"
    >
      <svg viewBox="0 0 64 64" style={{ width: "65%", height: "65%" }}>
        <path d="M28 14l16 18-16 18 6-18-6-18Z" fill="#FF7E1B" />
        <path d="M21 14l16 18-16 18 6-18-6-18Z" fill="#098041" opacity=".9" />
      </svg>
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Credit / Debit Card                                                */
/* ------------------------------------------------------------------ */

export function CardIcon({ className }: { className?: string }) {
  // Generic card tile — gradient navy, with the lucide CreditCard glyph.
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl text-white ${className ?? ""}`}
      style={{
        background: "linear-gradient(135deg,#0f172a 0%,#1e293b 100%)",
      }}
      aria-label="Credit / Debit Card"
    >
      <CreditCard style={{ width: "55%", height: "55%" }} strokeWidth={1.75} />
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Net Banking                                                        */
/* ------------------------------------------------------------------ */

export function NetBankingIcon({ className }: { className?: string }) {
  // No single brand here — it's a fan-out to every Indian bank. Use
  // lucide's Landmark (classical-column building) on a deep navy tile so
  // it reads as "bank" at a glance, matching the picker's visual rhythm.
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl text-white ${className ?? ""}`}
      style={{ background: "#1e3a8a" }}
      aria-label="Net Banking"
    >
      <Landmark style={{ width: "55%", height: "55%" }} strokeWidth={1.75} />
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Cash (owner-marked offline payment)                                */
/* ------------------------------------------------------------------ */

export function CashIcon({ className }: { className?: string }) {
  return (
    <span
      className={`relative inline-flex items-center justify-center rounded-2xl text-white ${className ?? ""}`}
      style={{ background: "#10b981" }}
      aria-label="Cash"
    >
      <Banknote style={{ width: "55%", height: "55%" }} strokeWidth={1.75} />
    </span>
  );
}
