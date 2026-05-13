package com.spa.home_rental_application.auth_service.Repository;

import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.enums.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserDetails, Long> {

    Optional<UserDetails> findByUserName(String userName);

    Optional<UserDetails> findByEmailIgnoreCase(String email);

    boolean existsByUserName(String userName);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Used at registration to enforce the same one-account-per-phone
     * rule we have for email. The phone is normalised to E.164 in
     * AuthServiceImpl.register BEFORE the check, so the comparison
     * is a straight exact match (no IgnoreCase / no LIKE — every
     * caller already canonicalises).
     */
    boolean existsByPhone(String phone);

    List<UserDetails> findByUserRole(Roles userRole);
}
