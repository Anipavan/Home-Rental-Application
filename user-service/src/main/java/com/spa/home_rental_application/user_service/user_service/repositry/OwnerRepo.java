package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OwnerRepo extends JpaRepository<Owners,String> {

    @Query("SELECT o FROM Owner o WHERE o.id = :ownerId" +
            "WHERE o.id = :ownerId")
    List<Owners> getTenentsByOwnerId(@Param("ownerId") String ownerId);
}
