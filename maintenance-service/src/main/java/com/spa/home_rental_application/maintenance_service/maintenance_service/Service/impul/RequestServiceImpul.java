package com.spa.home_rental_application.maintenance_service.maintenance_service.Service.impul;

import com.spa.home_rental_application.maintenance_service.maintenance_service.Repository.MaintaincesRepo;
import com.spa.home_rental_application.maintenance_service.maintenance_service.Service.RequestService;
import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class RequestServiceImpul implements RequestService {
    private final MaintaincesRepo maintaincesRepo;
    RequestServiceImpul(MaintaincesRepo maintaincesRepo)
    {
        this.maintaincesRepo=maintaincesRepo;
    }
    @Override
    public MaintenanceRequest createRequest(MaintenanceRequest request) {
        return maintaincesRepo.save(request);
    }

    @Override
    public MaintenanceRequest updateRequest(String id,MaintenanceRequest request) {
        if (request.getId() == null || request.getId().isEmpty()) {
            throw new IllegalArgumentException("Request ID is required for update");
        }
        MaintenanceRequest existing = maintaincesRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + request.getId()));
        if (request.getRequestNumber() != null) {
            existing.setRequestNumber(request.getRequestNumber());
        }
        if (request.getTenantId() != null) {
            existing.setTenantId(request.getTenantId());
        }
        if (request.getFlatId() != null) {
            existing.setFlatId(request.getFlatId());
        }
        if (request.getCategory() != null) {
            existing.setCategory(request.getCategory());
        }
        if (request.getTitle() != null) {
            existing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            existing.setPriority(request.getPriority());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getImages() != null) {
            existing.setImages(request.getImages());
        }
        if (request.getAssignedTo() != null) {
            existing.setAssignedTo(request.getAssignedTo());
        }

        if ("RESOLVED".equals(request.getStatus()) || "CLOSED".equals(request.getStatus())) {
            if (existing.getResolvedAt() == null) {
                existing.setResolvedAt(new Date());
            }
        }
        existing.setUpdatedAt(new Date());


        if (request.getComments() != null && !request.getComments().isEmpty()) {
            if (existing.getComments() == null) {
                existing.setComments(new ArrayList<>());
            }
            existing.getComments().addAll(request.getComments());
        }
        return maintaincesRepo.save(existing);
    }

    @Override
    public MaintenanceRequest deleteRequest(String id) {

        MaintenanceRequest existing = maintaincesRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + id));
        maintaincesRepo.delete(existing);
        return existing;
    }

    @Override
    public List<MaintenanceRequest> getAllRequests() {
        return maintaincesRepo.findAll();
    }

    @Override
    public List<MaintenanceRequest> getRequestsByStatus(String status) {
        return maintaincesRepo.findByStatus(status);
    }

    @Override
    public List<MaintenanceRequest> getRequestsByPriority(String priority) {
         return maintaincesRepo.findByPriority(priority);
    }

    @Override
    public List<MaintenanceRequest> getRequestByCategory(String category) {
        return maintaincesRepo.findByCategory(category);
    }

    @Override
    public Optional<MaintenanceRequest> getRequestsById(String requestId) {
        return maintaincesRepo.findById(requestId);
    }

    @Override
    public MaintenanceRequest assignToTechnician(String requestId,MaintenanceRequest request) {
        request.setAssignedTo("123456");
        return maintaincesRepo.findById(requestId).orElseThrow(()->new IllegalArgumentException("No record found with the request id: "+requestId));
    }

    @Override
    public Integer getPendingRequestCount() {
        return (maintaincesRepo.findByStatus("open").size()+maintaincesRepo.findByStatus("in-progress").size());
    }
}
