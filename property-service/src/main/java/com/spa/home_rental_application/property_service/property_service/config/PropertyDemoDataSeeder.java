package com.spa.home_rental_application.property_service.property_service.config;

import com.spa.home_rental_application.property_service.property_service.Entities.Building;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds 3 demo buildings + 12 flats on first boot. Idempotent -- skips
 * everything if a building with id BLD-DEMO-001 already exists.
 *
 * The {@code ownerId} values here MUST match what auth-service generates
 * for the seeded users. The auth seeder inserts users in fixed order on
 * a fresh database, producing predictable Long IDs:
 *   admin=1, owner_alice=2, owner_bob=3, tenant_chris=4, ...
 *
 * If your auth DB had pre-existing data, those IDs will shift. Override
 * the owner IDs via env vars:
 *   DEMO_OWNER_ALICE_ID, DEMO_OWNER_BOB_ID
 *   DEMO_TENANT_CHRIS_ID, DEMO_TENANT_DANA_ID, DEMO_TENANT_ELI_ID,
 *   DEMO_TENANT_FRAN_ID, DEMO_TENANT_GABE_ID
 *
 * Disable entirely with -Dapp.demo-seed.enabled=false.
 *
 * <p>Double-gated for prod safety: active only under dev/local/test
 * profiles AND when {@code app.demo-seed.enabled=true} (default true in
 * dev). A prod deployment with {@code SPRING_PROFILES_ACTIVE=prod}
 * cannot accidentally seed demo data.
 */
