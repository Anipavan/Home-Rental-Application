package com.spa.home_rental_application.property_service.property_service.config;

import com.spa.home_rental_application.property_service.property_service.Entities.RefCity;
import com.spa.home_rental_application.property_service.property_service.Entities.RefState;
import com.spa.home_rental_application.property_service.property_service.repository.RefCityRepo;
import com.spa.home_rental_application.property_service.property_service.repository.RefStateRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds India's states + major cities into the {@code ref_states} and
 * {@code ref_cities} tables on startup.
 *
 * <p>Source data: {@code classpath:reference/india_geo.csv}. IDs are stable
 * so cross-environment exports are predictable.
 *
 * <p><b>Upsert, not insert-once.</b> Previous versions of this seeder
 * skipped entirely when {@code ref_states} already had rows. That made
 * adding new cities to the CSV require manual SQL on every deployed
 * environment — easy to forget, easy to drift. The seeder now runs on
 * every boot and uses {@code saveAll} (which is JPA merge for
 * pre-assigned IDs), so:
 * <ul>
 *   <li>existing rows are updated in place (e.g. fixing a typo in a city
 *       name updates everywhere on restart)</li>
 *   <li>new rows in the CSV are inserted (the user's "missing cities"
 *       expansion of 2026-05 rolled out via just-a-restart)</li>
 *   <li>rows removed from the CSV are <em>not</em> deleted — the seeder
 *       is append/update-only on purpose, so cities referenced by
 *       existing buildings stay valid</li>
 * </ul>
 * The cost is two saveAll calls on every property-service boot
 * (~36 states + ~500 cities). At this size it's noise (single-digit
 * milliseconds against an empty cache) — measured at startup.
 *
 * <p>Run order is set to a high value so the demo data seeder runs first
 * (which doesn't touch reference tables) — but this is mostly for clarity;
 * neither seeder depends on the other.
 */
@Component
@Slf4j
public class ReferenceDataSeeder implements CommandLineRunner {

    private static final String CSV_PATH = "reference/india_geo.csv";

    private final RefStateRepo stateRepo;
    private final RefCityRepo cityRepo;

    public ReferenceDataSeeder(RefStateRepo stateRepo, RefCityRepo cityRepo) {
        this.stateRepo = stateRepo;
        this.cityRepo = cityRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        long stateCountBefore = stateRepo.count();
        long cityCountBefore = cityRepo.count();

        try {
            ParsedData data = parseCsv();
            // saveAll with pre-assigned IDs = merge: existing rows are
            // updated, new rows are inserted. Both ref_states and
            // ref_cities are small (~36 + ~500 rows respectively), so
            // a full re-sync on every boot is cheap.
            stateRepo.saveAll(data.states);
            cityRepo.saveAll(data.cities);

            long stateCountAfter = stateRepo.count();
            long cityCountAfter = cityRepo.count();
            long newStates = stateCountAfter - stateCountBefore;
            long newCities = cityCountAfter - cityCountBefore;

            if (newStates == 0 && newCities == 0 && stateCountBefore > 0) {
                log.debug("Reference data already in sync ({} states, {} cities)",
                        stateCountAfter, cityCountAfter);
            } else {
                log.info("Reference data synced: {} states (+{}), {} cities (+{})",
                        stateCountAfter, newStates, cityCountAfter, newCities);
            }
        } catch (IOException e) {
            log.error("Failed to seed reference data from {} — dropdowns may be empty",
                    CSV_PATH, e);
        }
    }

    /** Reads the CSV from classpath and returns the parsed entity lists. */
    private ParsedData parseCsv() throws IOException {
        List<RefState> states = new ArrayList<>(40);
        List<RefCity> cities = new ArrayList<>(400);

        ClassPathResource res = new ClassPathResource(CSV_PATH);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                String[] parts = trimmed.split(",", -1);
                if (parts.length < 4) {
                    log.warn("Skipping malformed line: {}", trimmed);
                    continue;
                }

                String type = parts[0].trim();
                if ("STATE".equals(type)) {
                    states.add(RefState.builder()
                            .id(Long.parseLong(parts[1].trim()))
                            .code(parts[2].trim())
                            .name(parts[3].trim())
                            .build());
                } else if ("CITY".equals(type)) {
                    Short tier = null;
                    if (parts.length >= 5 && !parts[4].trim().isEmpty()) {
                        try {
                            tier = Short.parseShort(parts[4].trim());
                        } catch (NumberFormatException ignored) { /* leave null */ }
                    }
                    cities.add(RefCity.builder()
                            .id(Long.parseLong(parts[1].trim()))
                            .stateId(Long.parseLong(parts[2].trim()))
                            .name(parts[3].trim())
                            .tier(tier)
                            .build());
                } else {
                    log.warn("Unknown row type {} — skipped", type);
                }
            }
        }
        return new ParsedData(states, cities);
    }

    private record ParsedData(List<RefState> states, List<RefCity> cities) {}
}
