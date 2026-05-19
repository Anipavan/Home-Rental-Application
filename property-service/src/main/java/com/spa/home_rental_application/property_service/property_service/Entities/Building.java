    package com.spa.home_rental_application.property_service.property_service.Entities;

    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.Id;
    import jakarta.persistence.Table;
    import lombok.*;

    @Entity
    @Table(name = "registered_buildings")
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public  class Building {
        @Id
        String buildingId;
        String buildingName;
        String ownerId;
        String buildingAddress;
        String buildingCity;
        String buildingState;
        String buildingTotalFloors;
        String buildingTotalFlats;
        String amenities;
        String createdDt;
        String updatedDt;

        /**
         * FK to {@code ref_states.id}. Optional — old buildings have only the
         * {@code buildingState} string. New buildings (created via the
         * cascading dropdown) carry both string + id so analytics / search
         * can use either interchangeably.
         */
        @Column(name = "state_id")
        private Long stateId;

        /** FK to {@code ref_cities.id}. Optional — see {@link #stateId}. */
        @Column(name = "city_id")
        private Long cityId;

        /**
         * Geographic coordinates — power the future map view + the
         * {@code GET /flats/near?lat=&lng=&radiusKm=} Haversine-distance
         * filter. Nullable so legacy rows / owners who don't know their
         * coordinates still create buildings; the geosearch endpoint
         * simply excludes them.
         *
         * <p>Owners pick a point on the map at create-time; reverse-
         * geocoding the {@code buildingAddress} via a one-shot
         * geocoding call (Nominatim / OpenCage / Mapbox) is a planned
         * follow-up to back-fill legacy rows.
         */
        @Column(name = "latitude")
        private Double latitude;

        @Column(name = "longitude")
        private Double longitude;

        @Column(name = "is_deleted", nullable = false)
        @Builder.Default
        private Boolean isDeleted = false;

        /**
         * Free-text "What's included" list — separate from {@link #amenities}
         * so the public detail page can render them as two distinct sections.
         * Amenities are building-level perks (lift, pool, gym); included
         * items are flat-level fittings the tenant gets out-of-the-box
         * (modular kitchen, wardrobes, RO water purifier, AC, etc.).
         *
         * <p>Stored as comma- or newline-separated free text. Nullable —
         * legacy buildings without this field render no "What's included"
         * section at all (the frontend hides empty sections rather than
         * showing a sad-default list).
         *
         * <p>Length 4000 mirrors the {@code address} column on the user
         * table — same Oracle VARCHAR2 standard-mode max, sidesteps the
         * Hibernate-doesn't-widen-to-CLOB-on-update gotcha that bit the
         * profile picture URL column earlier.
         */
        @Column(name = "included_items", length = 4000)
        private String includedItems;

        @Override
        public String toString() {
            return "Building{" +
                    "buildingId='" + buildingId + '\'' +
                    ", buildingName='" + buildingName + '\'' +
                    ", ownerId='" + ownerId + '\'' +
                    ", buildingAddress='" + buildingAddress + '\'' +
                    ", buildingCity='" + buildingCity + '\'' +
                    ", buildingState='" + buildingState + '\'' +
                    ", buildingTotalFloors='" + buildingTotalFloors + '\'' +
                    ", buildingTotalFlats='" + buildingTotalFlats + '\'' +
                    ", amenities='" + amenities + '\'' +
                    ", createdDt='" + createdDt + '\'' +
                    ", updatedDt='" + updatedDt + '\'' +
                    ", isDeleted=" + isDeleted +
                    '}';
        }
    }
