package com.spa.home_rental_application.user_service.user_service.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "owners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owners {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "gst_number", unique = true)
    private String gstNumber;

    @Column(name = "pan_number", unique = true)
    private String panNumber;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "total_properties")
    private Integer totalProperties;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false, updatable = false)
    private LocalDateTime updated_at;

    @Override
    public String toString() {
        return "Owners{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", businessName='" + businessName + '\'' +
                ", gstNumber='" + gstNumber + '\'' +
                ", panNumber='" + panNumber + '\'' +
                ", bankAccountNumber='" + bankAccountNumber + '\'' +
                ", ifscCode='" + ifscCode + '\'' +
                ", totalProperties=" + totalProperties +
                ", createdAt=" + createdAt +
                ", updated_at=" + updated_at +
                '}';
    }
}
