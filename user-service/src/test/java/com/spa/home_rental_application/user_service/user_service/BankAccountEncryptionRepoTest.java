package com.spa.home_rental_application.user_service.user_service;

import com.spa.home_rental_application.user_service.user_service.Entities.BankAccount;
import com.spa.home_rental_application.user_service.user_service.repositry.BankAccountRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.spa.home_rental_application.user_service.user_service.config.EncryptedStringConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-15a, slice 1: JPA-layer round-trip for the AES-GCM
 * @Converter applied to bank_accounts.account_number.
 *
 * <p>Uses @DataJpaTest (only the JPA + repo slice loads — no Feign,
 * no controllers, no Eureka) plus an in-memory H2 in Oracle-compat
 * mode for fast boot. We bring the EncryptedStringConverter in
 * manually because @DataJpaTest's default scan misses
 * non-repository / non-entity beans in other packages.
 *
 * <p>Verifies:
 *  • POSITIVE — plaintext written → ciphertext on disk (ENC: sentinel)
 *               → plaintext returned on read.
 *  • POSITIVE — legacy plaintext row (no ENC: sentinel) passes through
 *               unchanged for backward compat.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import(EncryptedStringConverter.class)
@EnableJpaRepositories(basePackageClasses = BankAccountRepo.class)
@EntityScan(basePackageClasses = BankAccount.class)
@AutoConfigurationPackage
class BankAccountEncryptionRepoTest {

    @Configuration
    static class TestEncryptionKeyConfig {
        // EncryptedStringConverter reads app.encryption.key via
        // @Value. test/resources/application.yaml sets a known value;
        // this bean overrides via @Primary if needed. Kept here as a
        // hook for future tuning — currently the key from yaml is
        // sufficient.
        @Bean
        String __testEncryptionKeyAnchor() {
            return "anchor-noop";
        }
    }

    @Autowired BankAccountRepo bankAccountRepo;
    @PersistenceContext EntityManager entityManager;

    @Test
    @DisplayName("[+] write plaintext → AES-GCM ciphertext on disk → plaintext on read")
    void encryption_round_trip_at_jpa_layer() {
        // Persist via JPA so the @Convert kicks in on write
        BankAccount entity = BankAccount.builder()
                .userId("test-user-jpa")
                .accountHolderName("Round Trip Tester")
                .bankName("Test Bank")
                .accountNumber("999988887777")
                .ifscCode("TBNK0001234")
                .accountType("SAVINGS")
                .build();
        bankAccountRepo.save(entity);

        entityManager.flush();
        entityManager.clear();   // force re-read from disk

        // Read back via JPA — converter applies in reverse
        BankAccount fetched = bankAccountRepo.findByUserId("test-user-jpa").orElseThrow();
        assertThat(fetched.getAccountNumber()).isEqualTo("999988887777");

        // Native SELECT bypasses the converter — reveals what's
        // ACTUALLY stored on disk
        Object raw = entityManager
                .createNativeQuery("SELECT account_number FROM bank_accounts WHERE user_id = :uid")
                .setParameter("uid", "test-user-jpa")
                .getSingleResult();
        assertThat(raw).asString().startsWith("ENC:");
        assertThat(raw).asString().doesNotContain("999988887777");
    }

    @Test
    @DisplayName("[+] legacy plaintext row (no ENC: prefix) round-trips unchanged")
    void legacy_plaintext_row_passes_through() {
        // Bypass the converter via a native INSERT — emulates a row
        // persisted before the converter rolled in.
        entityManager.createNativeQuery(
                        "INSERT INTO bank_accounts (id, user_id, account_holder_name, bank_name, " +
                                "account_number, ifsc_code, account_type, created_at, updated_at) " +
                                "VALUES (:id, :uid, :hn, :bn, :acct, :ifsc, :type, " +
                                "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .setParameter("id", "legacy-row-1")
                .setParameter("uid", "legacy-user-jpa")
                .setParameter("hn", "Legacy User")
                .setParameter("bn", "Legacy Bank")
                .setParameter("acct", "555566667777")
                .setParameter("ifsc", "LEGS0001111")
                .setParameter("type", "SAVINGS")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        BankAccount fetched = bankAccountRepo.findByUserId("legacy-user-jpa").orElseThrow();
        // No ENC: prefix on disk → converter returns the bytes verbatim.
        assertThat(fetched.getAccountNumber()).isEqualTo("555566667777");
    }
}
