package com.erg.ergspringboot.model.learningstandards;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learning_standard_xapi_statements", indexes = {
        @Index(name = "idx_xapi_statements_tenant_statement", columnList = "tenant_id, statement_id", unique = true),
        @Index(name = "idx_xapi_statements_tenant_received", columnList = "tenant_id, received_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class XapiStatement extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;
    @Column(name = "statement_id", nullable = false, length = 96)
    private String statementId;
    @Column(name = "actor_hash", nullable = false, length = 64)
    private String actorHash;
    @Column(name = "verb", nullable = false, length = 512)
    private String verb;
    @Column(name = "object_id", nullable = false, length = 512)
    private String objectId;
    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
    @Column(name = "accepted_by", nullable = false, length = 96)
    private String acceptedBy;
    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
