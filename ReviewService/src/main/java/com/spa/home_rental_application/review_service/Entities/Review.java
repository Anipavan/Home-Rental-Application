package com.spa.home_rental_application.review_service.Entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "reviews")
@CompoundIndexes({
        @CompoundIndex(name = "ix_target", def = "{'targetType': 1, 'targetId': 1, 'isDeleted': 1}"),
        @CompoundIndex(name = "ix_reviewer", def = "{'reviewerId': 1, 'createdAt': -1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    private String id;

    @Indexed
    private String reviewerId;
    private String reviewerType;       // TENANT | OWNER

    @Indexed
    private String targetId;
    private String targetType;         // PROPERTY | OWNER | TENANT

    private Integer rating;            // 1-5
    private String title;
    private String body;

    @Builder.Default
    private List<String> tags = List.of();

    @Builder.Default
    private Boolean isVerified = false;     // Set true if reviewer has an active lease for this property
    @Builder.Default
    private Boolean isModerated = false;
    @Builder.Default
    private String moderationStatus = "PENDING";   // PENDING | APPROVED | REJECTED | FLAGGED
    private String moderationReason;
    private String moderatedBy;
    private LocalDateTime moderatedAt;

    @Builder.Default
    private Boolean isDeleted = false;
    private LocalDateTime deletedAt;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
