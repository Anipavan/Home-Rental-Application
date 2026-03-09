package com.spa.home_rental_application.property_service.property_service.DTO.Request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FlatRequestDTO (@NotBlank(message = "Building ID is required")
String buildingId,

@NotBlank(message = "Flat number is required")
String flatNumber,

@NotNull(message = "Floor is required")
@PositiveOrZero
Integer floor,

@NotNull(message = "Bedrooms is required")
@Positive
Integer bedrooms,

@NotNull(message = "Bathrooms is required")
@Positive
Integer bathrooms,

@NotNull(message = "Area is required")
@Positive
Double areaSqft,

@NotNull(message = "Rent amount is required")
@Positive
BigDecimal rentAmount,

String tenantId,
LocalDate leaseStartDate,
LocalDate leaseEndDate
) {}
