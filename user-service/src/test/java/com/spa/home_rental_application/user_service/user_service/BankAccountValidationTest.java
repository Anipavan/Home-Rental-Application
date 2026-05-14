package com.spa.home_rental_application.user_service.user_service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.BankAccountRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-15a, slice 3: jakarta-validation rules on
 * BankAccountRequestDto. Pure unit-level — boots a single
 * ValidatorFactory in BeforeAll, no Spring context anywhere.
 *
 * <p>Verifies:
 *  • POSITIVE — a well-formed body produces no violations.
 *  • NEGATIVE — missing accountHolderName / bankName trigger @NotBlank.
 *  • NEGATIVE — account number outside 9-18 digits triggers @Pattern.
 *  • NEGATIVE — non-digit account number triggers @Pattern.
 *  • NEGATIVE — bad IFSC (length / shape) triggers @Pattern.
 */
class BankAccountValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static BankAccountRequestDto build(String holder, String bank, String acct, String ifsc) {
        return new BankAccountRequestDto(holder, bank, acct, ifsc, null, "SAVINGS", null);
    }

    private static boolean hasViolationOn(Set<ConstraintViolation<BankAccountRequestDto>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    @Test
    @DisplayName("[+] well-formed body produces zero violations")
    void valid_body_passes() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("John Doe", "SBI", "123456789012", "SBIN0001234"));
        assertThat(v).isEmpty();
    }

    @Test
    @DisplayName("[-] blank accountHolderName triggers @NotBlank")
    void blank_holder_rejected() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("", "SBI", "123456789012", "SBIN0001234"));
        assertThat(hasViolationOn(v, "accountHolderName")).isTrue();
    }

    @Test
    @DisplayName("[-] blank bankName triggers @NotBlank")
    void blank_bank_rejected() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("Holder", " ", "123456789012", "SBIN0001234"));
        assertThat(hasViolationOn(v, "bankName")).isTrue();
    }

    @Test
    @DisplayName("[-] 8-digit account number (below floor of 9) triggers @Pattern")
    void short_account_rejected() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("Holder", "SBI", "12345678", "SBIN0001234"));
        assertThat(hasViolationOn(v, "accountNumber")).isTrue();
    }

    @Test
    @DisplayName("[-] 19-digit account number (above ceiling of 18) triggers @Pattern")
    void long_account_rejected() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("Holder", "SBI", "1234567890123456789", "SBIN0001234"));
        assertThat(hasViolationOn(v, "accountNumber")).isTrue();
    }

    @Test
    @DisplayName("[-] non-digit account number triggers @Pattern")
    void non_digit_account_rejected() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("Holder", "SBI", "12345-67890", "SBIN0001234"));
        assertThat(hasViolationOn(v, "accountNumber")).isTrue();
    }

    @Test
    @DisplayName("[-] IFSC with wrong shape triggers @Pattern")
    void bad_ifsc_rejected() {
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("Holder", "SBI", "123456789012", "BAD-IFSC"));
        assertThat(hasViolationOn(v, "ifscCode")).isTrue();
    }

    @Test
    @DisplayName("[-] IFSC without the digit at position 5 triggers @Pattern")
    void ifsc_missing_zero_at_pos5_rejected() {
        // Real IFSC format: 4 letters + '0' + 6 alphanumeric. Anything
        // else at position 5 is invalid.
        Set<ConstraintViolation<BankAccountRequestDto>> v = validator.validate(
                build("Holder", "SBI", "123456789012", "SBIN90001234"));   // 12 chars + no '0' at pos 5
        assertThat(hasViolationOn(v, "ifscCode")).isTrue();
    }
}
