import { api } from "./client";
import type {
  AddExpenseRequest,
  MaintenanceExpense,
  SetupSocietyRequest,
  SocietyConfig,
  SocietyLedger,
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
};
