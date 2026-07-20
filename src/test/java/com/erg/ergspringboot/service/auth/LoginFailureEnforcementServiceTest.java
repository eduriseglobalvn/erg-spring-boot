package com.erg.ergspringboot.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class LoginFailureEnforcementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthSessionRepository sessionRepository;
    @Mock private AuthLoginAttemptRepository attemptRepository;

    @Test
    void invalidCredentialsNeverLockVictimAccountFromAttackerControlledFailures() throws Exception {
        User user = user();
        AuthSession session = session();
        when(attemptRepository.countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
                eq("teacher@example.com"), any(), any())).thenReturn(5L);
        when(attemptRepository.countByIpAddressAndResultInAndCreatedAtAfter(
                eq("127.0.0.1"), any(), any())).thenReturn(1L);

        LoginFailureEnforcementService service = new LoginFailureEnforcementService(
                userRepository, sessionRepository, attemptRepository);
        service.recordInvalidCredentials(
                user,
                "teacher@example.com",
                new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", "fp"),
                "lms"
        );

        Transactional transactional = LoginFailureEnforcementService.class
                .getMethod("recordInvalidCredentials", User.class, String.class, AuthClientContext.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);

        ArgumentCaptor<AuthLoginAttempt> attempt = ArgumentCaptor.forClass(AuthLoginAttempt.class);
        verify(attemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(attempt.getValue().getResult()).isEqualTo("FAILED");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(session.getRevokedReason()).isNull();
        verify(userRepository, never()).save(user);
        verify(sessionRepository, never()).saveAll(any());
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("teacher@example.com");
        user.setTenantId("tenant-a");
        user.setStatus("ACTIVE");
        return user;
    }

    private AuthSession session() {
        AuthSession session = new AuthSession();
        session.setId("session-db-1");
        session.setSessionId("session-1");
        session.setUserId("user-1");
        session.setTenantId("tenant-a");
        session.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        return session;
    }
}
