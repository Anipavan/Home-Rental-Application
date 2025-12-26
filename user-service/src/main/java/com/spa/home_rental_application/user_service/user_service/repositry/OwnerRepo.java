package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OwnerRepo extends JpaRepository<Owners,String> {

    @Query("SELECT u FROM User u " +
            "WHERE u.id IN (SELECT o.userId FROM Owners o WHERE o.id = :ownerId)")
    List<User> findTenantsByOwnerId(@Param("ownerId") String ownerId);
}
