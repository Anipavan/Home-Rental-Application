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
   * KYC is currently paused platform-wide. Flips off ALL surfaces:
   *  - tenant /app/kyc route (gated by FeatureDisabledOutlet)
   *  - tenant kyc.tsx API queries (skipped via `enabled: !disabled`)
   *  - owner /owner/tenants/:id KYC badge (hidden when paused)
   *  - sidebar "Paused" pill on the KYC nav item
   *
   * Re-enable by setting to false.
   */
  KYC_DISABLED: true as const,

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
