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
}
