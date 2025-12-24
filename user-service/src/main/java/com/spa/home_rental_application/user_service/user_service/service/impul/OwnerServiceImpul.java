package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.repositry.OwnerRepo;
import com.spa.home_rental_application.user_service.user_service.service.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OwnerServiceImpul implements OwnerService {
    @Autowired
    OwnerRepo ownerRepo;
    @Override
    public Owners createOwner(Owners owner) {
        owner.setCreatedAt(LocalDateTime.now());
        owner.setUpdated_at(LocalDateTime.now());
        return ownerRepo.save(owner);
    }

    @Override
    public Owners getOwnerById(String ownerId) {
        return ownerRepo.findById(ownerId).orElse(null);
    }

    @Override
    public Owners updateOwner(String ownerId, Owners owner) {
        owner.setCreatedAt(LocalDateTime.now());
        owner.setUpdated_at(LocalDateTime.now());
        return ownerRepo.save(owner);
    }

    @Override
    public List<Owners> getAllOwners() {
        return ownerRepo.findAll();
    }

    @Override
    public List<User> getTenentsByOwnerId(String ownerId) {
        return ownerRepo.findTenantsByOwnerId(ownerId);
    }
}
