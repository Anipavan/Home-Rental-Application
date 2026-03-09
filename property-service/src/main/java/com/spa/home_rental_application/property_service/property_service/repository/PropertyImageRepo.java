package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyImageRepo extends JpaRepository<PropertyImage,String> {
    List<PropertyImage> findByPropertyId(String propertyId);
}
