package com.spa.home_rental_application.property_service.property_service.DTO.Response;

import java.math.BigDecimal;
import java.time.Instant;

public record SavedSearchResponse(
        String id,
        String userId,
        String name,
        String city,
        Integer bedrooms,
        BigDecimal minRent,
        BigDecimal maxRent,
        Double minAreaSqft,
        String furnishingStatus,
        Boolean petFriendly,
        Boolean isActive,
        Instant lastMatchedAt,
        Instant createdAt
) {}
