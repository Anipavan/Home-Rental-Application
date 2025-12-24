package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;

import java.util.List;

public interface OwnerService {
    Owners createOwner(Owners owner);
    Owners getOwnerById(String ownerId);
    Owners updateOwner(String ownerId,Owners owner);
    List<Owners> getAllOwners();
    List<User> getTenentsByOwnerId(String ownerId);
}
