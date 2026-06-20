package com.spa.home_rental_application.payment_service.payment_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Feign client used to resolve {flat → building → owner} on the
 * payment-service side. The {@code flat.occupied} Kafka event payload
 * does not carry an {@code ownerId} field, so when we auto-create the
 * first rent invoice from {@code onFlatOccupied} we have no way to
 * stamp the new {@link com.spa.home_rental_application.payment_service.payment_service.entities.Payment}
 * row with its owner without going back to property-service.
 *
 * <p>Two endpoints are surfaced:
 * <ul>
 *   <li>{@link #getFlatById(String)} — used at write time inside
 *       {@code onFlatOccupied} to discover the building, which then
 *       feeds into {@link #getBuildingById(String)} for the
 *       {@code ownerId}.</li>
 *   <li>{@link #getFlatsByBuilding(String)} +
 *       {@link #getBuildingsByOwner(String)} — used at read time
 *       inside {@code getPaymentsByOwner} to back-fill the legacy
 *       null-ownerId rows that pre-dated this fix. Without the
 *       back-fill, the owner's view stays at ₹0 even after the
 *       write-time fix lands.</li>
 * </ul>
 *
 * <p>All failures are absorbed by {@link PropertyClientFallback} so a
 * property-service outage doesn't take down the rent-invoice creation
 * path — the row just gets created with {@code ownerId=null} and the
 * read-time back-fill will heal it on the next query.
 */
@FeignClient(name = "HRA-property-service", fallback = PropertyClientFallback.class)
public interface PropertyClient {

    @GetMapping("/properties/buildings/{buildingId}")
    BuildingSummary getBuildingById(@PathVariable("buildingId") String buildingId);

    @GetMapping("/properties/buildings/owner/{ownerId}")
    List<BuildingSummary> getBuildingsByOwner(@PathVariable("ownerId") String ownerId);

    @GetMapping("/properties/flats/{flatId}")
    FlatSummary getFlatById(@PathVariable("flatId") String flatId);

    @GetMapping("/properties/flats/building/{buildingId}")
    List<FlatSummary> getFlatsByBuilding(@PathVariable("buildingId") String buildingId);

    /**
     * Drives the maintenance-receipt PDF — payment-service calls this
     * for every SOCIETY_CHARGE Payment to itemise the line-item table
     * (Water bill ₹X, Maintenance ₹Y, Common-area share ₹Z) instead
     * of showing one lumped "Society charge" total. Fallback returns
     * an empty list, in which case the PDF generator collapses to a
     * single "Society charges" line — safe degradation.
     */
    @GetMapping("/society/charges/by-payment/{paymentId}")
    List<SocietyChargeLine> getSocietyChargesByPayment(
            @PathVariable("paymentId") String paymentId);

    /**
     * Subset of property-service {@code BuildingResponseDTO} — only the
     * fields the payment-service consumes. Extra fields on the wire
     * are silently dropped by Jackson, keeping the contract loose.
     */
    record BuildingSummary(
            String buildingId,
            String ownerId
    ) {
        public static BuildingSummary empty() {
            return new BuildingSummary(null, null);
        }
    }

    /**
     * Subset of property-service {@code FlatResponseDTO} — only the
     * fields the payment-service consumes. {@code flatNumber} drives
     * the human-readable "Flat No" line on the receipt PDF; without
     * it we'd be left showing the raw UUID.
     */
    record FlatSummary(
            String id,
            String buildingId,
            String tenantId,
            String flatNumber
    ) {
        public static FlatSummary empty() {
            return new FlatSummary(null, null, null, null);
        }
    }

    /**
     * Local mirror of property-service's {@code SocietyChargeLineItemResponse}.
     * Fields match exactly so Jackson can roundtrip; extras the server adds
     * later are silently dropped.
     */
    record SocietyChargeLine(
            String category,
            String forMonth,
            BigDecimal amountDue
    ) {}
}
