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
 * {@code ref_cities} tables on startup. Idempotent — skipped entirely if
 * {@code ref_states} already has rows.
 *
 * <p>Source data: {@code classpath:reference/india_geo.csv}. IDs are stable
 * so cross-environment exports are predictable.
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
        if (stateRepo.count() > 0) {
            log.debug("ref_states already has {} rows — skipping seed", stateRepo.count());
            return;
        }

        try {
            ParsedData data = parseCsv();
            stateRepo.saveAll(data.states);
            cityRepo.saveAll(data.cities);
            log.info("Seeded reference data: {} states + {} cities",
                    data.states.size(), data.cities.size());
        } catch (IOException e) {
            log.error("Failed to seed reference data from {} — dropdowns will be empty",
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
