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
   * Cashfree Easy Split checkout enabled platform-wide. When false
   * (default until Phase 3–6 of the split-payment plan lands), tenant
   * Pay pages surface ONLY the direct-UPI QR path. When true, owners
   * with a registered Cashfree vendor id and verified KYC route
   * through the Cashfree Checkout instead — money splits at Cashfree
   * (owner's share → their bank, commission → yours) before either
   * side sees it, so no PA license is needed on our end.
   *
   * <p>The Razorpay-based paths (both Standard and Route) were removed
   * entirely alongside this flag being introduced. Cashfree replaced
   * them because Razorpay Route required a turnover threshold we
   * don't have yet, whereas Cashfree onboards at any scale.
   */
  CASHFREE_SPLIT_CHECKOUT_ENABLED: true as const,
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

/** True when the Cashfree Easy Split checkout is enabled and should
 *  replace the direct-UPI QR path for owners who are payout-ready.
 *  False (default) → every Pay page uses the direct-UPI QR path.
 *  True → payout-ready owners get the Cashfree Checkout; others fall
 *  back to direct-UPI automatically. */
export function isCashfreeSplitCheckoutEnabled(): boolean {
  return FEATURE_FLAGS.CASHFREE_SPLIT_CHECKOUT_ENABLED;
}
