package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.user_service.user_service.DTO.Request.EmergencyContactRequestDto;
import com.spa.home_rental_application.user_service.user_service.Entities.EmergencyContact;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.repositry.EmergencyContactRepo;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.impul.EmergencyContactImpul;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyContactServiceTest {

    @Mock EmergencyContactRepo repo;
    @Mock UserRepo userRepo;

    EmergencyContactImpul service() {
        return new EmergencyContactImpul(repo, userRepo);
    }

    @Test
    void save_userNotFound_throws() {
        when(userRepo.findActiveById("USR-MISSING")).thenReturn(Optional.empty());
        var dto = new EmergencyContactRequestDto("USR-MISSING", "Mom", "Mother", "+919876543210");
        assertThatThrownBy(() -> service().saveUsersEmergencyContact(dto))
                .isInstanceOf(RecordNotFound.class);
        verifyNoInteractions(repo);
    }

    @Test
    void update_contactNotFound_throws() {
        when(repo.findById("EC-1")).thenReturn(Optional.empty());
        var dto = new EmergencyContactRequestDto("USR-1", "Mom", "Mother", "+919876543210");
        assertThatThrownBy(() -> service().UpdateEmergencyContact(dto, "EC-1"))
                .isInstanceOf(RecordNotFound.class);
    }

    @Test
    void delete_contactNotFound_throws() {
        when(repo.existsById("EC-1")).thenReturn(false);
        assertThatThrownBy(() -> service().DeleteEmergencyContact("EC-1"))
                .isInstanceOf(RecordNotFound.class);
        verify(repo, never()).deleteById(any());
    }

    @Test
    void save_happyPath_persists() {
        when(userRepo.findActiveById("USR-1")).thenReturn(Optional.of(User.builder().id("USR-1").build()));
        when(repo.save(any(EmergencyContact.class))).thenAnswer(inv -> {
            EmergencyContact c = inv.getArgument(0); c.setId("EC-1"); return c;
        });

        var dto = new EmergencyContactRequestDto("USR-1", "Mom", "Mother", "+919876543210");
        service().saveUsersEmergencyContact(dto);
        verify(repo).save(any(EmergencyContact.class));
    }
}
