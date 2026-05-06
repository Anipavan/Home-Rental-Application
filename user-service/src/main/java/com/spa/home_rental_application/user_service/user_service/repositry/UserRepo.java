package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, String> {

    @Query("SELECT u FROM User u WHERE u.isDeleted = false OR u.isDeleted IS NULL")
    Page<User> findAllActive(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.id = :id AND (u.isDeleted = false OR u.isDeleted IS NULL)")
    Optional<User> findActiveById(String id);

    Optional<User> findFirstByEmailIgnoreCaseAndIsDeletedFalse(String email);

    List<User> findByPhoneAndIsDeletedFalse(String phone);

    List<User> findByFirstNameContainingIgnoreCaseAndIsDeletedFalse(String firstName);

    Optional<User> findFirstByAuthUserIdAndIsDeletedFalse(String authUserId);

    boolean existsByEmailIgnoreCaseAndIsDeletedFalse(String email);
}
