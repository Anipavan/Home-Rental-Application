package com.spa.home_rental_application.property_service.property_service.DTO;

import com.spa.home_rental_application.property_service.property_service.DTO.Request.FlatRequestDTO;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import org.springframework.stereotype.Component;

@Component
public class FlatMapper {

    public Flat toEntity(FlatRequestDTO dto) {
        if (dto == null) return null;

        return Flat.builder()
                .buildingId(dto.buildingId())
                .flatNumber(dto.flatNumber())
                .floor(dto.floor())
                .bedrooms(dto.bedrooms())
                .bathrooms(dto.bathrooms())
                .areaSqft(dto.areaSqft())
                .rentAmount(dto.rentAmount())
                .tenantId(dto.tenantId())
                .leaseStartDate(dto.leaseStartDate())
                .leaseEndDate(dto.leaseEndDate())
                .build();
    }
    public FlatResponseDTO toResponseDTO(Flat flat) {
        if (flat == null) return null;

        return new FlatResponseDTO(
                flat.getId(),
                flat.getBuildingId(),
                flat.getFlatNumber(),
                flat.getFloor(),
                flat.getBedrooms(),
                flat.getBathrooms(),
                flat.getAreaSqft(),
                flat.getRentAmount(),
                flat.getIsOccupied(),
                flat.getTenantId(),
                flat.getLeaseStartDate(),
                flat.getLeaseEndDate(),
                flat.getCreatedAt(),
                flat.getUpdatedAt()
        );
    }
}