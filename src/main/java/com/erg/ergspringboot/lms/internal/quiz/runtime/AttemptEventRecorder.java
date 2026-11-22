package com.erg.ergspringboot.lms.internal.quiz.runtime;

import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptEvent;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptEventRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends attempt events idempotently (spec §1.7). Every runtime mutation (save-answer, submit, sync)
 * leaves an audit event so the append-only {@code attempt_events} table is the replayable source of
 * truth for an attempt. Idempotent by event id: a re-synced event with a known id is skipped, so an
 * offline client replaying its event log never double-records.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class AttemptEventRecorder {

    private final AttemptEventRepository attemptEventRepository;

    public AttemptEventRecorder(AttemptEventRepository attemptEventRepository) {
        this.attemptEventRepository = attemptEventRepository;
    }

    /** Records a single event; {@code eventId} null/blank generates a server id (non-replayable event). */
    public void record(
            String eventId,
            String tenantId,
            String attemptId,
            String quizId,
            String packageId,
            String type,
            String questionId,
            Object answer,
            Map<String, Object> result,
            OffsetDateTime clientCreatedAt
    ) {
        String id = storableId(attemptId, eventId);
        // A server-generated id is fresh, so only a client-supplied id can be a replay.
        if (eventId != null && !eventId.isBlank() && attemptEventRepository.existsById(id)) {
            return;
        }
        attemptEventRepository.save(newEvent(id, tenantId, attemptId, quizId, packageId, type, questionId, answer, result,
                clientCreatedAt));
    }

    private static AttemptEvent newEvent(String id, String tenantId, String attemptId, String quizId, String packageId,
            String type, String questionId, Object answer, Map<String, Object> result, OffsetDateTime clientCreatedAt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AttemptEvent event = new AttemptEvent();
        event.setId(id);
        event.setAttemptId(attemptId);
        event.setTenantId(tenantId);
        event.setQuizId(quizId);
        event.setPackageId(packageId);
        event.setType(type);
        event.setQuestionId(questionId);
        event.setAnswer(answer);
        event.setResult(result);
        event.setCreatedAt(clientCreatedAt == null ? now : clientCreatedAt);
        event.setReceivedAt(now);
        return event;
    }

    /** attempt_events.id is varchar(36) but clients send "evt_" + uuid (40 chars): map those to a stable uuid so replays stay idempotent. */
    static String storableId(String attemptId, String eventId) {
        if (eventId == null || eventId.isBlank()) return IdGenerator.newId();
        String id = eventId.trim();
        return id.length() <= 36 ? id : UUID.nameUUIDFromBytes((attemptId + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Audit events only: a hand-in must never fail on them, so the excess of an oversized batch is dropped, not rejected. */
    static final int MAX_CLIENT_EVENTS = 500;

    /**
     * Records a batch of client events (from a draft/sync payload), each idempotent by its own id. One select finds the ids
     * already stored, one saveAll inserts the rest. At most {@link #MAX_CLIENT_EVENTS} are accepted: the newest ones win.
     */
    @SuppressWarnings("unchecked")
    public void recordClientBatch(String tenantId, String attemptId, String quizId, String packageId, List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<Map<String, Object>> accepted = events.size() > MAX_CLIENT_EVENTS
                ? events.subList(events.size() - MAX_CLIENT_EVENTS, events.size()) : events;
        Map<String, AttemptEvent> byId = new java.util.LinkedHashMap<>(); // a duplicate id inside the batch counts once
        for (Map<String, Object> raw : accepted) {
            if (raw == null) {
                continue;
            }
            String type = string(raw.getOrDefault("type", "client_event"));
            Map<String, Object> result = raw.get("result") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : null;
            String id = storableId(attemptId, string(raw.get("id")));
            byId.putIfAbsent(id, newEvent(id, tenantId, attemptId, quizId, packageId, type, string(raw.get("questionId")),
                    raw.get("answer"), result, parseTime(raw.get("createdAt"))));
        }
        if (byId.isEmpty()) {
            return;
        }
        java.util.Set<String> stored = new java.util.HashSet<>(attemptEventRepository.findExistingIds(byId.keySet()));
        List<AttemptEvent> fresh = byId.values().stream().filter(event -> !stored.contains(event.getId())).toList();
        if (!fresh.isEmpty()) {
            attemptEventRepository.saveAll(fresh);
        }
    }

    private OffsetDateTime parseTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
