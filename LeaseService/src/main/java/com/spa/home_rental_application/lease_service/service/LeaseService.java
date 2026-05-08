package com.spa.home_rental_application.lease_service.service;

import com.spa.home_rental_application.lease_service.DTO.Request.CreateLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.RenewLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.SignLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Request.TerminateLeaseRequest;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseHistoryDto;
import com.spa.home_rental_application.lease_service.DTO.Response.LeaseResponseDto;

import java.io.IOException;
import java.util.List;

public interface LeaseService {

    LeaseResponseDto create(CreateLeaseRequest request);

    LeaseResponseDto getById(String id);

    List<LeaseResponseDto> getByTenantId(String tenantId);

    List<LeaseResponseDto> getByFlatId(String flatId);

    LeaseResponseDto renew(String leaseId, RenewLeaseRequest request);

    LeaseResponseDto terminate(String leaseId, TerminateLeaseRequest request);

    LeaseResponseDto sign(String leaseId, SignLeaseRequest request);

    byte[] downloadDeed(String leaseId) throws IOException;

    List<LeaseResponseDto> getLeasesExpiringWithin(int days);

    LeaseResponseDto generateReraStampedLease(String leaseId, String state);

    List<LeaseHistoryDto> getHistory(String leaseId);
}
