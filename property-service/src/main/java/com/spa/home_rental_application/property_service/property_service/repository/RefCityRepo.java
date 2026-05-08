package com.spa.home_rental_application.property_service.property_service.repository;

import com.spa.home_rental_application.property_service.property_service.Entities.RefCity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefCityRepo extends JpaRepository<RefCity, Long> {

    List<RefCity> findByStateIdOrderByNameAsc(Long stateId);

    /**
     * Free-text auto-suggest. Returns up to {@code limit} cities whose
     * name starts with or contains the query, alphabetically ordered.
     * Limit applied at JPQL level — we never want unbounded results
     * shipped to the dropdown.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c FROM RefCity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY CASE WHEN LOWER(c.name) LIKE LOWER(CONCAT(:q, '%')) THEN 0 ELSE 1 END, c.name ASC")
    List<RefCity> searchByName(@org.springframework.data.repository.query.Param("q") String q,
                               org.springframework.data.domain.Pageable pageable);
}
