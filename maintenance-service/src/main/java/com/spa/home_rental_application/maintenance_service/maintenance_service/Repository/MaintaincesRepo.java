package com.spa.home_rental_application.maintenance_service.maintenance_service.Repository;

import com.spa.home_rental_application.maintenance_service.maintenance_service.entities.MaintenanceRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintaincesRepo extends MongoRepository<MaintenanceRequest,String> {
    List<MaintenanceRequest> findByStatus(String status);
    List<MaintenanceRequest> findByPriority(String priority);
    List<MaintenanceRequest> findByCategory(String category);
}
