package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.capture.AuditCaptureRules.Decision;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records each top-level GraphQL mutation field (never queries, even though both arrive by POST).
 * Spring Boot applies every {@code Instrumentation} bean, so no registration is needed.
 */
@Component
public class GraphQlMutationAuditInstrumentation extends SimplePerformantInstrumentation {
    private final AuditOutboxWriter writer;

    public GraphQlMutationAuditInstrumentation(AuditOutboxWriter writer) {
        this.writer = writer;
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(
            DataFetcher<?> fetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        ExecutionStepInfo step = parameters.getExecutionStepInfo();
        boolean rootField = step.hasParent() && !step.getParent().hasParent();
        if (parameters.isTrivialDataFetcher() || !rootField
                || parameters.getEnvironment().getOperationDefinition().getOperation() != OperationDefinition.Operation.MUTATION) {
            return fetcher;
        }
        String field = step.getFieldDefinition().getName();
        if (AuditCaptureRules.classifyMutation(field) != Decision.CAPTURE) return fetcher;
        return environment -> {
            // Read on the request thread: async completions have no security context or request.
            ErgPrincipal principal = SecurityUtils.principal();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("operation", field);
            details.put("arguments", environment.getArguments());
            String requestId = requestDetails(details);
            Object value;
            try {
                value = fetcher.get(environment);
            } catch (Exception failure) {
                record(principal, field, outcomeOf(failure), requestId, details);
                throw failure;
            }
            if (value instanceof CompletionStage<?> stage) {
                return stage.whenComplete((result, failure) ->
                        record(principal, field, failure == null ? AuditOutcome.SUCCESS : outcomeOf(failure), requestId, details));
            }
            record(principal, field, AuditOutcome.SUCCESS, requestId, details);
            return value;
        };
    }

    private void record(ErgPrincipal principal, String field, AuditOutcome outcome, String requestId, Map<String, Object> details) {
        if (principal == null || outcome == null) return;
        writer.write(AuditOutboxWriter.record(principal, field, "GRAPHQL", outcome, requestId, details));
    }

    private static AuditOutcome outcomeOf(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ForbiddenException || cause instanceof AccessDeniedException) return AuditOutcome.DENIED;
        return AuditOutcome.FAILURE;
    }

    private static String requestDetails(Map<String, Object> details) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            details.put("method", request.getMethod());
            details.put("path", request.getRequestURI());
            details.put("ip", request.getRemoteAddr());
            details.put("userAgent", request.getHeader("User-Agent"));
            return RequestCorrelation.current(request);
        }
        return null;
    }
}
