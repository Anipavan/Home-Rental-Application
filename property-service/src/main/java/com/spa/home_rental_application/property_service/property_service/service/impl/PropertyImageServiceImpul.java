package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.property_service.property_service.Entities.PropertyImage;
import com.spa.home_rental_application.property_service.property_service.repository.PropertyImageRepo;
import com.spa.home_rental_application.property_service.property_service.service.PropertyImageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
@Service

public class PropertyImageServiceImpul implements PropertyImageService {
    private final PropertyImageRepo repo;

    PropertyImageServiceImpul(PropertyImageRepo repo)
    {

        this.repo = repo;
    }





    public void uploadImage(String propertyId, MultipartFile file) throws IOException {

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        Path path = Paths.get("uploads/" + fileName);

        Files.write(path, file.getBytes());

        PropertyImage image = PropertyImage.builder()
                .propertyId(propertyId)
                .imageUrl(path.toString())
                .build();
        repo.save(image);
    }

    @Override
    public List<PropertyImage> getImages(String id) {
        return repo.findByPropertyId(id);
    }
}
