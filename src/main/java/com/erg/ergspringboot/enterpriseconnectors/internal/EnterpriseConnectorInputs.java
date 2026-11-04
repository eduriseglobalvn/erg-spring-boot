package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorPolicy;
import com.erg.ergspringboot.exception.BadRequestException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Input validation and normalization for enterprise connector commands. */
final class EnterpriseConnectorInputs {
    static final int MAX_SAMPLE_IDS = 20;

    private EnterpriseConnectorInputs() {
    }

    static EnterpriseConnectorPolicy.Mapping mapping(String version, Map<String, String> fieldMapping) {
        try {
            return EnterpriseConnectorPolicy.mapping(version, fieldMapping);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    static String secret(String value) {
        try {
            return EnterpriseConnectorPolicy.secretReference(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    static List<String> ids(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 100) throw new BadRequestException("externalIds must contain 1-100 values");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        values.forEach(value -> unique.add(required(value, "externalId", 96)));
        return List.copyOf(unique);
    }

    static String joinSample(List<String> values) {
        if (values == null) return null;
        return String.join(",", values.stream().filter(Objects::nonNull).map(value -> required(value, "sampleExternalId", 96))
                .limit(MAX_SAMPLE_IDS).toList());
    }

    static String key(String value) {
        return required(value, "idempotencyKey", 255);
    }

    static String required(String value, String name, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new BadRequestException(name + " is required");
        if (normalized.length() > max) throw new BadRequestException(name + " is too long");
        return normalized;
    }

    static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return required(value, "value", max);
    }
}
