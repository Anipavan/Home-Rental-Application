package com.spa.home_rental_application.maintenance_service.maintenance_service.Repository;

import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Category;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Priority;
import com.spa.home_rental_application.maintenance_service.maintenance_service.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MaintenanceRequestRepository extends MongoRepository<MaintenanceRequest, String> {

    Page<MaintenanceRequest> findAll(Pageable pageable);

    List<MaintenanceRequest> findByStatus(Status status);
    long countByStatusIn(Collection<Status> statuses);

    List<MaintenanceRequest> findByPriority(Priority priority);

    List<MaintenanceRequest> findByCategory(Category category);
    long countByCategory(Category category);

    List<MaintenanceRequest> findByTenantId(String tenantId);
    List<MaintenanceRequest> findByOwnerId(String ownerId);

    List<MaintenanceRequest> findByFlatIdAndStatusIn(String flatId, Collection<Status> statuses);
}
