package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Reference data: city/town within a state.
 *
 * <p>Seeded once by {@code ReferenceDataSeeder} from a fixed list of major
 * Indian cities. Owners are not allowed to add new cities through the UI —
 * the search endpoint surfaces a "use the closest match" UX instead.
 */
@Entity
@Table(name = "ref_cities",
        uniqueConstraints = @UniqueConstraint(name = "uk_ref_city_state_name",
                columnNames = {"state_id", "name"}),
        indexes = {
                @Index(name = "idx_ref_cities_state", columnList = "state_id"),
                @Index(name = "idx_ref_cities_name",  columnList = "name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefCity {

    @Id
    private Long id;

    @Column(name = "state_id", nullable = false)
    private Long stateId;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Indian government tier classification (1 / 2 / 3) — useful for the AI
     * rent optimizer in the future. Null when unknown.
     */
    @Column
    private Short tier;
}
