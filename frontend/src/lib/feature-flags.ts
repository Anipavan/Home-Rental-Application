/**
 * Centralised feature-flag table.
 *
 * <p>Today this is a hard-coded module — flags are flipped via PR and
 * deploy. Reading the same constant from every call site means
 * re-enabling a paused feature is a one-place change.
 *
 * <p>If we later move to runtime flags (LaunchDarkly, env-backed, etc.)
 * the signatures here become hooks/getters; call sites don't change.
 */
export const FEATURE_FLAGS = {
  /**
   * KYC is LIVE — backed by Sandbox.co.in PAN verification.
   *
   * <p>The active flow is PAN-only: tenant types their PAN + name,
   * server calls Sandbox.co.in's NSDL-backed PAN verify, returns the
   * matched holder name, flips kyc_status to VERIFIED if the PAN is
   * valid and the name matches. First 100 verifications are free on
   * the Sandbox signup tier; ~₹0.50 per call after that.
   *
   * <p>The DigiLocker flow stays dormant in the codebase — when
   * Anirudh Homes incorporates and gets DigiLocker partner approval,
   * switch {@code app.kyc.provider=DIGILOCKER} + {@code app.kyc.pan-only-kyc=false}
   * on the backend to upgrade to one-tap Aadhaar verification.
   */
  KYC_DISABLED: false as const,

  /**
   * Saved-search Alerts paused platform-wide. Same pattern as KYC:
   *  - tenant /app/saved-searches route gated by FeatureDisabledOutlet
   *  - sidebar "Paused" pill on the Alerts nav item
   *
   * Per product call — the saved-search backend infra (alerts table,
   * matcher scheduler) is stable but we're polishing the email-digest
   * copy before re-opening it. Flip to false to re-enable.
   */
  ALERTS_DISABLED: true as const,

  /**
   * Owner-side RERA + GST compliance tools paused platform-wide.
   * Same three-part gate as KYC:
   *  - owner /owner/compliance route gated by FeatureDisabledOutlet
   *  - sidebar "Paused" pill on the Compliance nav item
   *
   * Paused while the compliance-service's RERA provider integration
   * is being swapped from MOCK to live. Re-enable by setting to false.
   */
  COMPLIANCE_DISABLED: true as const,

  /**
   * Razorpay-mediated payment paths (rent + society charge) disabled
   * platform-wide. When true, tenant Pay pages surface ONLY the
   * direct-UPI QR path — tenant scans a QR pointing at the recipient's
   * own UPI (owner for rent, maintainer for society charges), money
   * moves directly to the recipient's bank, recipient marks the
   * charge PAID from their dashboard.
   *
   * <p>Why paused: Razorpay Standard sends every payment into the
   * PLATFORM's linked bank account, then relies on manual
   * distribution to individual owners/maintainers. That's an RBI
   * Payment Aggregator model that needs a PA license (not
   * appropriate for MVP), plus doesn't match the product intent
   * that "owner adds bank details, tenant pays owner directly".
   *
   * <p>Turn back on when Razorpay Route (split-settlement to per-
   * owner linked accounts) is wired up. All the Razorpay-side
   * frontend and payment-service code stays intact behind this
   * flag — flipping to false restores the two-option UI.
   */
  RAZORPAY_PAYMENTS_DISABLED: true as const,
} as const;

/** True when the KYC feature is currently turned off platform-wide. */
export function isKycDisabled(): boolean {
  return FEATURE_FLAGS.KYC_DISABLED;
}

/** True when the saved-search Alerts feature is paused. */
export function isAlertsDisabled(): boolean {
  return FEATURE_FLAGS.ALERTS_DISABLED;
}

/** True when the owner Compliance tools are paused. */
export function isComplianceDisabled(): boolean {
  return FEATURE_FLAGS.COMPLIANCE_DISABLED;
}

/** True when Razorpay-mediated payments are paused platform-wide.
 *  When true, tenant Pay pages route through the direct-UPI-only
 *  flow instead of showing the Razorpay method picker. */
export function isRazorpayPaymentsDisabled(): boolean {
  return FEATURE_FLAGS.RAZORPAY_PAYMENTS_DISABLED;
}
