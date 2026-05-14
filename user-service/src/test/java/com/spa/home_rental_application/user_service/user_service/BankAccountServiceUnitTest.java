package com.spa.home_rental_application.user_service.user_service;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.BankAccountRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.BankAccountResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.BankAccount;
import com.spa.home_rental_application.user_service.user_service.repositry.BankAccountRepo;
import com.spa.home_rental_application.user_service.user_service.service.impul.BankAccountServiceImpul;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-15a, slice 2: service-layer unit tests. No Spring context — pure
 * Mockito so they run in milliseconds.
 *
 * <p>Verifies:
 *  • POSITIVE — masking strips the head of the account number, leaving
 *               only the last 4 digits visible.
 *  • POSITIVE — save() routes through the upsert path (findByUserId →
 *               builder or update in place).
 *  • POSITIVE — successful save emits a bank-account.added audit event.
 *  • POSITIVE — delete() emits a bank-account.removed audit event.
 *  • POSITIVE — delete() on an unknown user is a no-op (no exception,
 *               no audit row — there was nothing to remove).
 *  • POSITIVE — getByUserId returns Optional.empty when nothing on file.
 */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceUnitTest {

    @Mock BankAccountRepo repo;
    @Mock AuditEventPublisher audit;

    private BankAccountServiceImpul service() {
        return new BankAccountServiceImpul(repo, audit);
    }

    private static BankAccountRequestDto body(String acct) {
        return new BankAccountRequestDto(
                "John Doe",
                "State Bank of India",
                acct,
                "SBIN0001234",
                null,
                "SAVINGS",
                null);
    }

    @Test
    @DisplayName("[+] mask helper (covered via service) renders XXXX-grouped tail")
    void mask_via_service_renders_correctly() {
        when(repo.findByUserId(any())).thenReturn(Optional.empty());
        when(repo.save(any(BankAccount.class))).thenAnswer(inv -> {
            BankAccount b = inv.getArgument(0);
            b.setId("any-id");
            return b;
        });

        // 12 digits → 8 X + space + 4 last
        BankAccountResponseDto twelve = service().save("u12", body("123456789012"));
        assertThat(twelve.accountNumberMasked()).isEqualTo("XXXX XXXX 9012");

        // 9 digits → 5 X + 4 last (grouped)
        BankAccountResponseDto nine = service().save("u9", body("987654321"));
        assertThat(nine.accountNumberMasked()).isEqualTo("XXXX X 4321");
    }

    @Test
    @DisplayName("[+] save() upserts a new row when none exists and emits audit event")
    void save_creates_new_row_and_audits() {
        when(repo.findByUserId(eq("user-new"))).thenReturn(Optional.empty());
        // mimic repo.save(builder()) returning the saved entity with an id
        when(repo.save(any(BankAccount.class))).thenAnswer(inv -> {
            BankAccount b = inv.getArgument(0);
            b.setId("generated-id-123");
            return b;
        });

        BankAccountResponseDto saved = service().save("user-new", body("123456789012"));

        assertThat(saved.id()).isEqualTo("generated-id-123");
        assertThat(saved.accountNumberMasked()).endsWith("9012");
        // Audit channel got the bank-account.added event
        verify(audit).publishSuccess(
                eq("bank-account.added"),
                eq("user-new"),
                eq("user-new"),
                eq("generated-id-123"),
                any());
    }

    @Test
    @DisplayName("[+] save() updates in place when row already exists (id preserved)")
    void save_updates_existing_row_and_emits_updated_event() {
        BankAccount existing = BankAccount.builder()
                .id("existing-id-1")
                .userId("user-existing")
                .accountHolderName("Old Holder")
                .bankName("Old Bank")
                .accountNumber("111122223333")
                .ifscCode("OLDB0001111")
                .accountType("SAVINGS")
                .build();
        when(repo.findByUserId(eq("user-existing"))).thenReturn(Optional.of(existing));
        when(repo.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponseDto saved = service().save("user-existing",
                new BankAccountRequestDto("New Holder", "New Bank",
                        "999988887777", "NBNK0009999", null, "CURRENT", null));

        // Id preserved (upsert, not insert)
        assertThat(saved.id()).isEqualTo("existing-id-1");
        // Fields updated
        assertThat(saved.accountHolderName()).isEqualTo("New Holder");
        assertThat(saved.bankName()).isEqualTo("New Bank");
        assertThat(saved.ifscCode()).isEqualTo("NBNK0009999");
        assertThat(saved.accountType()).isEqualTo("CURRENT");
        // Audit event reflects update path
        verify(audit).publishSuccess(eq("bank-account.updated"),
                eq("user-existing"), eq("user-existing"),
                eq("existing-id-1"), any());
    }

    @Test
    @DisplayName("[+] delete() emits bank-account.removed when row found")
    void delete_emits_audit() {
        BankAccount existing = BankAccount.builder()
                .id("to-delete-id")
                .userId("user-deleting")
                .accountHolderName("X")
                .bankName("X")
                .accountNumber("X")
                .ifscCode("XXXX0000000")
                .build();
        when(repo.findByUserId(eq("user-deleting"))).thenReturn(Optional.of(existing));

        service().delete("user-deleting");

        verify(repo).delete(eq(existing));
        verify(audit).publishSuccess(eq("bank-account.removed"),
                eq("user-deleting"), eq("user-deleting"),
                eq("to-delete-id"), any());
    }

    @Test
    @DisplayName("[+] delete() on unknown user is a no-op (idempotent)")
    void delete_on_unknown_user_is_noop() {
        when(repo.findByUserId(eq("user-not-here"))).thenReturn(Optional.empty());

        service().delete("user-not-here");

        verify(repo, never()).delete(any(BankAccount.class));
        verify(audit, never()).publishSuccess(eq("bank-account.removed"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("[+] getByUserId returns Optional.empty when nothing on file")
    void get_unknown_returns_empty() {
        when(repo.findByUserId(eq("absent-user"))).thenReturn(Optional.empty());
        assertThat(service().getByUserId("absent-user")).isEmpty();
    }
}
