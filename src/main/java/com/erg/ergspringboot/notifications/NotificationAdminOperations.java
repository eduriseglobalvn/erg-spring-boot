package com.erg.ergspringboot.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminSendResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminStatsResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.DeadLetterReplayResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import com.erg.ergspringboot.notifications.internal.NotificationCommand;
import com.erg.ergspringboot.notifications.internal.NotificationCommandResult;
import com.erg.ergspringboot.notifications.internal.NotificationCommandService;
import com.erg.ergspringboot.notifications.internal.NotificationOutboxWorker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public application boundary for notification administration. */
@Service
public class NotificationAdminOperations {
    /** A manual "run now" delivers at most this many rows; the scheduler keeps draining the rest. */
    static final int MANUAL_RUN_ROWS = 50;
    /** Identical admin sends inside one window of this length count as retries of each other. */
    static final long DUPLICATE_WINDOW_SECONDS = 600;

    private final NotificationCommandService commands;
    private final NotificationOutboxWorker worker;
    private final NotificationOutboxRepository outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationAdminOperations(
            NotificationCommandService commands,
            NotificationOutboxWorker worker,
            NotificationOutboxRepository outbox
    ) {
        this(commands, worker, outbox, Clock.systemUTC());
    }

    NotificationAdminOperations(
            NotificationCommandService commands,
            NotificationOutboxWorker worker,
            NotificationOutboxRepository outbox,
            Clock clock
    ) {
        this.commands = commands;
        this.worker = worker;
        this.outbox = outbox;
        this.clock = clock;
    }

    public AdminSendResponse send(String tenantId, String actorUserId, AdminSendRequest body) {
        NotificationCommandResult result = commands.enqueue(new NotificationCommand(
                tenantId,
                actorUserId,
                idempotencyKey(tenantId, actorUserId, body),
                body.eventType(),
                body.sourceType(),
                body.sourceId(),
                body.title(),
                body.bodyPreview(),
                body.actionUrl(),
                body.priority(),
                body.portal(),
                body.userIds(),
                body.topicName(),
                body.data()
        ));
        return new AdminSendResponse(result.notificationId(), result.recipientCount(), result.outboxCount());
    }

    /**
     * The caller's key when sent; otherwise a digest of tenant, sender and the whole request content plus the
     * current 10-minute window. A retry of the same request inside the window reaches the existing
     * (tenant, dedupe_key) message and returns its id with 0 new recipients instead of sending twice. The same
     * text sent again after the window (or by another admin) is a new message.
     */
    String idempotencyKey(String tenantId, String actorUserId, AdminSendRequest body) {
        if (body.idempotencyKey() != null && !body.idempotencyKey().isBlank()) {
            return body.idempotencyKey();
        }
        StringBuilder canonical = new StringBuilder();
        for (String part : new String[] {tenantId, actorUserId, body.eventType(), body.sourceType(), body.sourceId(),
                body.title(), body.bodyPreview(), body.actionUrl(), body.priority(), body.portal(), body.topicName()}) {
            canonical.append(part == null ? "" : part).append('\u0001');
        }
        TreeSet<String> users = new TreeSet<>(body.userIds() == null ? List.of() : body.userIds());
        canonical.append(String.join(",", users)).append('\u0001');
        new TreeMap<>(body.data() == null ? Map.<String, String>of() : body.data())
                .forEach((key, value) -> canonical.append(key).append('=').append(value).append(';'));
        canonical.append('\u0001').append(clock.instant().getEpochSecond() / DUPLICATE_WINDOW_SECONDS);
        try {
            return "auto:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** One grouped count, cached 20 s per tenant (the outbox is the biggest table); run/replay evict it. */
    @Cacheable(cacheNames = "notificationOutboxStats", sync = true, key = "{#tenantId}")
    @Transactional(readOnly = true)
    public AdminStatsResponse stats(String tenantId) {
        Map<String, Long> counts = new java.util.HashMap<>();
        outbox.countByTenantGroupedByStatus(tenantId).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return new AdminStatsResponse(counts.getOrDefault("pending", 0L), counts.getOrDefault("retry", 0L),
                counts.getOrDefault("sent", 0L), counts.getOrDefault("dead", 0L));
    }

    @CacheEvict(cacheNames = "notificationOutboxStats", key = "{#tenantId}")
    public OutboxRunResponse runOutbox(String tenantId) {
        return worker.runDueBatch(tenantId, MANUAL_RUN_ROWS);
    }

    @CacheEvict(cacheNames = "notificationOutboxStats", key = "{#tenantId}")
    public DeadLetterReplayResponse replayDeadLetters(String tenantId, int limit) {
        return worker.replayDeadLetters(tenantId, limit);
    }
}
