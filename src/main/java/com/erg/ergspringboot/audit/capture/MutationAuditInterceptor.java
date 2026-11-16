package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.capture.AuditCaptureRules.Decision;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Records every authenticated REST write (POST/PUT/PATCH/DELETE) with its outcome. GraphQL is handled by
 * {@link GraphQlMutationAuditInstrumentation}; 403s raised in the security filter chain by
 * {@link AuditDeniedRecorder}.
 */
@Component
public class MutationAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(MutationAuditInterceptor.class);
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuditOutboxWriter writer;

    public MutationAuditInterceptor(AuditOutboxWriter writer) {
        this.writer = writer;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            capture(request, response, handler, ex);
        } catch (RuntimeException failure) {
            log.warn("Mutation audit capture failed path={}", request.getRequestURI(), failure);
        }
    }

    private void capture(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (READ_METHODS.contains(request.getMethod()) || !(handler instanceof HandlerMethod method)) return;
        if (request.getRequestURI().endsWith("/graphql")) return;
        ErgPrincipal principal = SecurityUtils.principal();
        if (principal == null) return;
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pattern = matched instanceof String text ? text : request.getRequestURI();
        if (AuditCaptureRules.classifyRest(pattern) != Decision.CAPTURE) return;
        AuditOutcome outcome = AuditCaptureRules.outcomeOf(response.getStatus(), ex);
        if (outcome == null) return;
        writer.write(AuditOutboxWriter.httpRecord(request, principal, pattern, outcome, response.getStatus(),
                method.getBeanType().getSimpleName() + "#" + method.getMethod().getName()));
    }
}
