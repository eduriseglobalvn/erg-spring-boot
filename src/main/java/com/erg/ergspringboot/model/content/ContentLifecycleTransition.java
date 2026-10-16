package com.erg.ergspringboot.model.content;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Append-only lifecycle trail. BE-061 owns the broader audit stream; this records state invariants. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(name = "content_lifecycle_transitions", indexes = {
        @Index(name = "idx_content_lifecycle_resource", columnList = "tenant_id, resource_kind, resource_id, created_at"),
        @Index(name = "idx_content_lifecycle_idempotency", columnList = "tenant_id, resource_kind, resource_id, idempotency_key")
})
public class ContentLifecycleTransition extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "resource_kind", nullable = false, length = 48)
    private String resourceKind;

    @Column(name = "resource_id", nullable = false, length = 96)
    private String resourceId;

    @Column(name = "from_state", nullable = false, length = 32)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 32)
    private String toState;

    @Column(name = "actor_id", nullable = false, length = 96)
    private String actorId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "expected_version")
    private Long expectedVersion;

    @Column(name = "resulting_version")
    private Long resultingVersion;

    @Column(name = "operation_id", length = 128)
    private String operationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "source_version_id", length = 96)
    private String sourceVersionId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;
}
