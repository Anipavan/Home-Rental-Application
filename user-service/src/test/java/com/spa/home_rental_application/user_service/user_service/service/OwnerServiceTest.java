package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.OwnerRegisteredEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.OwnerRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.OwnerResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.Owners;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.repositry.OwnerRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.External.PropertyServiceFeig;
import com.spa.home_rental_application.user_service.user_service.service.impul.OwnerServiceImpul;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @Mock OwnerRepo ownerRepo;
    @Mock UserRepo userRepo;
    @Mock UserServiceEvents events;
    @Mock PropertyServiceFeig propertyFeign;

    OwnerServiceImpul service() {
        return new OwnerServiceImpul(ownerRepo, userRepo, events, propertyFeign);
    }

    @Test
    void createOwner_publishesOwnerRegistered() {
        when(userRepo.findActiveById("USR-1")).thenReturn(Optional.of(
                User.builder().id("USR-1").firstName("Owner").build()));
        when(ownerRepo.save(any(Owners.class))).thenAnswer(inv -> {
            Owners o = inv.getArgument(0); o.setId("OWN-1"); return o;
        });

        OwnerRequestDto req = new OwnerRequestDto("USR-1", "Acme Realty",
                "27ABCDE1234F1Z5", "ABCDE1234F", "1234567890123", "HDFC0000001", 5);

        OwnerResponseDto resp = service().createOwner(req);

        assertThat(resp.id()).isEqualTo("OWN-1");
        ArgumentCaptor<OwnerRegisteredEvent> evt = ArgumentCaptor.forClass(OwnerRegisteredEvent.class);
        verify(events).sendOwnerRegistered(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("owner.registered");
        assertThat(evt.getValue().getOwnerId()).isEqualTo("OWN-1");
    }

    @Test
    void createOwner_userNotFound_throws() {
        when(userRepo.findActiveById("USR-MISSING")).thenReturn(Optional.empty());
        OwnerRequestDto req = new OwnerRequestDto("USR-MISSING", "Acme",
                "27ABCDE1234F1Z5", "ABCDE1234F", "1234567890123", "HDFC0000001", 0);
        assertThatThrownBy(() -> service().createOwner(req))
                .isInstanceOf(RecordNotFound.class);
        verifyNoInteractions(ownerRepo);
    }

    @Test
    void getOwnerById_notFound_throws() {
        when(ownerRepo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getOwnerById("missing"))
                .isInstanceOf(RecordNotFound.class);
    }

    @Test
    void getTenants_returnsResolvedUsers_viaFeign() {
        when(ownerRepo.findById("OWN-1")).thenReturn(Optional.of(
                Owners.builder().id("OWN-1").userId("USR-OWNER").build()));
        when(propertyFeign.getTenantIdsByOwner("OWN-1")).thenReturn(List.of("USR-T1", "USR-T2"));
        when(userRepo.findActiveById("USR-T1")).thenReturn(Optional.of(
                User.builder().id("USR-T1").firstName("Alice").email("a@x.com").build()));
        when(userRepo.findActiveById("USR-T2")).thenReturn(Optional.of(
                User.builder().id("USR-T2").firstName("Bob").email("b@x.com").build()));

        List<UserResponseDto> tenants = service().getTenentsByOwnerId("OWN-1");

        assertThat(tenants).hasSize(2);
        assertThat(tenants).extracting(UserResponseDto::firstName).contains("Alice", "Bob");
    }

    @Test
    void getTenants_propertyServiceDown_returnsEmpty() {
        when(ownerRepo.findById("OWN-1")).thenReturn(Optional.of(
                Owners.builder().id("OWN-1").build()));
        when(propertyFeign.getTenantIdsByOwner("OWN-1")).thenThrow(new RuntimeException("conn refused"));

        assertThat(service().getTenentsByOwnerId("OWN-1")).isEmpty();
    }
}
