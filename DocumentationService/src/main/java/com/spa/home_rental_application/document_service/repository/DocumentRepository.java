package com.spa.home_rental_application.document_service.repository;

import com.spa.home_rental_application.document_service.Entities.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, String> {

    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.isDeleted = false")
    Optional<Document> findActiveById(@Param("id") String id);

    @Query("SELECT d FROM Document d WHERE d.userId = :userId AND d.isDeleted = false ORDER BY d.uploadedAt DESC")
    List<Document> findActiveByUserId(@Param("userId") String userId);

    @Query("SELECT d FROM Document d WHERE d.userId = :userId AND d.documentType = :type AND d.isDeleted = false")
    List<Document> findActiveByUserIdAndType(@Param("userId") String userId,
                                             @Param("type") String type);
}
