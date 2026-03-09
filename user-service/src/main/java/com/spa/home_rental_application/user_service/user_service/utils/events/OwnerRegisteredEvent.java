package com.spa.home_rental_application.user_service.user_service.utils.events;

import lombok.*;

import java.time.Instant;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OwnerRegisteredEvent {
    private String eventType;
    private String ownerId;
    private String businessName;
    private Instant timestamp;
}
