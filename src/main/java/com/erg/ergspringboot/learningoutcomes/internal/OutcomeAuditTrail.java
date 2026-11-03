package com.erg.ergspringboot.learningoutcomes.internal;

import static com.erg.ergspringboot.learningoutcomes.internal.OutcomeValues.now;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes bounded audit records for successful learning-outcome commands. */
@Component
public class OutcomeAuditTrail {
    private final AuditOperations audit;

    public OutcomeAuditTrail(AuditOperations audit) {
        this.audit = audit;
    }

    void record(UserAccessContext access, String action, String resourceType, String resourceId,
                String idempotencyKey, Map<String, Object> details) {
        Map<String, Object> bounded = new LinkedHashMap<>();
        details.forEach((name, value) -> {
            if (bounded.size() < 16 && name != null && value != null) bounded.put(name, String.valueOf(value));
        });
        audit.record(new AuditRecord(access.tenantId(), access.userId(),
                access.currentClassId() == null ? "EDUCATION_UNIT" : "CLASS",
                access.currentClassId() == null ? access.currentEducationUnitId() : access.currentClassId(),
                action, resourceType, resourceId, AuditOutcome.SUCCESS, null, null, null,
                bounded, "learning-outcomes:" + idempotencyKey, now(), null));
    }
}
