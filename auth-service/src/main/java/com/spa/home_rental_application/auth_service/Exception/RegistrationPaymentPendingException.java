package com.spa.home_rental_application.auth_service.Exception;

import org.springframework.security.authentication.DisabledException;

/**
 * Thrown by {@code AuthServiceImpl.login} when the authenticating user
 * is disabled <em>specifically</em> because their one-time maintainer
 * registration fee hasn't been paid yet
 * ({@code disable_reason='REGISTRATION_PAYMENT_PENDING'}).
 *
 * <p>Extends {@link DisabledException} so anything downstream that
 * catches {@code DisabledException} (Spring Security plumbing, audit
 * helpers) still sees a disabled account. The dedicated handler in
 * {@link GlobalExceptionHandler} catches this subclass <em>first</em>
 * and returns the distinct {@code REGISTRATION_PAYMENT_PENDING} error
 * code + the {@code paymentId} the frontend needs to resume the
 * paywall.
 */
public class RegistrationPaymentPendingException extends DisabledException {

    private final String paymentPendingForUserId;

    public RegistrationPaymentPendingException(String authUserId) {
        super("Registration payment pending");
        this.paymentPendingForUserId = authUserId;
    }

    public String getPaymentPendingForUserId() {
        return paymentPendingForUserId;
    }
}
