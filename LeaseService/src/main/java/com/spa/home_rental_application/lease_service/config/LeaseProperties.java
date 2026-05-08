package com.spa.home_rental_application.lease_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.lease")
@Getter
@Setter
public class LeaseProperties {
    /** Days-before-end-date at which we publish lease.expiring + send a warning. */
    private int expiryWarningDays = 60;

    /** Default rent increment % to apply on renewal when none supplied. */
    private BigDecimal defaultRentIncrementPercent = new BigDecimal("5.00");

    /** Local filesystem dir to store generated lease PDFs (S3 in prod). */
    private String deedStorageDir = "uploads/lease-deeds";

    /** Cron expression for the daily expiry sweep. */
    private String expiryCron = "0 0 2 * * *";
}
