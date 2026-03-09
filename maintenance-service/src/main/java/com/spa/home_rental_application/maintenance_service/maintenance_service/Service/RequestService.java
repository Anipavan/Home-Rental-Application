package com.spa.home_rental_application.maintenance_service.maintenance_service.Service;

import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;

import java.util.List;
import java.util.Optional;

public interface RequestService {
    MaintenanceRequest createRequest(MaintenanceRequest request);
    MaintenanceRequest updateRequest(String id,MaintenanceRequest request);
    MaintenanceRequest deleteRequest(String id);
    List<MaintenanceRequest> getAllRequests();
    List<MaintenanceRequest> getRequestsByStatus(String status);
    List<MaintenanceRequest> getRequestsByPriority(String priority);
    List<MaintenanceRequest> getRequestByCategory(String category);
    Optional<MaintenanceRequest> getRequestsById(String requestId);
    MaintenanceRequest assignToTechnician(String requestId,MaintenanceRequest request);
    Integer getPendingRequestCount();
}
