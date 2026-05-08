package com.spa.home_rental_application.KafkaEvents.Producers.DTO.DocumentServiceEvents;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Published once OCR / Document AI has extracted structured data from an
 * uploaded document. Consumed by KYC Service (auto-fill PAN holder name)
 * and User Service (auto-fill profile fields).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExtractedEvent {
    private String eventType;
    private String documentId;
    private String userId;
    private String documentType;
    private Map<String, String> extractedData;     // name, dob, idNumber, address, ...
    private Boolean fraudFlag;
    private BigDecimal confidenceScore;
    private LocalDateTime extractedAt;
    private LocalDateTime timestamp;
}
