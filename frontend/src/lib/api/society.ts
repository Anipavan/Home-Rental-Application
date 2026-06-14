import { api } from "./client";
import type {
  AddExpenseRequest,
  EligibleMaintainer,
  FlatMaintenanceRow,
  MaintenanceExpense,
  PromoteTenantRequest,
  PromoteTenantResponse,
  SetupSocietyRequest,
  SocietyConfig,
  SocietyLedger,
  UpsertFlatCollectionRequest,
} from "@/types/api";

/**
 * Society / common-area maintenance API. Backend lives in
 * property-service under {@code /society/**}. The
 * {@code /society/public/{token}/ledger} endpoint is the only path
 * that doesn't require auth — wired to the gateway whitelist.
 */
export const societyApi = {
  // ── Config ──────────────────────────────────────────────────────
  setup: (buildingId: string, body: SetupSocietyRequest) =>
    api
      .post<SocietyConfig>(`/society/${buildingId}/setup`, body)
      .then((r) => r.data),

  get: (buildingId: string) =>
    api.get<SocietyConfig>(`/society/${buildingId}`).then((r) => r.data),

  update: (buildingId: string, body: SetupSocietyRequest) =>
    api
      .put<SocietyConfig>(`/society/${buildingId}`, body)
      .then((r) => r.data),

  regenerateToken: (buildingId: string) =>
    api
      .post<SocietyConfig>(`/society/${buildingId}/regenerate-token`)
      .then((r) => r.data),

  /** All societies the caller manages (as owner or assigned maintainer). */
  mine: () =>
    api.get<SocietyConfig[]>("/society/mine").then((r) => r.data),

  /** For the tenant view — society for the building the caller lives in. Null if none. */
  myTenant: () =>
    api
      .get<SocietyConfig | null>("/society/my-tenant")
      .then((r) => r.data),

  // ── Expenses ────────────────────────────────────────────────────
  addExpense: (buildingId: string, body: AddExpenseRequest) =>
    api
      .post<MaintenanceExpense>(`/society/${buildingId}/expenses`, body)
      .then((r) => r.data),

  updateExpense: (
    buildingId: string,
    expenseId: string,
    body: AddExpenseRequest,
  ) =>
    api
      .put<MaintenanceExpense>(
        `/society/${buildingId}/expenses/${expenseId}`,
        body,
      )
      .then((r) => r.data),

  deleteExpense: (buildingId: string, expenseId: string) =>
    api
      .delete<void>(`/society/${buildingId}/expenses/${expenseId}`)
      .then((r) => r.data),

  listExpenses: (buildingId: string, month?: string) =>
    api
      .get<MaintenanceExpense[]>(`/society/${buildingId}/expenses`, {
        params: month ? { month } : undefined,
      })
      .then((r) => r.data),

  // ── Ledger ──────────────────────────────────────────────────────
  ledger: (buildingId: string, month?: string) =>
    api
      .get<SocietyLedger>(`/society/${buildingId}/ledger`, {
        params: month ? { month } : undefined,
      })
      .then((r) => r.data),

  /** Public read-only ledger via shareable token. No auth header sent. */
  publicLedger: (token: string, month?: string) =>
    api
      .get<SocietyLedger>(`/society/public/${token}/ledger`, {
        params: month ? { month } : undefined,
      })
      .then((r) => r.data),

  // ── Maintainer assignment (owner-driven) ───────────────────────
  /**
   * Owner-only: the list of tenants currently in the building's flats.
   * Powers the AssignMaintainerDialog dropdown.
   */
  eligibleMaintainers: (buildingId: string) =>
    api
      .get<EligibleMaintainer[]>(`/society/${buildingId}/eligible-maintainers`)
      .then((r) => r.data),

  /**
   * Owner-only: promote a tenant to MAINTAINER + reset their password to
   * {@code temporaryPassword}. Server-side: role flip in auth-service +
   * config update in property-service, both wrapped in @Transactional.
   */
  promoteTenant: (buildingId: string, body: PromoteTenantRequest) =>
    api
      .post<PromoteTenantResponse>(
        `/society/${buildingId}/maintainer/promote-tenant`,
        body,
      )
      .then((r) => r.data),

  // ── Per-flat collections (maintainer dashboard) ────────────────
  /**
   * Owner / maintainer: per-flat per-month rows. One row per flat in
   * the building, with monthAmount resolved from the collection row or
   * the society default.
   */
  flatsForMonth: (buildingId: string, month?: string) =>
    api
      .get<FlatMaintenanceRow[]>(`/society/${buildingId}/flats`, {
        params: month ? { month } : undefined,
      })
      .then((r) => r.data),

  /**
   * Maintainer / owner: upsert the (flat, month) collection — typically
   * used to set the usage-based monthly amount + line-item notes
   * (water bill / gas / common-area share / etc.).
   */
  upsertFlatCollection: (
    buildingId: string,
    flatId: string,
    body: UpsertFlatCollectionRequest,
  ) =>
    api
      .post<FlatMaintenanceRow>(
        `/society/${buildingId}/flats/${flatId}/collection`,
        body,
      )
      .then((r) => r.data),

  /**
   * Tenant-side: every charge against the caller's own flat for the
   * given month. Caller must be a tenant of a flat in the building.
   * Drives the Pay-Now surface on /app/society.
   */
  myBills: (buildingId: string, month?: string) =>
    api
      .get<FlatMaintenanceRow[]>(`/society/${buildingId}/my-bills`, {
        params: month ? { month } : undefined,
      })
      .then((r) => r.data),

  /**
   * Tenant-side bulk-pay bridge. Hands the backend a set of DUE /
   * OVERDUE collectionIds; the backend creates a single Razorpay-
   * backed Payment row covering the sum and returns its paymentId.
   * The FE then navigates to {@code /app/payments/{paymentId}/pay}
   * — the exact same Razorpay flow as rent (UPI / Card / Net Banking
   * method picker).
   *
   * <p>The optional {@code idempotencyKey} guards against fast double-
   * clicks creating two Razorpay orders. Stripe convention; the
   * backend forwards it to payment-service as Idempotency-Key.
   */
  initiateSocietyChargePayment: (
    buildingId: string,
    collectionIds: string[],
    idempotencyKey?: string,
  ) =>
    api
      .post<{
        paymentId: string;
        totalAmount: number;
        collectionCount: number;
      }>(
        `/society/${buildingId}/charges/initiate-payment`,
        { collectionIds },
        idempotencyKey
          ? { headers: { "Idempotency-Key": idempotencyKey } }
          : undefined,
      )
      .then((r) => r.data),
};
