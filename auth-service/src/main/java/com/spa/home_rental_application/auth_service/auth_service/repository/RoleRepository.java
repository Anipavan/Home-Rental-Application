package com.spa.home_rental_application.auth_service.auth_service.repository;

import com.spa.home_rental_application.auth_service.auth_service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
}
