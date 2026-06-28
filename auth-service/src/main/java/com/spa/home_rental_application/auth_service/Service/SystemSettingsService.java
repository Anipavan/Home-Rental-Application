package com.spa.home_rental_application.auth_service.Service;

import com.spa.home_rental_application.auth_service.Dto.Response.MaintainerPaymentStatusResponse;
import com.spa.home_rental_application.auth_service.Dto.Response.SystemSettingResponse;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;

import java.util.List;

/**
 * Reads + writes for the {@code system_settings} table plus the
 * derived state-machine logic for the maintainer-payment gate.
 *
 * <p>Layered with a short cache so per-request status checks (every
 * maintainer-dashboard page load) don't go to the DB. The cache
 * invalidates whenever an admin writes a new value, so changes
 * propagate within a cache TTL at most.
 */
public interface SystemSettingsService {

    /** Cached read of the {@code maintainer_payment_enabled} toggle. */
    boolean isMaintainerPaymentEnabled();

    /**
     * Persist a new value for {@code maintainer_payment_enabled} and
     * invalidate the cache. {@code adminUserId} is recorded on the
     * row for audit.
     */
    void setMaintainerPaymentEnabled(boolean enabled, Long adminUserId);

    /** Cached read of the {@code email_verification_required} toggle. */
    boolean isEmailVerificationRequired();

    /**
     * Persist a new value for {@code email_verification_required} and
     * invalidate the cache. {@code adminUserId} is recorded on the
     * row for audit.
     */
    void setEmailVerificationRequired(boolean required, Long adminUserId);

    /** Admin /admin/settings list — every toggle as a flat response. */
    List<SystemSettingResponse> listAll();

    /**
     * Run the maintainer-payment state machine for the given user.
     * See {@link MaintainerPaymentStatusResponse} for the shape and
     * {@code SystemSettingsServiceImpl.computeStatus} for the rules.
     */
    MaintainerPaymentStatusResponse computeStatus(UserDetails user);
}
