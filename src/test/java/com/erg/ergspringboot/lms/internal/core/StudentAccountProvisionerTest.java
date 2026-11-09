package com.erg.ergspringboot.lms.internal.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.CredentialHasher;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StudentAccountProvisionerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    private final CredentialHasher hasher = mock(CredentialHasher.class);
    private final StudentAccountProvisioner provisioner = new StudentAccountProvisioner(users, assignments, hasher);
    private final UserAccessContext access = new UserAccessContext("admin-1", "erg", List.of(), List.of(), null, null);

    @Test
    void createsAnActiveLearnerThatSkipsOnboarding() {
        when(hasher.hash("123456")).thenReturn("hash");

        String id = provisioner.provision(access, "school-1", " An.Nguyen7A ", null, "Nguyễn An", "0900", "123456");

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getId()).isEqualTo(id);
        assertThat(user.getValue().getUsername()).isEqualTo("an.nguyen7a");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(user.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getValue().getTenantId()).isEqualTo("erg");
        assertThat(user.getValue().getIsProfileCompleted()).isTrue();
        assertThat(user.getValue().getRecoveryEmailVerified()).isTrue();
        assertThat(user.getValue().getMustChangePassword()).isFalse();

        ArgumentCaptor<UserRoleAssignment> role = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(assignments).save(role.capture());
        assertThat(role.getValue().getUserId()).isEqualTo(id);
        assertThat(role.getValue().getRoleId()).isEqualTo("role_lms_student");
        assertThat(role.getValue().getScopeType()).isEqualTo("SCHOOL");
        assertThat(role.getValue().getScopeId()).isEqualTo("school-1");
        assertThat(role.getValue().getAssignedBy()).isEqualTo("admin-1");
    }

    @Test
    void refusesALoginThatIsAlreadyTaken() {
        when(users.existsByUsernameIgnoreCase("an")).thenReturn(true);

        assertThatThrownBy(() -> provisioner.provision(access, "school-1", "An", null, "An", null, "123456"))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
        verify(assignments, never()).save(any());
    }
}
