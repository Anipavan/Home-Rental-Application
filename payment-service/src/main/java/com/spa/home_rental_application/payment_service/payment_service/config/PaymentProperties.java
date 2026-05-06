package com.spa.home_rental_application.payment_service.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PaymentProperties {
    /** Active gateway implementation: mock | razorpay | stripe */
    private String gateway = "mock";

    /** Late-fee accrual rate (percent of base amount per overdue week). */
    private BigDecimal lateFeePercentPerWeek = new BigDecimal("2.0");

    /** Cap on total accrued late fee (percent of base amount). */
    private BigDecimal maxLateFeePercent = new BigDecimal("10.0");

    /** Send a payment reminder N days before the due date. */
    private int reminderDaysBeforeDue = 3;
}
