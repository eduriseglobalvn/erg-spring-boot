package com.erg.ergspringboot.users.internal.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserLifecycleUseCaseSoftDeleteTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final UserLifecycleUseCase useCase =
            new UserLifecycleUseCase(users, sessions, mock(UserActivityRepository.class));

    @Test
    void deleteKeepsTheRowButDisablesAndAnonymisesTheAccount() {
        User user = user("usr_1");
        AuthSession session = new AuthSession();
        when(users.findByIdAndTenantId("usr_1", "erg")).thenReturn(Optional.of(user));
        when(sessions.findByUserIdAndTenantIdAndRevokedAtIsNull("usr_1", "erg")).thenReturn(List.of(session));

        assertThat(useCase.delete("usr_1", "erg").modifiedCount()).isEqualTo(1);

        verify(users, never()).delete(any());
        verify(users, never()).deleteAll(any());
        assertThat(user.getStatus()).isEqualTo("DELETED");
        assertThat(user.getEmail()).isEqualTo("deleted-usr_1@deleted.invalid");
        assertThat(user.getUsername()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getFullName()).isEqualTo("Deleted user");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getAddress()).isNull();
        assertThat(user.getDateOfBirth()).isNull();
        assertThat(user.getRecoveryEmail()).isNull();
        assertThat(user.getAuthzVersion()).isEqualTo(2L);
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevokedReason()).isEqualTo("status_changed_deleted");
    }

    @Test
    void bulkDeleteSoftDeletesEveryUser() {
        User first = user("usr_1");
        User second = user("usr_2");
        when(users.findByIdAndTenantId("usr_1", "erg")).thenReturn(Optional.of(first));
        when(users.findByIdAndTenantId("usr_2", "erg")).thenReturn(Optional.of(second));

        useCase.bulkDelete("erg", new BulkDeleteRequest(List.of("usr_1", "usr_2")));

        verify(users, never()).deleteAll(any());
        assertThat(List.of(first.getStatus(), second.getStatus())).containsOnly("DELETED");
    }

    @Test
    void alreadyDeletedUserIsNotFound() {
        User user = user("usr_1");
        user.setStatus("DELETED");
        when(users.findByIdAndTenantId("usr_1", "erg")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.delete("usr_1", "erg")).isInstanceOf(ResourceNotFoundException.class);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setTenantId("erg");
        user.setStatus("ACTIVE");
        user.setEmail(id + "@example.test");
        user.setUsername(id);
        user.setPasswordHash("not-a-real-hash");
        user.setFullName("Some Person");
        user.setPhone("000");
        user.setAddress("somewhere");
        user.setDateOfBirth("2000-01-01");
        user.setRecoveryEmail("recovery@example.test");
        return user;
    }
}
