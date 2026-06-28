import { api } from "./client";
import type {
  SetEmailVerificationRequiredRequest,
  SetMaintainerPaymentEnabledRequest,
  SystemSettingResponse,
} from "@/types/api";

/**
 * Admin global-toggle endpoints. ADMIN role gated server-side; the
 * gateway forwards X-Auth-Roles which Spring Security's
 * {@code @PreAuthorize("hasRole('ADMIN')")} on each route enforces.
 */
export const adminSettingsApi = {
  listAll: () =>
    api
      .get<SystemSettingResponse[]>("/auth/admin/settings")
      .then((r) => r.data),

  setMaintainerPaymentEnabled: (enabled: boolean) =>
    api
      .put<SystemSettingResponse>(
        "/auth/admin/settings/maintainer-payment-enabled",
        { enabled } satisfies SetMaintainerPaymentEnabledRequest,
      )
      .then((r) => r.data),

  setEmailVerificationRequired: (required: boolean) =>
    api
      .put<SystemSettingResponse>(
        "/auth/admin/settings/email-verification-required",
        { required } satisfies SetEmailVerificationRequiredRequest,
      )
      .then((r) => r.data),
};
