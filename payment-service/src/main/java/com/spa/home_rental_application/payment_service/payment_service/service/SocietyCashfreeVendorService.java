package com.spa.home_rental_application.payment_service.payment_service.service;

import com.spa.home_rental_application.payment_service.payment_service.entities.SocietyCashfreeVendor;

import java.util.List;
import java.util.Optional;

/**
 * Society-scoped sibling of {@code CashfreeVendorService}. Manages
 * the Cashfree Easy Split vendor lifecycle for a SOCIETY (per building)
 * so tenant maintenance money settles to the society's own bank
 * account rather than the maintainer user's personal one.
 */
public interface SocietyCashfreeVendorService {

    /**
     * Called from the Kafka listener that consumes
     * {@code society.bank-account.saved}. Looks up (or creates) the
     * per-society vendor row, Feigns the current bank + KYC dump from
     * property-service, and either registers a fresh Cashfree vendor
     * or PATCHes the existing one with the new bank. Idempotent.
     */
    Optional<SocietyCashfreeVendor> tryRegisterIfReadyForSociety(String buildingId);

    /** Point lookup by building. */
    Optional<SocietyCashfreeVendor> getForBuilding(String buildingId);

    /** Admin re-register (mirrors the per-user version). */
    Optional<SocietyCashfreeVendor> reRegister(String buildingId);

    /** Admin listing for the vendor-status dashboard. */
    List<SocietyCashfreeVendor> listAll();
}
