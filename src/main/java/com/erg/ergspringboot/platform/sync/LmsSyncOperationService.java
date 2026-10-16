package com.erg.ergspringboot.platform.sync;

import com.erg.ergspringboot.common.response.sync.SyncMutationResponses.ConflictPayload;
import com.erg.ergspringboot.common.response.sync.SyncMutationResponses.MutationAck;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.SyncConflictException;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Durable server-side receipt boundary for offline mutation replay.
 *
 * <p>Only a canonical payload hash and the first response are retained. Raw client payloads,
 * credentials and bearer material never enter the receipt table.</p>
 */
@Service
public class LmsSyncOperationService {
    private static final int MAX_OPERATION_ID = 128;
    private static final int MAX_KEY = 255;
    private static final int MAX_ENTITY = 160;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LmsSyncOperationService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Reserves a key; empty means the caller is using a legacy non-replayable request. */
    @Transactional(value = "postgresTransactionManager")
    public Optional<Receipt> reserve(
            String tenantId,
            String accountId,
            String operationId,
            String idempotencyKey,
            String entityType,
            String entityId,
            Object payload,
            Long expectedVersion
    ) {
        String tenant = AuthenticatedTenant.require(tenantId);
        String account = required(accountId, "accountId", 255);
        String key = normalize(idempotencyKey, "idempotencyKey", MAX_KEY);
        if (key == null) return Optional.empty();
        String operationValue = normalize(operationId, "operationId", MAX_OPERATION_ID);
        String operation = operationValue == null ? key : operationValue;
        String type = required(entityType, "entityType", MAX_ENTITY);
        String id = required(entityId, "entityId", MAX_ENTITY);
        String hash = payloadHash(payload);
        Optional<Receipt> existing = find(tenant, account, key);
        if (existing.isPresent()) {
            Receipt receipt = existing.get();
            if (!operation.equals(receipt.operationId()) || !hash.equals(receipt.payloadHash())) {
                throw conflict("IDEMPOTENCY_CONFLICT", receipt, expectedVersion, receipt.serverVersion(), false);
            }
            if ("SUCCEEDED".equals(receipt.status())) return Optional.of(receipt.asReplayed());
            throw conflict("SYNC_PENDING", receipt, expectedVersion, receipt.serverVersion(), true);
        }
        OffsetDateTime now = serverTime();
        try {
            jdbc.update("""
                    insert into lms_sync_operations
                      (tenant_id, account_id, operation_id, idempotency_key, entity_type, entity_id,
                       payload_hash, status, server_version, result_json, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, 'PENDING', null, null, ?, ?)
                    """, tenant, account, operation, key, type, id, hash, now, now);
        } catch (DataIntegrityViolationException race) {
            Receipt raced = find(tenant, account, key)
                    .or(() -> findByOperation(tenant, account, operation))
                    .orElseThrow(() -> race);
            if (!operation.equals(raced.operationId()) || !hash.equals(raced.payloadHash())) {
                throw conflict("IDEMPOTENCY_CONFLICT", raced, expectedVersion, raced.serverVersion(), false);
            }
            if ("SUCCEEDED".equals(raced.status())) return Optional.of(raced.asReplayed());
            throw conflict("SYNC_PENDING", raced, expectedVersion, raced.serverVersion(), true);
        }
        return Optional.of(new Receipt(tenant, account, operation, key, type, id, hash,
                "PENDING", null, null, now, now, false));
    }

    @Transactional(value = "postgresTransactionManager")
    public MutationAck complete(Receipt receipt, Object result, long serverVersion) {
        if (receipt == null) return null;
        OffsetDateTime now = serverTime();
        jdbc.update("""
                update lms_sync_operations
                   set status = 'SUCCEEDED', server_version = ?, result_json = cast(? as jsonb), updated_at = ?
                 where tenant_id = ? and account_id = ? and idempotency_key = ? and status = 'PENDING'
                """, serverVersion, json(result), now, receipt.tenantId(), receipt.accountId(), receipt.idempotencyKey());
        return new MutationAck(receipt.operationId(), receipt.idempotencyKey(), receipt.entityType(), receipt.entityId(),
                "SUCCEEDED", serverVersion, now, false);
    }

