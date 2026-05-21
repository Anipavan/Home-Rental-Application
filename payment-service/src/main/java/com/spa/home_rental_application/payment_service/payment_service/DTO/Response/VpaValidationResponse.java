package com.spa.home_rental_application.payment_service.payment_service.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Outward shape of a UPI VPA validation result. Mirrors
 * {@link com.spa.home_rental_application.payment_service.payment_service.gateway.VpaValidationResult}
 * without the gatewayName field — that's a server-side concern, the
 * frontend doesn't need to know which gateway resolved the call.
 *
 * <p>Used by both:
 * <ul>
 *   <li>Owner Profile → Bank details — verify the VPA the owner is saving</li>
 *   <li>Tenant Pay → Other UPI — verify the VPA the tenant entered before
 *       launching a UPI collect call</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VpaValidationResponse(
        boolean valid,
        String vpa,
        String customerName,
        String failureReason
) {
}
