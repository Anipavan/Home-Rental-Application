import { api } from "./client";
import type {
  CreateMembershipClaimRequest,
  DecideMembershipClaimRequest,
  MembershipClaim,
} from "@/types/api";

/**
 * Self-service membership claims. Two flavours wrapped in one client:
 *
 *  • Submission — anyone logged in can `create` a claim against a
 *    building (request to be its maintainer or resident).
 *  • Decision — building owners list `pendingForOwner` and approve /
 *    reject. The applicant can `withdraw` their own pending claim.
 *
 * Talks to property-service's `/society/claims` namespace. JWT auth
 * is enforced on every endpoint (no public claims).
 */
export const claimsApi = {
  create: (body: CreateMembershipClaimRequest) =>
    api
      .post<MembershipClaim>("/society/claims", body)
      .then((r) => r.data),

  pendingForOwner: () =>
    api
      .get<MembershipClaim[]>("/society/claims/pending/owner")
      .then((r) => r.data),

  /** Dual-approval claims awaiting the calling user's decision as the
   *  current maintainer of the affected building(s). */
  pendingForMaintainer: () =>
    api
      .get<MembershipClaim[]>("/society/claims/pending/maintainer")
      .then((r) => r.data),

  mine: () =>
    api.get<MembershipClaim[]>("/society/claims/mine").then((r) => r.data),

  approve: (claimId: string, body?: DecideMembershipClaimRequest) =>
    api
      .put<MembershipClaim>(`/society/claims/${claimId}/approve`, body ?? {})
      .then((r) => r.data),

  reject: (claimId: string, body?: DecideMembershipClaimRequest) =>
    api
      .put<MembershipClaim>(`/society/claims/${claimId}/reject`, body ?? {})
      .then((r) => r.data),

  withdraw: (claimId: string) =>
    api
      .put<MembershipClaim>(`/society/claims/${claimId}/withdraw`)
      .then((r) => r.data),
};
