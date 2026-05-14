package com.spa.home_rental_application.property_service.property_service.service.impl;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.PropertyServiceEvents;
import com.spa.home_rental_application.property_service.property_service.DTO.Response.FlatResponseDTO;
import com.spa.home_rental_application.property_service.property_service.Entities.Flat;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.InvalidLeasePeriodException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.OutstandingDuesException;
import com.spa.home_rental_application.property_service.property_service.ExceptionClass.RecordNotFoundException;
import com.spa.home_rental_application.property_service.property_service.client.PaymentClient;
import com.spa.home_rental_application.property_service.property_service.client.UserClient;
import com.spa.home_rental_application.property_service.property_service.DTO.FlatMapper;
import com.spa.home_rental_application.property_service.property_service.repository.BuildingRepo;
import com.spa.home_rental_application.property_service.property_service.repository.FlatRepo;
import com.spa.home_rental_application.property_service.property_service.service.AgreementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-15e: tenant-scheduled-vacate coverage for FlatServiceImpul.
 *
 *  POSITIVE
 *   - Valid date (today+60) + no dues → scheduledVacateDate persisted,
 *     vacateWarningSentAt cleared so the 10-day-prior owner notice
 *     fires next cron sweep.
 *   - Already-scheduled (idempotent) → returns existing without
 *     hitting the Feign dues check.
 *   - Date beyond the 60-day floor (e.g. today+90) accepted.
 *
 *  NEGATIVE
 *   - Date earlier than today+60 throws InvalidLeasePeriodException
 *     with "Can't vacate the house before 60 days" message.
 *   - Null date throws InvalidLeasePeriodException.
 *   - Flat that's not occupied throws InvalidLeasePeriodException.
 *   - Unknown flat id throws RecordNotFoundException.
 *   - Outstanding dues (PaymentClient says unpaid > 0) throws
 *     OutstandingDuesException.
 *   - PaymentClient itself throws → fails closed (also throws
 *     OutstandingDuesException), defensive against partial-outage.
 *
 *  Notes:
 *   - CallerSecurity.requireSelfOrAdmin walks RequestContextHolder; with
 *     no request bound (the default for Mockito unit tests) it falls
 *     through to "system call → allow" so we don't need a HTTP shim
 *     to drive the happy path.
 *   - Event publisher is mocked at the bean boundary; Kafka isn't
 *     reachable in this test.
 */
@ExtendWith(MockitoExtension.class)
class FlatScheduleVacateTest {

    @Mock FlatRepo flatRepo;
    @Mock BuildingRepo buildingRepo;
    @Mock PropertyServiceEvents eventProducer;
    @Mock FlatMapper flatMapper;
    @Mock AgreementService agreementService;
    @Mock UserClient userClient;
    @Mock PaymentClient paymentClient;

    private FlatServiceImpul service() {
        return new FlatServiceImpul(flatRepo, buildingRepo, eventProducer,
                flatMapper, agreementService, userClient, paymentClient);
    }

    private Flat occupiedFlat(String id) {
        Flat f = Flat.builder()
                .id(id)
                .flatNumber("A-301")
                .floor(3)
                .bedrooms(2)
                .rentAmount(new BigDecimal("15000"))
                .tenantId("T-1")
                .isOccupied(true)
                .buildingId("B-1")
                .leaseStartDate(LocalDate.now().minusMonths(3))
                .leaseEndDate(LocalDate.now().plusYears(1))
                .build();
        return f;
    }

    private LocalDate atLeast60DaysOut() {
        return LocalDate.now().plusDays(65);
    }

    private void stubMapperPassthrough() {
        // Wire the mock mapper to return a minimal DTO so the test
        // assertions don't NPE. Field-by-field mapping is covered by
        // FlatMapper's own tests; here we just need scheduledVacateDate
        // to round-trip from entity → DTO so the test can assert on it.
        lenient().when(flatMapper.toResponseDTO(any(Flat.class))).thenAnswer(inv -> {
            Flat f = inv.getArgument(0);
            return new FlatResponseDTO(
                    f.getId(),
                    f.getBuildingId(),
                    null,                    // buildingName
                    null,                    // buildingAddress
                    null,                    // buildingCity
                    f.getFlatNumber(),
                    f.getFloor(),
                    f.getBedrooms(),
                    null,                    // bathrooms
                    null,                    // areaSqft
                    f.getRentAmount(),
                    f.getIsOccupied(),
                    f.getTenantId(),
                    f.getLeaseStartDate(),
                    f.getLeaseEndDate(),
                    null,                    // furnishingStatus
                    null,                    // petFriendly
                    null,                    // availableFrom
                    null,                    // depositAmount
                    null,                    // description
                    f.getScheduledVacateDate(),
                    null,                    // createdAt
                    null);                   // updatedAt
        });
    }

    /* ───────────────────────── POSITIVE ───────────────────────── */

