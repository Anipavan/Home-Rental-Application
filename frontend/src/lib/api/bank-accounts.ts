import { api } from "./client";

/**
 * Bank-account API client — backs the Profile → Bank details section.
 * Self-or-admin gated server-side, so passing someone else's authUserId
 * here will 403. The full account number is never returned by the
 * backend; {@link BankAccountResponse.accountNumberMasked} renders
 * as {@code XXXX XXXX 1234}.
 */
export interface BankAccountRequest {
  accountHolderName: string;
  bankName: string;
  accountNumber: string;
  ifscCode: string;
  branch?: string | null;
  accountType?: "SAVINGS" | "CURRENT" | null;
  upiId?: string | null;
}

export interface BankAccountResponse {
  id: string;
  userId: string;
  accountHolderName: string;
  bankName: string;
  accountNumberMasked: string;
  ifscCode: string;
  branch?: string | null;
  accountType?: string | null;
  upiId?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** Payable subset — what a payer needs to actually pay another user.
 *  Backed by GET /users/bank-accounts/payout/{userId} which is open
 *  to any authenticated caller (the full-detail endpoint stays
 *  self-or-admin gated). Full account number is NEVER included — only
 *  the masked form. */
export interface BankAccountPayoutResponse {
  accountHolderName: string;
  bankName: string;
  accountNumberMasked: string;
  ifscCode: string;
  branch?: string | null;
  accountType?: string | null;
  /** UPI VPA. Empty when the recipient hasn't added one — payer has
   *  to fall back to NEFT with the bank details. */
  upiId?: string | null;
}

export const bankAccountsApi = {
  /**
   * Fetch the bank account on file. Resolves to {@code null} when the
   * user hasn't saved one yet (backend returns 404 in that case, which
   * we translate to null so the UI can render an empty-state CTA
   * without a try/catch at the call site).
   */
  getByUserId: (userId: string) =>
    api
      .get<BankAccountResponse>(`/users/bank-accounts/user/${userId}`)
      .then((r) => r.data)
      .catch((err) => {
        if (err?.response?.status === 404) return null;
        throw err;
      }),

  /** Upsert (creates on first save, updates on subsequent saves). */
  upsert: (userId: string, body: BankAccountRequest) =>
    api
      .put<BankAccountResponse>(`/users/bank-accounts/user/${userId}`, body)
      .then((r) => r.data),

  /** Remove the saved bank account. Idempotent on the server. */
  remove: (userId: string) =>
    api
      .delete<void>(`/users/bank-accounts/user/${userId}`)
      .then((r) => r.data),

  /** Payable subset for ANY authenticated caller — used by the tenant
   *  pay page to fetch the owner's UPI ID + bank details so it can
   *  render a direct-pay QR. Resolves to null on 404 (owner hasn't
   *  saved a bank account yet). */
  getPayoutByUserId: (userId: string) =>
    api
      .get<BankAccountPayoutResponse>(`/users/bank-accounts/payout/${userId}`)
      .then((r) => r.data)
      .catch((err) => {
        if (err?.response?.status === 404) return null;
        throw err;
      }),
};
