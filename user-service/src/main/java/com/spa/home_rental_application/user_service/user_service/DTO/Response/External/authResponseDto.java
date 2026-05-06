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
    private Instant recordCreatedDate;
    private Instant recodeUpdatedDate;


    @Override
    public String toString() {
        return "authResponseDto{" +
                "id='" + id + '\'' +
                ", userName='" + userName + '\'' +
                ", userRole='" + userRole + '\'' +
                ", recordCreatedDate=" + recordCreatedDate +
                ", recodeUpdatedDate=" + recodeUpdatedDate +
                '}';
    }
}