package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places or releases a legal hold on one audit event (C11). Requires {@code audit.manage};
 * the tenant always comes from the authenticated access context. The change itself is
 * audited, and the database trigger (V121) permits no other column to change.
 */
@Service
public class AuditLegalHoldService {
    static final String PERMISSION = "audit.manage";

    private final AuditEventRepository repository;
    private final ErgPolicyService policy;
    private final AuditWriteService writes;

    public AuditLegalHoldService(AuditEventRepository repository, ErgPolicyService policy, AuditWriteService writes) {
        this.repository = repository;
        this.policy = policy;
        this.writes = writes;
    }

    @Transactional("postgresTransactionManager")
    public void setLegalHold(UserAccessContext access, String eventId, boolean legalHold, String requestId) {
        if (!currentPrincipalHas(access)) {
            throw new ForbiddenException("missing permission " + PERMISSION);
        }
        if (eventId == null || eventId.isBlank()) {
            throw new ResourceNotFoundException("Audit event not found");
        }
        if (repository.updateLegalHold(access.tenantId(), eventId.trim(), legalHold) == 0) {
            throw new ResourceNotFoundException("Audit event not found");
        }
        String key = requestId == null || requestId.isBlank()
                ? null : "audit-legal-hold:" + eventId.trim() + ":" + legalHold + ":" + requestId;
        writes.append(new AuditRecord(
                access.tenantId(), access.userId(), "TENANT", null,
                legalHold ? "AUDIT_LEGAL_HOLD_SET" : "AUDIT_LEGAL_HOLD_RELEASED", "AUDIT_EVENT", eventId.trim(),
                AuditOutcome.SUCCESS, requestId, requestId, null, Map.of("legalHold", legalHold), key,
                OffsetDateTime.now(ZoneOffset.UTC), null));
    }

    private boolean currentPrincipalHas(UserAccessContext access) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof ErgPrincipal principal
                && access.userId().equals(principal.userId())
                && policy.isAllowed(principal, PERMISSION, null, access.tenantId());
    }
}
