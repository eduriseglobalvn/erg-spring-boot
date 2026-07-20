package com.erg.ergspringboot.service.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class EmployeeSecurityActivityServiceTest {
    @Mock private SecurityEventRepository securityEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthLoginAttemptRepository loginAttemptRepository;
    @Mock private UserActivityRepository activityRepository;
    @Mock private AuthSessionRepository sessionRepository;
    private EmployeeSecurityActivityService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeSecurityActivityService(
                securityEventRepository,
                userRepository,
                loginAttemptRepository,
                activityRepository,
                sessionRepository);
    }

    @Test
    void unifiedFeedMergesSourcesWithStableGlobalPaginationAndEmployeeIdentity() {
        SecurityEvent warning = new SecurityEvent();
        warning.setId("event-1"); warning.setTenantId("tenant-a"); warning.setUserId("user-1");
        warning.setEventType("RAPID_SESSION_REPLACEMENT"); warning.setRiskCode("RAPID_SESSION_REPLACEMENT");
        warning.setSeverity("HIGH"); warning.setOccurredAt(OffsetDateTime.parse("2026-07-15T10:04:00Z"));
        AuthLoginAttempt login = new AuthLoginAttempt();
        login.setId("login-1"); login.setTenantId("tenant-a"); login.setUserId("user-1");
        login.setResult("SUCCESS"); login.setCreatedAt(OffsetDateTime.parse("2026-07-15T10:03:00Z"));
        UserActivity activity = new UserActivity();
        activity.setId("activity-1"); activity.setTenantId("tenant-a"); activity.setTargetUserId("user-1");
        activity.setAction("USER_ROLES_CHANGED"); activity.setCreatedAt(OffsetDateTime.parse("2026-07-15T10:02:00Z"));
        AuthSession session = new AuthSession();
        session.setId("session-row-1"); session.setTenantId("tenant-a"); session.setUserId("user-1");
        session.setSessionId("session-1"); session.setCreatedAt(OffsetDateTime.parse("2026-07-15T10:01:00Z"));
        when(securityEventRepository.findByTenantIdOrderByOccurredAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(warning), Pageable.unpaged(), 1));
        when(loginAttemptRepository.findByTenantIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(login), Pageable.unpaged(), 1));
        when(activityRepository.findByTenantIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity), Pageable.unpaged(), 1));
        when(sessionRepository.findSecurityFeedByTenant(
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session), Pageable.unpaged(), 1));
        User user = new User(); user.setId("user-1"); user.setTenantId("tenant-a");
        user.setFullName("Teacher One"); user.setEmail("teacher@erg.edu.vn");
        when(userRepository.findByTenantIdAndIdInAnyStatus("tenant-a", List.of("user-1")))
                .thenReturn(List.of(user));

        var result = service.listUnifiedFeed("tenant-a", null, null, 1, 2);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.items()).extracting(item -> item.eventType())
                .containsExactly("RAPID_SESSION_REPLACEMENT", "LOGIN_SUCCESS");
        assertThat(result.items().getFirst().userName()).isEqualTo("Teacher One");
        assertThat(result.items().getFirst().riskCode()).isEqualTo("RAPID_SESSION_REPLACEMENT");
    }

    @Test
    void unifiedFeedRejectsUnboundedMergedWindowsBeforeQueryingSources() {
        assertThatThrownBy(() -> service.listUnifiedFeed("tenant-a", null, null, 21, 100))
                .isInstanceOf(com.erg.ergspringboot.exception.BadRequestException.class)
                .hasMessageContaining("maximum merged window");
    }
}
