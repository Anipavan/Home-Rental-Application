package com.spa.home_rental_application.KafkaEvents.Producers.DTO.KycServiceEvents;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Published when the PAN number alone has been verified
 * (a sub-step of full KYC; needed for GST invoice generation).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycPanVerifiedEvent {
    private String eventType;
    private String userId;
    private String panNumber;          // masked when logged
    private String panHolderName;
    private Boolean panVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime timestamp;
}
