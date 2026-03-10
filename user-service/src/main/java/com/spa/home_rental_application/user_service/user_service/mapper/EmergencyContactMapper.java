package com.spa.home_rental_application.user_service.user_service.mapper;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.EmergencyContactResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContact;

public class EmergencyContactMapper {

    public static EmergencyContact toEntity(EmergencyContactRequestDto dto) {
        if (dto == null) {
            return null;
        }

        return EmergencyContact.builder()
                .userId(dto.userId())
                .name(dto.name())
                .relation(dto.relation())
                .phone(dto.phone())
                .build();
    }

    public static EmergencyContactResponseDto toDto(EmergencyContact entity) {
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
