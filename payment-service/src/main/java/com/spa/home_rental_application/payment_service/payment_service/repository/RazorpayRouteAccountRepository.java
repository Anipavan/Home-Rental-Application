package com.spa.home_rental_application.payment_service.payment_service.repository;

import com.spa.home_rental_application.payment_service.payment_service.entities.RazorpayRouteAccount;
import com.spa.home_rental_application.payment_service.payment_service.enums.RouteAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RazorpayRouteAccountRepository
        extends JpaRepository<RazorpayRouteAccount, String> {

    /** The one row for this owner, or empty if they've never opened
     *  the KYC form. Used by:
     *   • the KYC page (to show current status),
     *   • the split-payment code (to look up the linked_account_id
     *     when building the transfers[] array),
     *   • the flat-listing gate (to block publishing until ACTIVATED). */
    Optional<RazorpayRouteAccount> findByUserId(String userId);

    /** Look up by Razorpay's account id — drives the
     *  {@code account.updated} webhook handler which arrives with
     *  {@code account_id} but no local id. */
    Optional<RazorpayRouteAccount> findByRazorpayAccountId(String razorpayAccountId);

    /** All accounts stuck in a non-terminal state, ordered by how
     *  long they've been that way. Powers an admin dashboard
     *  ("who's waiting on activation, and for how long?") + drives
     *  a nudge cron ("email owners who submitted 3+ days ago and
     *  are still pending"). */
    List<RazorpayRouteAccount> findByStatusInOrderByKycSubmittedAtAsc(
            List<RouteAccountStatus> statuses);
}
