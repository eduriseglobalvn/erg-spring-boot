package com.erg.ergspringboot.service.quiz.runtime;

import com.erg.ergspringboot.model.quiz.runtime.AttemptEvent;
import com.erg.ergspringboot.repository.quiz.runtime.AttemptEventRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
        String id = eventId == null || eventId.isBlank() ? IdGenerator.newId() : eventId.trim();
        if (attemptEventRepository.existsById(id)) {
            return;
        }
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
        attemptEventRepository.save(event);
    }

    /** Records a batch of client events (from a draft/sync payload), each idempotent by its own id. */
    @SuppressWarnings("unchecked")
    public void recordClientBatch(String tenantId, String attemptId, String quizId, String packageId, List<Map<String, Object>> events) {
        if (events == null) {
            return;
        }
        for (Map<String, Object> raw : events) {
            if (raw == null) {
                continue;
            }
            String eventId = string(raw.get("id"));
            String type = string(raw.getOrDefault("type", "client_event"));
            String questionId = string(raw.get("questionId"));
            Object answer = raw.get("answer");
            Map<String, Object> result = raw.get("result") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : null;
            record(eventId, tenantId, attemptId, quizId, packageId, type, questionId, answer, result,
                    parseTime(raw.get("createdAt")));
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
