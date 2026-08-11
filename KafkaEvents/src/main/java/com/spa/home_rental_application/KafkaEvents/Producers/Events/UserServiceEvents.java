package com.spa.home_rental_application.KafkaEvents.Producers.Events;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.BankAccountSavedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;

public interface UserServiceEvents {
    void sendUserProfileCreated(UserProfileCreatedEvent event);
    void sendUserProfileUpdated(UserProfileUpdatedEvent event);
    void sendOwnerRegistered(OwnerRegisteredEvent event);

    /**
     * Publishes {@code user.bank-account.saved}. Payment-service consumes
     * this to trigger Cashfree vendor registration once KYC is also
     * verified. Idempotent — fire on every save (create + update).
     */
    void sendBankAccountSaved(BankAccountSavedEvent event);
}
