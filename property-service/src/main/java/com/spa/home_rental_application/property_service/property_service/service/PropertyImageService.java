package com.spa.home_rental_application.property_service.property_service.service;

import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface PropertyImageService {
    void uploadImage(String propertyId, MultipartFile file) throws IOException;
    List<PropertyImage> getImages(String id);
}
