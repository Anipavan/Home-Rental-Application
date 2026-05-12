package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PropertyImageRepo extends JpaRepository<PropertyImage,String> {
    List<PropertyImage> findByPropertyId(String propertyId);

    /**
     * Audit M10: atomic batched unset of the cover flag for every
     * image of a property except the one being promoted. The single
     * UPDATE acquires one set of row locks in one statement, closing
     * the race window that existed with the previous read-loop-save
     * sequence (two concurrent setCover calls could each unset the
     * other's target and leave two covers active).
     */
    @Modifying
    @Transactional
    @Query("UPDATE PropertyImage p SET p.isCover = false " +
           "WHERE p.propertyId = :propertyId AND p.id <> :exceptImageId AND p.isCover = true")
    int unsetCoverForProperty(@Param("propertyId") String propertyId,
                              @Param("exceptImageId") String exceptImageId);
}
