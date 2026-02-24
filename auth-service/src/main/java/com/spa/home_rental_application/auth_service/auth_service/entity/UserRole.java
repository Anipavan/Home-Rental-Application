package com.spa.home_rental_application.auth_service.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_roles")
@IdClass(UserRole.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;


    @Column(name = "role_id")
    private Long roleId;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

}
