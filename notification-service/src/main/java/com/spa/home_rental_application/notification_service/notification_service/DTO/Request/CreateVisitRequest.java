package com.spa.home_rental_application.notification_service.notification_service.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body for {@code POST /notifications/visit-requests}. Submitted from the
 * public property-detail page's "Schedule a visit" dialog. Anonymous
 * public visitors send {@code userId="PUBLIC_VISITOR"}; signed-in
 * visitors send their authUserId.
 */
public record CreateVisitRequest(
        @NotBlank String userId,

        @NotBlank @Size(max = 200) String visitorName,
        @Email   @Size(max = 200) String visitorEmail,
        @Size(max = 30) String visitorPhone,

        @NotBlank String flatId,
        String buildingId,

        @Size(max = 200) String propertyLabel,

        /** When the visitor wants to come. ISO-8601 instant. */
        Instant preferredAt,

        @Size(max = 2000) String message,
        @Size(max = 500) String contextUrl
) {
}
