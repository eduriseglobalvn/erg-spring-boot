package com.erg.ergspringboot.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.notifications.internal.NotificationCommandService;
import com.erg.ergspringboot.notifications.internal.NotificationOutboxWorker;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationAdminOperationsTest {

    private final NotificationOutboxWorker worker = mock(NotificationOutboxWorker.class);

    private NotificationAdminOperations at(String instant) {
        return new NotificationAdminOperations(mock(NotificationCommandService.class), worker,
                mock(NotificationOutboxRepository.class), Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static AdminSendRequest request(String key, List<String> users) {
        return new AdminSendRequest(key, "GENERAL_NOTICE", null, null, "Title", "Body", null, null, null, users, null, Map.of("k", "v"));
    }

    @Test
    void sameRequestInTheSameWindowGetsTheSameDerivedKeyWhateverTheUserOrder() {
        NotificationAdminOperations ops = at("2026-01-01T10:01:00Z");

        String first = ops.idempotencyKey("t1", "admin", request(null, List.of("a", "b")));
        String retry = at("2026-01-01T10:08:59Z").idempotencyKey("t1", "admin", request(null, List.of("b", "a")));

        assertThat(first).startsWith("auto:").hasSizeLessThanOrEqualTo(180).isEqualTo(retry);
    }

    @Test
    void differentContentSenderTenantOrWindowGivesAnotherKey() {
        NotificationAdminOperations ops = at("2026-01-01T10:01:00Z");
        String base = ops.idempotencyKey("t1", "admin", request(null, List.of("a")));

        assertThat(ops.idempotencyKey("t1", "admin", request(null, List.of("a", "c")))).isNotEqualTo(base);
        assertThat(ops.idempotencyKey("t1", "other", request(null, List.of("a")))).isNotEqualTo(base);
        assertThat(ops.idempotencyKey("t2", "admin", request(null, List.of("a")))).isNotEqualTo(base);
        assertThat(at("2026-01-01T10:12:00Z").idempotencyKey("t1", "admin", request(null, List.of("a")))).isNotEqualTo(base);
    }

    @Test
    void aKeyTheCallerSendsIsUsedAsIs() {
        assertThat(at("2026-01-01T10:01:00Z").idempotencyKey("t1", "admin", request("client-key", List.of("a"))))
                .isEqualTo("client-key");
    }

    @Test
    void manualOutboxRunIsCappedAtFiftyRows() {
        at("2026-01-01T10:01:00Z").runOutbox("t1");

        verify(worker).runDueBatch(eq("t1"), eq(50));
    }
}
