package com.spa.home_rental_application.kyc_service.DTO.Response;

/**
 * Returned to the frontend after we've created a pending DigiLocker
 * authorization. The frontend should stash {@code state} in
 * sessionStorage and then navigate the user to {@code authorizeUrl}.
 *
 * <p>On callback the frontend re-checks the {@code state} from the URL
 * against the one in sessionStorage as a defence-in-depth measure;
 * our server also validates it against the persisted KYC record before
 * exchanging the code.
 */
public record DigiLockerAuthorizeResponse(
        String authorizeUrl,
        String state,
        String referenceId
) {
}
