package com.erg.ergspringboot.platform.graphql;

import com.erg.ergspringboot.common.response.FieldErrorDetail;
import com.erg.ergspringboot.exception.ApiException;
import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Maps resolver failures to the same stable codes used by REST without leaking internal messages. */
@Component
public class ErgGraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(ErgGraphQlExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        GraphQlRequestContext context = environment.getGraphQlContext().get(GraphQlRequestContext.CONTEXT_KEY);
        String requestId = context == null ? null : context.requestId();
        Descriptor descriptor = describe(exception, requestId);
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", descriptor.code());
        extensions.put("status", descriptor.status());
        if (requestId != null && !requestId.isBlank()) extensions.put("request_id", requestId);
        if (!descriptor.details().isEmpty()) extensions.put("details", descriptor.details());
        if (descriptor.internal()) {
            log.error("Unhandled GraphQL resolver exception [requestId={}]", requestId, exception);
        }
        return GraphqlErrorBuilder.newError(environment)
                .message(descriptor.message())
                .errorType(descriptor.classification())
                .extensions(extensions)
                .build();
    }

    Descriptor describe(Throwable throwable, String requestId) {
        Throwable exception = unwrap(throwable);
        if (exception instanceof ApiException api) {
            return new Descriptor(api.getCode(), api.getMessage(), api.getStatus().value(),
                    ErrorType.DataFetchingException, List.of(), false);
        }
        if (exception instanceof ConstraintViolationException validation) {
            List<FieldErrorDetail> details = validation.getConstraintViolations().stream()
                    .sorted(Comparator.comparing(item -> item.getPropertyPath().toString()))
                    .map(item -> new FieldErrorDetail(
                            item.getPropertyPath().toString(), "ConstraintViolation",
                            "ConstraintViolation", item.getMessage()))
                    .toList();
            return new Descriptor("VALIDATION_ERROR", "validation failed", 422,
                    ErrorType.ValidationError, details, false);
        }
        if (exception instanceof AccessDeniedException) {
            return new Descriptor("ACCESS_DENIED", "You do not have permission to access this resource", 403,
                    ErrorType.DataFetchingException, List.of(), false);
        }
        if (exception instanceof AuthenticationCredentialsNotFoundException) {
            return new Descriptor("AUTHENTICATION_REQUIRED", "Authentication is required", 401,
                    ErrorType.DataFetchingException, List.of(), false);
        }
        return new Descriptor("INTERNAL_SERVER_ERROR", "Unexpected server error", 500,
                ErrorType.DataFetchingException, List.of(), true);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.lang.reflect.UndeclaredThrowableException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    record Descriptor(
            String code,
            String message,
            int status,
            ErrorClassification classification,
            List<FieldErrorDetail> details,
            boolean internal
    ) {}
}
