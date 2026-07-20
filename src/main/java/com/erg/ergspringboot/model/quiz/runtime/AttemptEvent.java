package com.erg.ergspringboot.model.quiz.runtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only attempt event, idempotent by {@code id} = {@code AttemptEvent.id} (spec §1.7). Replay is
 * safe: inserts use {@code on conflict (id) do nothing} so a re-synced event never double-counts. The
 * client clock is {@code createdAt}; the server stamps {@code receivedAt}. Out-of-order is accepted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "attempt_events",
        indexes = {
                @Index(name = "idx_attempt_events_attempt_created", columnList = "attempt_id, created_at")
        }
)
public class AttemptEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "attempt_id", nullable = false)
    private String attemptId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "quiz_id")
    private String quizId;

    @Column(name = "package_id")
    private String packageId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "question_id")
    private String questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private Object answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private Map<String, Object> meta;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
