package com.spa.home_rental_application.KafkaEvents.Producers.DTO.PropertyServiceEvents;

import lombok.*;

import java.time.Instant;

/**
 * Fired when a maintainer/owner saves or updates the SOCIETY's bank +
 * KYC details via property-service's society-config endpoints.
 * Consumed by payment-service to (re-)register a Cashfree Easy Split
 * vendor keyed on the SOCIETY (not the maintainer user), so tenant
 * maintenance payments settle to the society's own bank account
 * instead of the maintainer's personal one.
 *
 * <p>Mirrors {@code BankAccountSavedEvent} in shape but semantically
 * distinct — the payment-service consumer looks these up by
 * {@code buildingId} and Feigns back for the full bank + KYC record
 * from property-service (secrets never enter Kafka's message log).
 *
 * <p>Idempotent on the consumer side — repeat events for the same
 * building just re-run "check and register / update if ready".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocietyBankAccountSavedEvent {
    /** Always "society.bank-account.saved". */
    private String eventType;
    /** Building the society config belongs to (routing key). */
    private String buildingId;
    /** The maintainer/owner who saved the details — audit only. */
    private String savedByUserId;
    /** Last-4 of the account number, for logging / audit only. */
    private String accountNumberLast4;
    /** Non-secret display info. */
    private String payeeName;
    private Instant timestamp;
}
