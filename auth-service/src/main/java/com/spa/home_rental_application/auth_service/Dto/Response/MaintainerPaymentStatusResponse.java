package com.spa.home_rental_application.auth_service.Dto.Response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Returned from {@code GET /auth/me/payment-status}. The frontend's
 * {@code MaintainerPaymentGate} keys its rendering off
 * {@link #status}: PAID renders the dashboard plain, TRIAL adds a
 * countdown banner, SKIP_GRACE adds an amber banner, PROMPT and
 * FORCED open the payment modal.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code status} — see the state machine in
 *       {@code SystemSettingsServiceImpl.computeStatus}.</li>
 *   <li>{@code trialDaysLeft} — only meaningful in TRIAL state.
 *       Null otherwise.</li>
 *   <li>{@code skipsLeft} — 0, 1, or 2. Tells the modal whether to
 *       render the Skip button and how many tries are left.</li>
 *   <li>{@code nextPromptAt} — only meaningful in SKIP_GRACE state;
 *       drives the banner copy "Next prompt on &lt;date&gt;".</li>
 *   <li>{@code amountInr} — fee shown on the modal. Resolved from
 *       {@code app.maintainer-registration.fee-inr} so an env
 *       change updates it without code.</li>
 * </ul>
 */
public record MaintainerPaymentStatusResponse(
        Status status,
        Integer trialDaysLeft,
        Integer skipsLeft,
        Instant nextPromptAt,
        BigDecimal amountInr
) {
    public enum Status {
        PAID, TRIAL, PROMPT, SKIP_GRACE, FORCED
    }
}
