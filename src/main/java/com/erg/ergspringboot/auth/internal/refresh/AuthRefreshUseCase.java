package com.erg.ergspringboot.auth.internal.refresh;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.RefreshTokenHistory;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.service.auth.AuthTokenService;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns refresh-token rotation, durable replay history and account-wide replay response. */
@Service
public class AuthRefreshUseCase {

    private static final String DEFAULT_TENANT = "erg";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final AuthSessionRepository sessions;
    private final AuthTokenService tokens;
    private SecurityEventRepository securityEvents;
    private RefreshTokenHistoryRepository refreshHistory;

    @Autowired
    public AuthRefreshUseCase(
            AuthSessionRepository sessions,
            AuthTokenService tokens,
            ObjectProvider<SecurityEventRepository> securityEvents,
            ObjectProvider<RefreshTokenHistoryRepository> refreshHistory) {
        this(sessions, tokens);
        this.securityEvents = securityEvents.getIfAvailable();
        this.refreshHistory = refreshHistory.getIfAvailable();
    }

    /** Compatibility constructor for the legacy facade and focused unit tests. */
    public AuthRefreshUseCase(AuthSessionRepository sessions, AuthTokenService tokens) {
        this.sessions = sessions;
        this.tokens = tokens;
    }

    public void setSecurityEventRepository(SecurityEventRepository securityEvents) {
        this.securityEvents = securityEvents;
    }

    public void setRefreshTokenHistoryRepository(RefreshTokenHistoryRepository refreshHistory) {
        this.refreshHistory = refreshHistory;
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        String currentHash = tokens.hashToken(request.refreshToken());
        AuthSession session = sessions.lockByRefreshTokenHash(currentHash)
                .filter(this::isSessionActive)
                .orElse(null);
        if (session == null) {
            AuthSession replayed = sessions.findByPreviousRefreshTokenHash(currentHash).orElse(null);
            if (replayed == null && refreshHistory != null) {
                replayed = refreshHistory.findByTokenHash(currentHash)
                        .flatMap(history -> sessions.findBySessionIdAndTenantId(
                                history.getSessionId(), history.getTenantId()))
                        .orElse(null);
            }
            if (replayed != null) {
                handleReplay(replayed, context);
            }
            throw new AuthFailure.InvalidToken();
        }

        String accessToken = tokens.newToken();
        String refreshToken = tokens.newToken();
        OffsetDateTime now = now();
        if (refreshHistory != null) {
            RefreshTokenHistory history = new RefreshTokenHistory();
            history.setId(IdGenerator.newId());
            history.setTokenHash(currentHash);
            history.setSessionId(session.getSessionId());
            history.setUserId(session.getUserId());
            history.setTenantId(firstNonBlank(session.getTenantId(), DEFAULT_TENANT));
            history.setConsumedAt(now);
            history.setExpiresAt(session.getExpiresAt());
            refreshHistory.save(history);
        }
        session.setPreviousRefreshTokenHash(currentHash);
        session.setRefreshTokenRotatedAt(now);
        session.setAccessTokenHash(tokens.hashToken(accessToken));
        session.setRefreshTokenHash(tokens.hashToken(refreshToken));
        session.setLastActiveAt(now);
        session.setAccessTokenExpiresAt(now.plus(ACCESS_TOKEN_TTL));
        sessions.save(session);

        return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds(), "Bearer");
    }

    private void handleReplay(AuthSession replayed, AuthClientContext context) {
        OffsetDateTime now = now();
        List<AuthSession> activeSessions = activeAccountSessions(
                replayed.getUserId(), firstNonBlank(replayed.getTenantId(), DEFAULT_TENANT));
        activeSessions.forEach(session -> {
            session.setRevokedAt(now);
            session.setRevokedReason("refresh_token_reuse");
        });
        if (!activeSessions.isEmpty()) {
            sessions.saveAll(activeSessions);
        }
        if (securityEvents != null) {
            SecurityEvent event = new SecurityEvent();
            event.setId(IdGenerator.newId());
            event.setTenantId(replayed.getTenantId());
            event.setUserId(replayed.getUserId());
            event.setSessionId(replayed.getSessionId());
            event.setEventType("REFRESH_TOKEN_REUSE");
            event.setSeverity("CRITICAL");
            event.setRiskCode("TOKEN_REPLAY");
            event.setResult("SESSIONS_REVOKED");
            event.setReason("rotated_refresh_token_reused");
            event.setIpAddress(context == null ? null : context.ipAddress());
            event.setDeviceId(context == null ? null : context.deviceId());
            event.setOccurredAt(now);
            securityEvents.save(event);
        }
    }

    private List<AuthSession> activeAccountSessions(String userId, String tenantId) {
        List<AuthSession> activeSessions = sessions.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, tenantId);
        if (activeSessions == null || activeSessions.isEmpty()) {
            return List.of();
        }
        return activeSessions.stream().filter(this::isSessionActive).toList();
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
