package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.OwnerMapper;
import com.spa.home_rental_application.user_service.user_service.mapper.UserMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.OwnerRepo;
import com.spa.home_rental_application.user_service.user_service.service.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OwnerServiceImpul implements OwnerService {
    @Autowired
    OwnerRepo ownerRepo;
    @Autowired
    UserServiceEvents userServiceEvent;
    @Override
    public OwnerResponseDto createOwner(OwnerRequestDto ownerRequest) {

        Owners owner= OwnerMapper.toEntity(ownerRequest);
        owner.setCreatedAt(LocalDateTime.now());
        owner.setUpdatedAt(LocalDateTime.now());
        Owners savedOwner=ownerRepo.save(owner);

        userServiceEvent.sendOwnerRegistered(OwnerRegisteredEvent.builder()
                        .eventType("Owner-Registered")
                        .ownerId(savedOwner.getId())
                        .timestamp(Instant.now())
                .build());
        return OwnerMapper.toDto(savedOwner);
    }

    @Override
    public OwnerResponseDto getOwnerById(String ownerId) {
        Owners owner =ownerRepo.findById(ownerId).orElseThrow(()->new RecordNotFound("Owner with the given Id is not Prest :"+ownerId));
        return OwnerMapper.toDto(owner);
    }

    @Override
    public OwnerResponseDto updateOwner(String ownerId, OwnerRequestDto ownerRequest) {
        Owners owner=OwnerMapper.toEntity(ownerRequest);

        Owners foundOwner = ownerRepo.findById(ownerId)
                .orElseThrow(() -> new RecordNotFound("Owner with given Id is not present : " + ownerId));

        if (owner.getUserId() != null && !owner.getUserId().isBlank()) {
            foundOwner.setUserId(owner.getUserId());
        }
        if (owner.getBusinessName() != null && !owner.getBusinessName().isBlank()) {
            foundOwner.setBusinessName(owner.getBusinessName());
        }
        if (owner.getGstNumber() != null && !owner.getGstNumber().isBlank()) {
            foundOwner.setGstNumber(owner.getGstNumber());
        }
        if (owner.getPanNumber() != null && !owner.getPanNumber().isBlank()) {
            foundOwner.setPanNumber(owner.getPanNumber());
        }
        if (owner.getBankAccountNumber() != null && !owner.getBankAccountNumber().isBlank()) {
            foundOwner.setBankAccountNumber(owner.getBankAccountNumber());
        }
        if (owner.getIfscCode() != null && !owner.getIfscCode().isBlank()) {
            foundOwner.setIfscCode(owner.getIfscCode());
        }
        if (owner.getTotalProperties() != null) {
            foundOwner.setTotalProperties(owner.getTotalProperties());
        }
        foundOwner.setUpdatedAt(LocalDateTime.now());
        return OwnerMapper.toDto(ownerRepo.save(foundOwner));
    }


    @Override
    public Page<OwnerResponseDto> getAllOwners(Pageable pageable) {
       Page<Owners> owners=ownerRepo.findAll(pageable);

        return owners.map(OwnerMapper::toDto);
    }

    @Override
    public List<UserResponseDto> getTenentsByOwnerId(String ownerId) {
        List<User>users=ownerRepo.findTenantsByOwnerId(ownerId);
        return users.stream().map(UserMapper::toDto).collect(Collectors.toList());
    }
}
