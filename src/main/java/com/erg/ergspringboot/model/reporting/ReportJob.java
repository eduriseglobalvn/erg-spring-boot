package com.erg.ergspringboot.model.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report_jobs")
public class ReportJob {
    @Id
    @Column(length = 96, nullable = false, updatable = false)
    private String id;
    @Column(name = "tenant_id", length = 96, nullable = false, updatable = false)
    private String tenantId;
    @Column(name = "requested_by", length = 96, nullable = false, updatable = false)
    private String requestedBy;
    @Column(name = "report_type", length = 64, nullable = false, updatable = false)
    private String reportType;
    @Column(name = "aggregate_id", length = 96, nullable = false, updatable = false)
    private String aggregateId;
    @Column(name = "idempotency_key", length = 192, nullable = false, updatable = false)
    private String idempotencyKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> requestJson = Map.of();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;
    @Column(length = 24, nullable = false)
    private String status = "queued";
    @Column(nullable = false)
    private int progress;
    @Column(nullable = false)
    private int attempts;
    @Column(length = 16, nullable = false)
    private String format = "json";
    @Column(name = "available_at", nullable = false)
    private OffsetDateTime availableAt;
    @Column(name = "lease_until")
    private OffsetDateTime leaseUntil;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "started_at")
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Map<String, Object> getRequestJson() { return requestJson; }
    public void setRequestJson(Map<String, Object> requestJson) { this.requestJson = requestJson; }
    public Map<String, Object> getResultJson() { return resultJson; }
    public void setResultJson(Map<String, Object> resultJson) { this.resultJson = resultJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public OffsetDateTime getAvailableAt() { return availableAt; }
    public void setAvailableAt(OffsetDateTime availableAt) { this.availableAt = availableAt; }
    public OffsetDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(OffsetDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
