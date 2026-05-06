package com.spa.home_rental_application.payment_service.payment_service.DTO.Request;

import com.spa.home_rental_application.payment_service.payment_service.enums.CardNetwork;
import com.spa.home_rental_application.payment_service.payment_service.enums.PaymentMethod;
import com.spa.home_rental_application.payment_service.payment_service.enums.UpiApp;
import com.spa.home_rental_application.payment_service.payment_service.enums.WalletProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /payments/initiate — tenant tells the service which method
 * they want to pay with. The service then talks to the payment gateway and
 * returns whatever the gateway needs the client to do (redirect URL, UPI
 * intent string, deep-link, etc.).
 *
 * <p>Field rules:
 * <ul>
 *   <li>{@code paymentMethod} is required.</li>
 *   <li>If method=UPI, {@code upiApp} should be set; {@code upiVpa} optional
 *       (collect-style flow).</li>
 *   <li>If method=WALLET, {@code walletProvider} should be set.</li>
 *   <li>If method=CARD, {@code cardNetwork} + {@code cardLast4} optional
 *       (the gateway handles the full card collection over its own UI).</li>
 *   <li>If method=BANK_TRANSFER, {@code bankReference} optional.</li>
 * </ul>
 */
public record InitiatePaymentRequest(
        @NotBlank(message = "paymentId is mandatory") String paymentId,

        @NotNull(message = "paymentMethod is mandatory") PaymentMethod paymentMethod,

        UpiApp upiApp,

        @Pattern(regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z]+$",
                 message = "upiVpa must look like name@bank")
        @Size(max = 255)
        String upiVpa,

        WalletProvider walletProvider,

        CardNetwork cardNetwork,

        @Pattern(regexp = "^[0-9]{4}$", message = "cardLast4 must be 4 digits")
        String cardLast4,

        @Size(max = 100) String bankReference,

        // Where the gateway should redirect the user back to after auth
        @Size(max = 1000) String returnUrl
) {}
