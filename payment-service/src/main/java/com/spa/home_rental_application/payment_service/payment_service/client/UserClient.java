package com.spa.home_rental_application.payment_service.payment_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client used by payment-service to resolve the owner's
 * payout details (UPI VPA, masked bank account, IFSC) when a tenant
 * is about to pay rent.
 *
 * <p>Failures are absorbed by {@link UserClientFallback} so a
 * user-service outage degrades gracefully — the FE shows a
 * "couldn't load payment details, try again in a moment" toast
 * rather than a 500.
 */
@FeignClient(name = "HRA-user-service", fallback = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/users/bank-accounts/payout/{userId}")
    PayoutDetails getPayoutDetails(@PathVariable("userId") String userId);

    /**
     * Resolve an auth-user-id to the user's profile (first + last name)
     * so the receipt PDF can print "Name: Pavan Anirudh" instead of
     * "Tenant ID: 25a2d158-c987-4220-92ab-9185e215807c". On fallback
     * we return an empty profile so the PDF generator falls back to
     * the raw id — never blocks receipt generation.
     */
    @GetMapping("/users/auth/{authUserId}")
    UserProfileSummary getByAuthUserId(@PathVariable("authUserId") String authUserId);

    /**
     * Payable subset of a user's bank account — local mirror of
     * user-service's BankAccountPayoutDto so payment-service doesn't
     * have to depend on user-service code. Field-by-field identical
     * (Jackson dropping any extras the wire might add later).
     */
    record PayoutDetails(
            String accountHolderName,
            String bankName,
            String accountNumberMasked,
            String ifscCode,
            String branch,
            String accountType,
            String upiId
    ) {
        public static PayoutDetails empty() {
            return new PayoutDetails(null, null, null, null, null, null, null);
        }
    }

    /**
     * Tiny subset of user-service's {@code UserResponseDto} — just the
     * fields the receipt PDF actually prints. Jackson silently drops
     * everything else, keeping the contract loose so user-service can
     * add fields without breaking payment-service deserialisation.
     */
    record UserProfileSummary(
            String id,
            String authUserId,
            String firstName,
            String lastName,
            String email
    ) {
        public static UserProfileSummary empty() {
            return new UserProfileSummary(null, null, null, null, null);
        }

        /**
         * "First Last" joined, blanks collapsed. Returns null when
         * neither name is set so callers can fall back to a different
         * identifier (auth id, email, etc.).
         */
        public String displayName() {
            String fn = firstName == null ? "" : firstName.trim();
            String ln = lastName == null ? "" : lastName.trim();
            String full = (fn + " " + ln).trim();
            return full.isEmpty() ? null : full;
        }
    }
}
