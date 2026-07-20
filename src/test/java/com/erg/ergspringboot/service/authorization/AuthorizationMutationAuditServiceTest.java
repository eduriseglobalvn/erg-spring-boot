package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationMutationAuditServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private UserActivityRepository activityRepository;
    @Mock private SecurityEventRepository securityEventRepository;
    private AuthorizationMutationAuditService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationMutationAuditService(userRepository, activityRepository, securityEventRepository);
    }

    @Test
    void authorizationChangeLocksUserInTenantAndEmitsRequestAwareVersionEvent() {
        User target = new User();
        target.setId("user-1");
        target.setTenantId("tenant-a");
        target.setAuthzVersion(7L);
        when(userRepository.lockAuthzVersions("tenant-a", List.of("user-1"))).thenReturn(List.of(target));

        service.authorizationChanged(
                List.of(new AuthorizationMutationAuditService.UserRef("user-1", "tenant-a")),
                "role-teacher",
                "SCOPED_ROLE_REVOKED",
                new AuditContext("admin-1", "request-1", "127.0.0.1", "JUnit")
        );

        assertThat(target.getAuthzVersion()).isEqualTo(8L);
        verify(userRepository).saveAll(List.of(target));
        verify(userRepository, never()).save(any(User.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserActivity>> activities = ArgumentCaptor.forClass(List.class);
        verify(activityRepository).saveAll(activities.capture());
        UserActivity activity = activities.getValue().getFirst();
        assertThat(activity.getRequestId()).isEqualTo("request-1");
        assertThat(activity.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(activity.getAction()).isEqualTo("SCOPED_ROLE_REVOKED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SecurityEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(securityEventRepository).saveAll(events.capture());
        SecurityEvent event = events.getValue().getFirst();
        assertThat(event.getEventType()).isEqualTo("AUTHZ_VERSION_CHANGED");
        assertThat(event.getUserId()).isEqualTo("user-1");
        assertThat(event.getMetadata())
                .containsEntry("actorUserId", "admin-1")
                .containsEntry("roleId", "role-teacher")
                .containsEntry("oldVersion", 7L)
                .containsEntry("newVersion", 8L)
                .containsEntry("reason", "SCOPED_ROLE_REVOKED")
                .containsEntry("requestId", "request-1");
    }
}
