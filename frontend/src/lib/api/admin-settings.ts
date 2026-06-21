import { api } from "./client";
import type {
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
};
