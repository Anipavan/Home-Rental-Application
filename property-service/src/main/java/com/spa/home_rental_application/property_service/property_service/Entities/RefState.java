package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Reference data: Indian state / union territory.
 *
 * <p>Seeded by {@code ReferenceDataSeeder} on first boot. Drives the
 * cascading state→city dropdown on the Add Building form (and any other
 * place we need a canonical state list).
 *
 * <p>{@code code} is the ISO-3166-2 sub-division code without the country
 * prefix (e.g. "KA" for Karnataka, "MH" for Maharashtra). Stable across
 * environments — useful for cross-service joins.
 */
@Entity
@Table(name = "ref_states", indexes = {
        @Index(name = "idx_ref_states_code", columnList = "code", unique = true),
        @Index(name = "idx_ref_states_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefState {

    @Id
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;
}
