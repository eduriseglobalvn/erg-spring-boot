package com.erg.ergspringboot.platform.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import tools.jackson.databind.JavaType;

public record CacheDescriptor<T>(
        String name,
        JavaType valueType,
        Duration l1Ttl,
        Duration l2Ttl,
        long l1MaximumWeightBytes,
        int maximumSerializedBytes,
        boolean tenantRequired,
        boolean cacheNulls
) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,96}");

    public CacheDescriptor {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid cache name: " + name);
        }
        Objects.requireNonNull(valueType, "valueType");
        requirePositive(l1Ttl, "l1Ttl");
        requirePositive(l2Ttl, "l2Ttl");
        if (l1MaximumWeightBytes < 1) {
            throw new IllegalArgumentException("l1MaximumWeightBytes must be positive");
        }
        if (maximumSerializedBytes < 1 || maximumSerializedBytes > 1024 * 1024) {
            throw new IllegalArgumentException("maximumSerializedBytes must be between 1 and 1048576");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

