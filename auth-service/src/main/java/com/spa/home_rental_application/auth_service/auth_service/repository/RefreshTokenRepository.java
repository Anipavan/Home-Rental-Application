package com.spa.home_rental_application.auth_service.auth_service.repository;

import com.spa.home_rental_application.auth_service.auth_service.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByUserIdAndToken(Long userId, String token);
    void deleteByUserId(Long userId);
}