    @Transactional(value = "postgresTransactionManager")
    public void abandon(Receipt receipt) {
        if (receipt == null) return;
        jdbc.update("delete from lms_sync_operations where tenant_id = ? and account_id = ? and idempotency_key = ? and status = 'PENDING'",
                receipt.tenantId(), receipt.accountId(), receipt.idempotencyKey());
    }

    public <T> T result(Receipt receipt, Class<T> type) {
        if (receipt == null || receipt.resultJson() == null) return null;
        try {
            return mapper.readValue(receipt.resultJson(), type);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored sync result cannot be decoded", exception);
        }
    }

    public String payloadHash(Object payload) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(canonical(payload));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (RuntimeException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash sync payload", exception);
        }
    }

    public OffsetDateTime serverTime() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private Optional<Receipt> find(String tenant, String account, String key) {
        List<Receipt> rows = jdbc.query("""
                select tenant_id, account_id, operation_id, idempotency_key, entity_type, entity_id,
                       payload_hash, status, server_version, result_json, created_at, updated_at
                  from lms_sync_operations where tenant_id = ? and account_id = ? and idempotency_key = ?
                """, (rs, row) -> new Receipt(rs.getString("tenant_id"), rs.getString("account_id"),
                        rs.getString("operation_id"), rs.getString("idempotency_key"), rs.getString("entity_type"),
                        rs.getString("entity_id"), rs.getString("payload_hash"), rs.getString("status"),
                        rs.getObject("server_version", Long.class), rs.getString("result_json"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class), false),
                tenant, account, key);
        return rows.stream().findFirst();
    }

    private Optional<Receipt> findByOperation(String tenant, String account, String operation) {
        List<Receipt> rows = jdbc.query("""
                select tenant_id, account_id, operation_id, idempotency_key, entity_type, entity_id,
                       payload_hash, status, server_version, result_json, created_at, updated_at
                  from lms_sync_operations where tenant_id = ? and account_id = ? and operation_id = ?
                """, (rs, row) -> new Receipt(rs.getString("tenant_id"), rs.getString("account_id"),
                        rs.getString("operation_id"), rs.getString("idempotency_key"), rs.getString("entity_type"),
                        rs.getString("entity_id"), rs.getString("payload_hash"), rs.getString("status"),
                        rs.getObject("server_version", Long.class), rs.getString("result_json"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class), false),
                tenant, account, operation);
        return rows.stream().findFirst();
    }

    private SyncConflictException conflict(String code, Receipt receipt, Long expected, Long actual, boolean retryable) {
        ConflictPayload payload = new ConflictPayload(code, receipt.operationId(), receipt.idempotencyKey(),
                receipt.entityType(), receipt.entityId(), expected, actual, serverTime(), retryable);
        return new SyncConflictException(payload, code + " for " + receipt.entityType() + "/" + receipt.entityId());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (RuntimeException exception) { throw new IllegalStateException("Unable to encode sync result", exception); }
    }

    private Object canonical(Object value) {
        if (value == null) return Map.of();
        if (value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?>) return value;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            iterable.forEach(item -> list.add(canonical(item)));
            return list;
        }
        return new LinkedHashMap<>(mapper.convertValue(value, Map.class));
    }

    private String required(String value, String field, int max) {
        String normalized = normalize(value, field, max);
        if (normalized == null) throw new BadRequestException(field + " is required");
        return normalized;
    }

    private String normalize(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new BadRequestException(field + " exceeds " + max + " characters");
        return normalized;
    }

    public record Receipt(String tenantId, String accountId, String operationId, String idempotencyKey,
                          String entityType, String entityId, String payloadHash, String status,
                          Long serverVersion, String resultJson, OffsetDateTime createdAt,
                          OffsetDateTime updatedAt, boolean replayed) {
        public Receipt asReplayed() {
            return new Receipt(tenantId, accountId, operationId, idempotencyKey, entityType, entityId, payloadHash,
                    status, serverVersion, resultJson, createdAt, updatedAt, true);
        }
    }
}
