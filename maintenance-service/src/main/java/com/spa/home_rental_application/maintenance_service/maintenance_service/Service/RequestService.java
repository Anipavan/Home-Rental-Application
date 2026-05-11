package com.spa.home_rental_application.maintenance_service.maintenance_service.Service;

import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Request.*;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.CategoryStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.MaintenanceRequestResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.DTO.Response.ResolutionTimeStatsResponse;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Kind;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface RequestService {

    /* Lifecycle */
    MaintenanceRequestResponse createRequest(CreateRequestDto dto);
    MaintenanceRequestResponse updateRequest(String id, UpdateRequestDto dto);
    void                       deleteRequest(String id);
    MaintenanceRequestResponse getRequestById(String id);
    Page<MaintenanceRequestResponse> getAllRequests(Pageable pageable);

    /* Lookups */
    List<MaintenanceRequestResponse> getRequestsByStatus(Status status);
    List<MaintenanceRequestResponse> getRequestsByPriority(Priority priority);
    List<MaintenanceRequestResponse> getRequestsByCategory(Category category);
    List<MaintenanceRequestResponse> getRequestsByTenant(String tenantId);
    List<MaintenanceRequestResponse> getRequestsByOwner(String ownerId);

    /* Kind-scoped lookups — power the complaints feature without
       duplicating the storage/state-machine. */
    Page<MaintenanceRequestResponse> getAllByKind(Kind kind, Pageable pageable);
    List<MaintenanceRequestResponse> getByTenantAndKind(String tenantId, Kind kind);
    List<MaintenanceRequestResponse> getByOwnerAndKind(String ownerId, Kind kind);

    /* Actions */
    MaintenanceRequestResponse assignTechnician(String id, AssignTechnicianRequest body);
    MaintenanceRequestResponse addComment(String id, AddCommentRequest body);
    MaintenanceRequestResponse changeStatus(String id, StatusChangeRequest body);
    MaintenanceRequestResponse uploadImage(String id, MultipartFile file) throws IOException;
    List<MaintenanceRequestResponse.HistoryEntryResponse> getHistory(String id);

    /* Analytics */
    long getPendingRequestCount();
    long getPendingCountByKind(Kind kind);
    List<CategoryStatsResponse> getCategoryStats();
    ResolutionTimeStatsResponse getResolutionTimeStats();

    /* Cross-service consumer */
    void onFlatVacated(String flatId, String tenantId);
}
