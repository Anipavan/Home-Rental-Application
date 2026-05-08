package com.spa.home_rental_application.compliance_service.provider;

import com.spa.home_rental_application.compliance_service.DTO.Request.ReraRegisterRequest;

import java.time.LocalDate;

/**
 * Strategy interface for state RERA portals. Each Indian state runs its own
 * RERA portal with its own number format, so this abstraction lets us plug
 * in {@code KarnatakaReraAdapter}, {@code MaharashtraReraAdapter}, etc.
 * without changing the service layer.
 */
public interface ReraPortalAdapter {

    String name();

    /** Register the property and return the assigned RERA number + expiry. */
    RegistrationResult register(ReraRegisterRequest request);

    record RegistrationResult(
            boolean success,
            String reraRegistrationNumber,
            String reraPortalId,
            LocalDate expiryDate,
            String failureReason) {}
}
