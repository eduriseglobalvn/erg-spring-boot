package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.capture.AuditCaptureRules.Decision;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records a 403 raised in the security filter chain (it never reaches {@link MutationAuditInterceptor}).
 * Service-level {@code ForbiddenException}/{@code AccessDeniedException} already end as a 403 response
 * seen by the interceptor, so the exception handler needs no hook. Called from the AccessDeniedHandler.
 */
@Component
public class AuditDeniedRecorder {
    private static final Logger log = LoggerFactory.getLogger(AuditDeniedRecorder.class);
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuditOutboxWriter writer;

    public AuditDeniedRecorder(AuditOutboxWriter writer) {
        this.writer = writer;
    }

    /** shortcut: denied reads (GET) are not recorded, only writes; widen if reads must be traced too. */
    public void record(HttpServletRequest request) {
        try {
            ErgPrincipal principal = SecurityUtils.principal();
            if (principal == null || READ_METHODS.contains(request.getMethod())) return;
            String path = request.getRequestURI();
            if (AuditCaptureRules.classifyRest(path) != Decision.CAPTURE || path.endsWith("/graphql")) return;
            writer.write(AuditOutboxWriter.httpRecord(request, principal, path, AuditOutcome.DENIED, 403, null));
        } catch (RuntimeException failure) {
            log.warn("Denied audit capture failed path={}", request.getRequestURI(), failure);
        }
    }
}
