package com.spa.home_rental_application.auth_service.Exception;

/**
 * Thrown by {@code AuthServiceImpl.login} when the
 * {@code email_verification_required} toggle is ON and the user's
 * {@code email_verified} column is still false. Maps to HTTP 403
 * with {@code errorCode=EMAIL_VERIFICATION_REQUIRED} in
 * {@link GlobalExceptionHandler} so the frontend can surface a
 * "verify your email before logging in" prompt + resend button
 * instead of the generic "account disabled" copy.
 */
public class EmailVerificationRequiredException extends RuntimeException {
    private final String email;

    public EmailVerificationRequiredException(String email) {
        super("Email verification required for " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