    @Test
    @DisplayName("[+] valid date (today+65) + no dues → persists scheduledVacateDate, clears warning")
    void happy_path_persists_schedule() {
        stubMapperPassthrough();
        Flat flat = occupiedFlat("F-1");
        when(flatRepo.findById(eq("F-1"))).thenReturn(Optional.of(flat));
        when(paymentClient.getUnpaidByFlat(eq("F-1")))
                .thenReturn(new PaymentClient.UnpaidSummary(
                        "F-1", 0, BigDecimal.ZERO, List.of()));
        when(flatRepo.save(any(Flat.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate effective = atLeast60DaysOut();
        FlatResponseDTO resp = service().scheduleVacate("F-1", effective);

        ArgumentCaptor<Flat> saved = ArgumentCaptor.forClass(Flat.class);
        verify(flatRepo).save(saved.capture());
        assertThat(saved.getValue().getScheduledVacateDate()).isEqualTo(effective);
        assertThat(saved.getValue().getVacateWarningSentAt()).isNull();
        assertThat(resp.scheduledVacateDate()).isEqualTo(effective);
    }

    @Test
    @DisplayName("[+] idempotent: already-scheduled returns existing without dues check")
    void already_scheduled_is_idempotent() {
        stubMapperPassthrough();
        Flat flat = occupiedFlat("F-IDEM");
        flat.setScheduledVacateDate(LocalDate.now().plusDays(80));
        when(flatRepo.findById(eq("F-IDEM"))).thenReturn(Optional.of(flat));

        FlatResponseDTO resp = service().scheduleVacate("F-IDEM", atLeast60DaysOut());

        assertThat(resp.scheduledVacateDate()).isEqualTo(flat.getScheduledVacateDate());
        // Critical: short-circuited BEFORE the dues check
        verify(paymentClient, never()).getUnpaidByFlat(any());
        verify(flatRepo, never()).save(any());
    }

    @Test
    @DisplayName("[+] far-future date (today+180) accepted as long as dues are clear")
    void far_future_date_accepted() {
        stubMapperPassthrough();
        Flat flat = occupiedFlat("F-FAR");
        when(flatRepo.findById(eq("F-FAR"))).thenReturn(Optional.of(flat));
        when(paymentClient.getUnpaidByFlat(eq("F-FAR")))
                .thenReturn(new PaymentClient.UnpaidSummary(
                        "F-FAR", 0, BigDecimal.ZERO, List.of()));
        when(flatRepo.save(any(Flat.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate effective = LocalDate.now().plusDays(180);
        FlatResponseDTO resp = service().scheduleVacate("F-FAR", effective);

        assertThat(resp.scheduledVacateDate()).isEqualTo(effective);
    }

    /* ───────────────────────── NEGATIVE ───────────────────────── */

    @Test
    @DisplayName("[-] date < today+60 throws InvalidLeasePeriodException with 60-day message")
    void date_before_floor_rejected() {
        Flat flat = occupiedFlat("F-EARLY");
        when(flatRepo.findById(eq("F-EARLY"))).thenReturn(Optional.of(flat));

        assertThatThrownBy(() -> service().scheduleVacate("F-EARLY", LocalDate.now().plusDays(30)))
                .isInstanceOf(InvalidLeasePeriodException.class)
                .hasMessageContaining("60 days");

        verify(paymentClient, never()).getUnpaidByFlat(any());
        verify(flatRepo, never()).save(any());
    }

    @Test
    @DisplayName("[-] null date throws InvalidLeasePeriodException")
    void null_date_rejected() {
        Flat flat = occupiedFlat("F-NULL");
        when(flatRepo.findById(eq("F-NULL"))).thenReturn(Optional.of(flat));

        assertThatThrownBy(() -> service().scheduleVacate("F-NULL", null))
                .isInstanceOf(InvalidLeasePeriodException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("[-] non-occupied flat throws InvalidLeasePeriodException (nothing to vacate)")
    void non_occupied_flat_rejected() {
        Flat flat = occupiedFlat("F-EMPTY");
        flat.setIsOccupied(false);
        flat.setTenantId(null);
        when(flatRepo.findById(eq("F-EMPTY"))).thenReturn(Optional.of(flat));

        assertThatThrownBy(() -> service().scheduleVacate("F-EMPTY", atLeast60DaysOut()))
                .isInstanceOf(InvalidLeasePeriodException.class)
                .hasMessageContaining("not occupied");
    }

    @Test
    @DisplayName("[-] unknown flat id throws RecordNotFoundException")
    void unknown_flat_id_rejected() {
        when(flatRepo.findById(eq("F-NONE"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().scheduleVacate("F-NONE", atLeast60DaysOut()))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    @DisplayName("[-] outstanding dues throws OutstandingDuesException with amount/count detail")
    void outstanding_dues_block_vacate() {
        Flat flat = occupiedFlat("F-DEBT");
        when(flatRepo.findById(eq("F-DEBT"))).thenReturn(Optional.of(flat));
        when(paymentClient.getUnpaidByFlat(eq("F-DEBT")))
                .thenReturn(new PaymentClient.UnpaidSummary(
                        "F-DEBT", 2, new BigDecimal("30000"),
                        List.of("INV-1", "INV-2")));

        assertThatThrownBy(() -> service().scheduleVacate("F-DEBT", atLeast60DaysOut()))
                .isInstanceOf(OutstandingDuesException.class)
                .hasMessageContaining("30000")
                .hasMessageContaining("2");

        verify(flatRepo, never()).save(any());
    }

    @Test
    @DisplayName("[-] payment-service Feign blows up → fails closed (OutstandingDuesException)")
    void feign_failure_fails_closed() {
        Flat flat = occupiedFlat("F-FAIL");
        when(flatRepo.findById(eq("F-FAIL"))).thenReturn(Optional.of(flat));
        when(paymentClient.getUnpaidByFlat(eq("F-FAIL")))
                .thenThrow(new RuntimeException("payment-service unreachable"));

        assertThatThrownBy(() -> service().scheduleVacate("F-FAIL", atLeast60DaysOut()))
                .isInstanceOf(OutstandingDuesException.class)
                .hasMessageContaining("verify outstanding dues");

        verify(flatRepo, never()).save(any());
    }
}
