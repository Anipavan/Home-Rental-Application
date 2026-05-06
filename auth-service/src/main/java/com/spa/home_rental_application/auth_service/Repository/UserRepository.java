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

    List<UserDetails> findByUserRole(Roles userRole);
}
