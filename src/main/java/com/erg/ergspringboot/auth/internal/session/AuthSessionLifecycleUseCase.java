package com.erg.ergspringboot.auth.internal.session;

import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns explicit session logout and account-wide session revocation. */
@Service
public class AuthSessionLifecycleUseCase {

    private static final String DEFAULT_TENANT = "erg";

    private final AuthSessionRepository sessions;

    public AuthSessionLifecycleUseCase(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional
    public void logout(String userId, String tenantId, String sessionId) {
        AuthSession session = sessions.findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenantId)
                .orElseThrow(AuthFailure.InvalidToken::new);
        session.setRevokedAt(now());
        session.setRevokedReason("logout");
        sessions.save(session);
    }

    @Transactional
    public int logoutAll(String userId, String tenantId) {
        List<AuthSession> activeSessions = activeAccountSessions(
                userId, firstNonBlank(tenantId, DEFAULT_TENANT));
        revokeSessions(activeSessions, now(), "logout_all");
        return activeSessions.size();
    }

    private List<AuthSession> activeAccountSessions(String userId, String tenantId) {
        List<AuthSession> activeSessions = sessions.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, tenantId);
        if (activeSessions == null || activeSessions.isEmpty()) {
            return List.of();
        }
        return activeSessions.stream().filter(this::isSessionActive).toList();
    }

    private void revokeSessions(List<AuthSession> activeSessions, OffsetDateTime revokedAt, String reason) {
        if (activeSessions.isEmpty()) {
            return;
        }
        activeSessions.forEach(session -> {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason(reason);
        });
        sessions.saveAll(activeSessions);
    }

    private boolean isSessionActive(AuthSession session) {
        return session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now());
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