@Component
@Profile({"dev", "local", "test", "default"})
@ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class PropertyDemoDataSeeder implements CommandLineRunner {

    private final BuildingRepo buildings;
    private final FlatRepo flats;

    public PropertyDemoDataSeeder(BuildingRepo buildings, FlatRepo flats) {
        this.buildings = buildings;
        this.flats = flats;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (System.getProperty("app.demo-seed.enabled", "true").equalsIgnoreCase("false")
                || "false".equalsIgnoreCase(System.getenv("APP_DEMO_SEED_ENABLED"))) {
            log.info("PropertyDemoDataSeeder: disabled");
            return;
        }

        // Idempotent guard -- if our marker building already exists, don't reseed.
        if (buildings.findById("BLD-DEMO-001").isPresent()) {
            log.info("PropertyDemoDataSeeder: BLD-DEMO-001 already present; skipping");
            return;
        }

        // Owner IDs match the auth-service seeded users on a fresh DB.
        // Override via env if your auth DB starts at a different sequence.
        String aliceId = env("DEMO_OWNER_ALICE_ID", "2");
        String bobId   = env("DEMO_OWNER_BOB_ID",   "3");
        String chrisId = env("DEMO_TENANT_CHRIS_ID", "4");
        String danaId  = env("DEMO_TENANT_DANA_ID",  "5");
        String eliId   = env("DEMO_TENANT_ELI_ID",   "6");
        String franId  = env("DEMO_TENANT_FRAN_ID",  "7");
        String gabeId  = env("DEMO_TENANT_GABE_ID",  "8");

        log.info("PropertyDemoDataSeeder: seeding 3 buildings + 12 flats");
        log.info("  Owners used: alice={} bob={}", aliceId, bobId);

        String now = LocalDateTime.now().toString();

        Building b1 = save(Building.builder()
                .buildingId("BLD-DEMO-001")
                .buildingName("Sunrise Residency")
                .ownerId(aliceId)
                .buildingAddress("12 MG Road, Sector 9")
                .buildingCity("Bangalore")
                .buildingState("Karnataka")
                .buildingTotalFloors("8")
                .buildingTotalFlats("4")
                .amenities("Gym, Swimming Pool, Parking, Security, Garden")
                .createdDt(now).updatedDt(now)
                .isDeleted(false)
                .build());

        Building b2 = save(Building.builder()
                .buildingId("BLD-DEMO-002")
                .buildingName("Maple Heights")
                .ownerId(aliceId)
                .buildingAddress("78 Brigade Road")
                .buildingCity("Bangalore")
                .buildingState("Karnataka")
                .buildingTotalFloors("12")
                .buildingTotalFlats("4")
                .amenities("Gym, Concierge, Parking, Power Backup, EV Charging")
                .createdDt(now).updatedDt(now)
                .isDeleted(false)
                .build());

        Building b3 = save(Building.builder()
                .buildingId("BLD-DEMO-003")
                .buildingName("Lakeview Towers")
                .ownerId(bobId)
                .buildingAddress("5 Lake Side Drive")
                .buildingCity("Mumbai")
                .buildingState("Maharashtra")
                .buildingTotalFloors("15")
                .buildingTotalFlats("4")
                .amenities("Pool, Gym, Clubhouse, Parking, Garden, Children Play Area")
                .createdDt(now).updatedDt(now)
                .isDeleted(false)
                .build());

        // Sunrise: 4 flats, 2 occupied (chris, dana)
        seedFlats(b1.getBuildingId(), List.of(
                new FlatSpec("A-101", 1, 2, 2, 1100.0, 22000, false, null),
                new FlatSpec("A-102", 1, 3, 2, 1450.0, 32000, true,  chrisId),
                new FlatSpec("B-201", 2, 2, 2, 1100.0, 24000, true,  danaId),
                new FlatSpec("B-202", 2, 1, 1,  650.0, 16000, false, null)
        ));

        // Maple Heights: all vacant
        seedFlats(b2.getBuildingId(), List.of(
                new FlatSpec("M-301", 3, 3, 3, 1850.0, 45000, false, null),
                new FlatSpec("M-302", 3, 2, 2, 1200.0, 30000, false, null),
                new FlatSpec("M-401", 4, 3, 2, 1500.0, 36000, false, null),
                new FlatSpec("M-501", 5, 4, 3, 2400.0, 60000, false, null)
        ));

        // Lakeview: 3 occupied (eli, fran, gabe), 1 vacant
        seedFlats(b3.getBuildingId(), List.of(
                new FlatSpec("L-101", 1, 2, 2, 1300.0, 38000, true,  eliId),
                new FlatSpec("L-202", 2, 3, 3, 1700.0, 52000, true,  franId),
                new FlatSpec("L-305", 3, 3, 2, 1500.0, 46000, true,  gabeId),
                new FlatSpec("L-410", 4, 4, 4, 2100.0, 75000, false, null)
        ));

        log.info("PropertyDemoDataSeeder: done. {} buildings, {} flats",
                buildings.count(), flats.count());
    }

    private Building save(Building b) {
        Building saved = buildings.save(b);
        log.info("  + Building {} ({}) ownerId={}",
                saved.getBuildingId(), saved.getBuildingName(), saved.getOwnerId());
        return saved;
    }

    private void seedFlats(String buildingId, List<FlatSpec> specs) {
        LocalDateTime now = LocalDateTime.now();
        int n = 1;
        for (FlatSpec s : specs) {
            String fid = "FLT-DEMO-" + buildingId.substring(buildingId.length() - 3) + "-" + n++;
            Flat f = Flat.builder()
                    .id(fid)
                    .buildingId(buildingId)
                    .flatNumber(s.flatNumber)
                    .floor(s.floor)
                    .bedrooms(s.bedrooms)
                    .bathrooms(s.bathrooms)
                    .areaSqft(s.area)
                    .rentAmount(BigDecimal.valueOf(s.rent))
                    .isOccupied(s.occupied)
                    .isDeleted(false)
                    .tenantId(s.tenantId)
                    .leaseStartDate(s.occupied ? LocalDate.now().minusMonths(3) : null)
                    .leaseEndDate(s.occupied ? LocalDate.now().plusMonths(9) : null)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            flats.save(f);
            log.info("    + Flat {} ({}br/{}ba, Rs.{}/mo) {}",
                    fid, s.bedrooms, s.bathrooms, s.rent,
                    s.occupied ? "occupied by tenantId=" + s.tenantId : "vacant");
        }
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private record FlatSpec(
            String flatNumber, int floor, int bedrooms, int bathrooms,
            double area, int rent, boolean occupied, String tenantId) {}
}
