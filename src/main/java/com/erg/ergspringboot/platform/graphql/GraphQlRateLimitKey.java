package com.erg.ergspringboot.platform.graphql;

public record GraphQlRateLimitKey(
        String tenantId,
        String userId,
        String operationName
) {
}
