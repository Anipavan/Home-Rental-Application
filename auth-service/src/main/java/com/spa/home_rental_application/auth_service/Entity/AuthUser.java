package com.spa.home_rental_application.auth_service.Entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUser {
    private String userName;
    private  String password;
}
