package com.spa.home_rental_application.user_service.user_service.mapper;

import com.spa.home_rental_application.user_service.user_service.DTO.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.Owners;

public class OwnerMapper {

    public static OwnerResponseDto toDto(Owners owner) {
        if (owner == null) {
            return null;
        }

        return new OwnerResponseDto(
                owner.getId(),
                owner.getUserId(),
                owner.getBusinessName(),
                owner.getGstNumber(),
                owner.getPanNumber(),
                owner.getBankAccountNumber(),
                owner.getIfscCode(),
                owner.getTotalProperties(),
                owner.getCreatedAt(),
                owner.getUpdatedAt()
        );
    }

    public static Owners toEntity(OwnerRequestDto dto) {
        if (dto == null) {
            return null;
        }

        return Owners.builder()
                .userId(dto.userId())
                .businessName(dto.businessName())
                .gstNumber(dto.gstNumber())
                .panNumber(dto.panNumber())
                .bankAccountNumber(dto.bankAccountNumber())
                .ifscCode(dto.ifscCode())
                .totalProperties(dto.totalProperties())
                .build();
    }
}
