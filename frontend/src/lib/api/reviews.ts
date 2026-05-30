import { api } from "./client";
import type {
  CreateReviewRequest,
  ModerateReviewRequest,
  Page,
  RatingSummary,
  ReviewResponse,
} from "@/types/api";

/**
 * Review Service (port 8094). Mounted at `/reviews/**` server-side; gateway
 * route is `/rentals/v1/reviews/**`.
 */
export const reviewsApi = {
  submit: (body: CreateReviewRequest) =>
    api.post<ReviewResponse>("/reviews", body).then((r) => r.data),

  getById: (id: string) =>
    api.get<ReviewResponse>(`/reviews/${id}`).then((r) => r.data),

  byProperty: (propertyId: string, page = 0, size = 10) =>
    api
      .get<Page<ReviewResponse>>(`/reviews/property/${propertyId}`, {
        params: { page, size },
      })
      .then((r) => r.data),

  byOwner: (ownerId: string, page = 0, size = 10) =>
    api
      .get<Page<ReviewResponse>>(`/reviews/owner/${ownerId}`, {
        params: { page, size },
      })
      .then((r) => r.data),

  byTenant: (tenantId: string, page = 0, size = 10) =>
    api
      .get<Page<ReviewResponse>>(`/reviews/tenant/${tenantId}`, {
        params: { page, size },
      })
      .then((r) => r.data),

  byReviewer: (reviewerId: string, page = 0, size = 10) =>
    api
      .get<Page<ReviewResponse>>(`/reviews/by/${reviewerId}`, {
        params: { page, size },
      })
      .then((r) => r.data),

  pendingModeration: (page = 0, size = 10) =>
    api
      .get<Page<ReviewResponse>>("/reviews/moderation/pending", {
        params: { page, size },
      })
      .then((r) => r.data),

  /**
   * Featured testimonials for the public landing page. Returns the
   * highest-rated, most-recent APPROVED reviews. Whitelisted at the
   * gateway so no JWT required.
   *
   * <p>Default size 3 matches the landing-page testimonial grid.
   */
  featured: (size = 3) =>
    api
      .get<Page<ReviewResponse>>("/reviews/featured", { params: { size } })
      .then((r) => r.data),

  moderate: (id: string, body: ModerateReviewRequest) =>
    api
      .put<ReviewResponse>(`/reviews/${id}/moderate`, body)
      .then((r) => r.data),

  remove: (id: string) =>
    api.delete<void>(`/reviews/${id}`).then((r) => r.data),

  summary: (targetType: "PROPERTY" | "OWNER" | "TENANT", targetId: string) =>
    api
      .get<RatingSummary>(`/reviews/summary/${targetType}/${targetId}`)
      .then((r) => r.data),
};
