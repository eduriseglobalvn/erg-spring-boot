package com.erg.ergspringboot.model.audit;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.audit.AuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Canonical append-only audit projection. Updates/deletes are rejected by V93. */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_events_tenant_time", columnList = "tenant_id, occurred_at, id"),
        @Index(name = "idx_audit_events_retention", columnList = "retention_until, legal_hold")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96, updatable = false)
    private String tenantId;
    @Column(name = "actor_id", length = 96, updatable = false)
    private String actorId;
    @Column(name = "scope_type", length = 64, updatable = false)
    private String scopeType;
    @Column(name = "scope_id", length = 96, updatable = false)
    private String scopeId;
    @Column(name = "action", nullable = false, length = 128, updatable = false)
    private String action;
    @Column(name = "resource_type", nullable = false, length = 96, updatable = false)
    private String resourceType;
    @Column(name = "resource_id", length = 96, updatable = false)
    private String resourceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 24, updatable = false)
    private AuditOutcome outcome;
    @Column(name = "request_id", length = 128, updatable = false)
    private String requestId;
    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;
    @Column(name = "causation_id", length = 128, updatable = false)
    private String causationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false, updatable = false)
    private Map<String, Object> details = new LinkedHashMap<>();
    @Column(name = "idempotency_key", nullable = false, length = 192, updatable = false)
    private String idempotencyKey;
    @Column(name = "previous_hash", length = 64, updatable = false)
    private String previousHash;
    @Column(name = "event_hash", nullable = false, length = 64, updatable = false)
    private String eventHash;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "retention_until", nullable = false, updatable = false)
    private OffsetDateTime retentionUntil;
    @Column(name = "redaction_version", nullable = false, updatable = false)
    private int redactionVersion = 1;
    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;
}
