import { api } from "./client";

/**
 * Commission rule as returned by payment-service. Rates come back in
 * BOTH forms — rateBps (persisted) and ratePercent (display) — so the
 * admin page never has to duplicate the /100 conversion.
 */
export interface CommissionRuleResponse {
  id: string;
  /** null for the global-default row; populated for per-owner overrides. */
  ownerId: string | null;
  rateBps: number;
  ratePercent: number;
  updatedByAdminId?: string | null;
  notes?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Payload for setting the global rate OR upserting a per-owner
 * override. Percentage matches what the admin types in the form
 * (2 → 2 %, 0 → 0 %, 2.5 → 2.5 %).
 */
export interface CommissionRuleUpsertRequest {
  ratePercent: number;
  notes?: string | null;
}

/**
 * Dry-run compute response — drives the live "on ₹10,000, you keep
 * ₹200, owner gets ₹9,800" preview strip.
 */
export interface CommissionPreviewResponse {
  totalAmount: number;
  platformFee: number;
  ownerAmount: number;
  appliedRateBps: number;
  appliedRatePercent: number;
  /** "global" | "owner" — tells the UI which rule fired. */
  source: "global" | "owner";
  ownerId?: string | null;
}

export const commissionApi = {
  getGlobal: () =>
    api
      .get<CommissionRuleResponse>("/payments/commission/global")
      .then((r) => r.data),

  setGlobal: (body: CommissionRuleUpsertRequest) =>
    api
      .put<CommissionRuleResponse>("/payments/commission/global", body)
      .then((r) => r.data),

  listOverrides: () =>
    api
      .get<CommissionRuleResponse[]>("/payments/commission/overrides")
      .then((r) => r.data),

  upsertOverride: (ownerId: string, body: CommissionRuleUpsertRequest) =>
    api
      .put<CommissionRuleResponse>(`/payments/commission/overrides/${ownerId}`, body)
      .then((r) => r.data),

  deleteOverride: (ownerId: string) =>
    api
      .delete<void>(`/payments/commission/overrides/${ownerId}`)
      .then((r) => r.data),

  preview: (amount: number, ownerId?: string) => {
    const params = new URLSearchParams({ amount: String(amount) });
    if (ownerId) params.set("ownerId", ownerId);
    return api
      .get<CommissionPreviewResponse>(`/payments/commission/preview?${params.toString()}`)
      .then((r) => r.data);
  },
};
