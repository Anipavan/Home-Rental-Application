package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "propertyimages")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PropertyImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "property_id", nullable = false)
    private String propertyId;   // can be buildingId or flatId based on your design

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "type")
    private String type; // e.g. "BUILDING", "FLAT", "THUMBNAIL", etc.
}
