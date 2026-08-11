package com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Fired when a user saves or updates their bank account details via
 * user-service's {@code PUT /users/bank-accounts/user/{userId}}.
 *
 * <p>Consumed by payment-service to trigger Cashfree vendor
 * registration once the user also has verified KYC. Idempotent on
 * the consumer side — repeat events for the same user just re-run
 * the "check and register if ready" logic.
 *
 * <p>The full account number and IFSC are deliberately NOT included
 * in the payload — the consumer fetches them via a scoped Feign
 * call at registration time so bank details never enter Kafka's
 * message log.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountSavedEvent {
    /** Always "user.bank-account.saved". */
    private String eventType;
    /** Auth user id (String form for cross-service consistency). */
    private String userId;
    /** Last-4 of the account number, for logging / audit only. */
    private String accountNumberLast4;
    /** Non-secret display info. */
    private String bankName;
    private Instant timestamp;
}
