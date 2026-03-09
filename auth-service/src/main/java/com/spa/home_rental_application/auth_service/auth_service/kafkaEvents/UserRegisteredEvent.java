package com.spa.home_rental_application.auth_service.auth_service.kafkaEvents;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Auth Service Data
    private Long authUserId;
    private String username;
    private String email;
    private String role;

    // User Service Data
    private String firstName;
    private String lastName;
    private String phone;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String dateOfBirth;
    private String gender;
    private String address;

    // Event Metadata
    private String eventType;
    private long timestamp;
}
