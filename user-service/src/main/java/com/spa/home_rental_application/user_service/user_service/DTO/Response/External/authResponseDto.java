package com.spa.home_rental_application.user_service.user_service.DTO.Response.External;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class authResponseDto {

    private String id;
    private String userName;
    private String userRole;
    /**
     * Auth-service's AuthUserResponse exposes this; user-service's
     * self-heal path uses it as the email for newly-created stub User
     * rows. Kept optional — a missing field deserializes to null and
     * the caller falls back to a placeholder.
     */
    private String email;
    private Instant recordCreatedDate;
    private Instant recodeUpdatedDate;


    @Override
    public String toString() {
        return "authResponseDto{" +
                "id='" + id + '\'' +
                ", userName='" + userName + '\'' +
                ", userRole='" + userRole + '\'' +
                ", email='" + email + '\'' +
                ", recordCreatedDate=" + recordCreatedDate +
                ", recodeUpdatedDate=" + recodeUpdatedDate +
                '}';
    }
}