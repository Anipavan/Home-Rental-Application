package com.spa.home_rental_application.user_service.user_service.service;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DuplicateUserException;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.External.AuthServiceFeig;
import com.spa.home_rental_application.user_service.user_service.service.impul.UserServiceImpul;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepo userRepo;
    @Mock UserServiceEvents userServiceEvents;
    @Mock AuthServiceFeig authServiceFeig;

    UserServiceImpul service() {
        return new UserServiceImpul(userRepo, userServiceEvents, authServiceFeig, "uploads/users");
    }

    @Test
    void createUser_publishesUserProfileCreated() {
        UserRequestDto req = new UserRequestDto("AUTH-1", "Asha", "Rao",
                "asha@example.com", "+919876543210", LocalDate.of(1995, 4, 12),
                "FEMALE", "1 MG Road", null, null,
                // maritalStatus + tenantType (new optional fields)
                null, null);

        when(userRepo.existsByEmailIgnoreCaseAndIsDeletedFalse("asha@example.com")).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("USR-1");
            return u;
        });

        UserResponseDto resp = service().createUser(req);

        assertThat(resp).isNotNull();
        assertThat(resp.email()).isEqualTo("asha@example.com");

        ArgumentCaptor<UserProfileCreatedEvent> evt = ArgumentCaptor.forClass(UserProfileCreatedEvent.class);
        verify(userServiceEvents).sendUserProfileCreated(evt.capture());
        assertThat(evt.getValue().getEventType()).isEqualTo("user.profile.created");
        assertThat(evt.getValue().getUserId()).isEqualTo("USR-1");
    }

    @Test
    void createUser_whenEmailExists_throwsDuplicate() {
        when(userRepo.existsByEmailIgnoreCaseAndIsDeletedFalse("dup@example.com")).thenReturn(true);
        UserRequestDto req = new UserRequestDto("AUTH-1", "X", "Y", "dup@example.com",
                "+911234567890", null, null, null, null, null,
                // maritalStatus + tenantType
                null, null);

        assertThatThrownBy(() -> service().createUser(req))
                .isInstanceOf(DuplicateUserException.class);

        verify(userServiceEvents, never()).sendUserProfileCreated(any());
    }

    @Test
    void getUserById_throwsRecordNotFoundWhenAbsent() {
        when(userRepo.findActiveById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getUserById("missing"))
                .isInstanceOf(RecordNotFound.class);
    }

    @Test
    void deleteUserById_softDeletesAndStampsTimestamp() {
        User u = User.builder().id("USR-1").email("a@b.com").firstName("X").build();
        u.setIsDeleted(false);
        when(userRepo.findActiveById("USR-1")).thenReturn(Optional.of(u));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service().deleteUserById("USR-1");

        assertThat(u.getIsDeleted()).isTrue();
        assertThat(u.getDeletedAt()).isNotNull();
    }
}
