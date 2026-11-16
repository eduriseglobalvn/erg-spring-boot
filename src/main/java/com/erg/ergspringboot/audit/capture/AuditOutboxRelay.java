package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.platform.security.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Moves {@code audit_outbox} rows into {@code audit_events} (hash chain, retention, redaction) off the
 * request path. Lease-based claim like the other outbox worker; failures retry with backoff, then park as
 * {@code dead} and are counted in {@code erg.audit.relay.failures}.
 */
@Component
@ConditionalOnProperty(prefix = "erg.audit.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(AuditOutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 8;

    private static final String CLAIM = """
            update audit_outbox set status = 'processing', lease_until = now() + interval '60 seconds'
            where id in (
                select id from audit_outbox
                where (status = 'pending' and available_at <= now()) or (status = 'processing' and lease_until < now())
                order by created_at limit ?
                for update skip locked)
            returning id, tenant_id, payload::text as payload, attempts
            """;
    private static final String FAIL = """
            update audit_outbox set attempts = attempts + 1, lease_until = null, last_error = ?,
                status = case when attempts + 1 >= ? then 'dead' else 'pending' end,
                available_at = now() + (least(300, power(2, attempts + 1)) * interval '1 second')
            where id = ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditOperations audit;
    private final MeterRegistry meters;
    private final TransactionTemplate transaction;

    public AuditOutboxRelay(JdbcTemplate jdbc, ObjectMapper mapper, AuditOperations audit, MeterRegistry meters,
                            @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.meters = meters;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${erg.audit.relay.fixed-delay-ms:5000}")
    public void scheduledRun() {
        try {
            runDueBatch();
        } catch (RuntimeException failure) {
            log.error("Audit outbox relay batch failed", failure);
            meters.counter("erg.audit.relay.failures").increment();
        }
    }

    /** Returns how many rows were claimed. */
    public int runDueBatch() {
        List<Row> due;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            due = transaction.execute(status -> jdbc.query(CLAIM,
                    (rs, i) -> new Row(rs.getString("id"), rs.getString("tenant_id"), rs.getString("payload"), rs.getInt("attempts")),
                    BATCH_SIZE));
        }
        if (due == null) return 0;
        for (Row row : due) {
            dispatch(row);
        }
        return due.size();
    }

    private void dispatch(Row row) {
        try {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(row.tenantId(), null, "system"))) {
                audit.record(toRecord(row));
            }
            inMaintenance(() -> jdbc.update("delete from audit_outbox where id = ?", row.id()));
        } catch (RuntimeException failure) {
            // Only the failure type: messages may echo request parameters.
            log.error("Audit outbox relay failed id={} attempt={}", row.id(), row.attempts() + 1, failure);
            meters.counter("erg.audit.relay.failures").increment();
            inMaintenance(() -> jdbc.update(FAIL, failure.getClass().getSimpleName(), MAX_ATTEMPTS, row.id()));
        }
    }

    @SuppressWarnings("unchecked")
    private AuditRecord toRecord(Row row) {
        Map<String, Object> p = mapper.readValue(row.payload(), Map.class);
        return new AuditRecord(
                (String) p.get("tenantId"), (String) p.get("actorId"), (String) p.get("scopeType"), (String) p.get("scopeId"),
                (String) p.get("action"), (String) p.get("resourceType"), (String) p.get("resourceId"),
                AuditOutcome.valueOf((String) p.get("outcome")), (String) p.get("requestId"), (String) p.get("requestId"), null,
                (Map<String, Object>) p.get("details"), (String) p.get("idempotencyKey"),
                OffsetDateTime.now(), null);
    }

    private void inMaintenance(Runnable work) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            transaction.executeWithoutResult(status -> work.run());
        }
    }

    private record Row(String id, String tenantId, String payload, int attempts) {}
}
