package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OwnerService {
    OwnerResponseDto createOwner(OwnerRequestDto ownerRequest);
    OwnerResponseDto getOwnerById(String ownerId);
    OwnerResponseDto updateOwner(String ownerId,OwnerRequestDto owner);
    Page<OwnerResponseDto> getAllOwners(Pageable pageable);
    List<UserResponseDto> getTenentsByOwnerId(String ownerId);
}
