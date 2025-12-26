package com.spa.home_rental_application.user_service.user_service.mapper;

import com.spa.home_rental_application.user_service.user_service.DTO.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContacts;

public class EmergencyContactMapper {

    public static EmergencyContacts toEntity(EmergencyContactRequestDto dto) {
        if (dto == null) {
            return null;
        }

        return EmergencyContacts.builder()
                .userId(dto.userId())
                .name(dto.name())
                .relation(dto.relation())
                .phone(dto.phone())
                .build();
    }

    public static EmergencyContactResponseDto toDto(EmergencyContacts entity) {
        if (entity == null) {
            return null;
        }

        return new EmergencyContactResponseDto(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getRelation(),
                entity.getPhone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
