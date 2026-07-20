package com.erg.ergspringboot.platform.cache;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.cache.interceptor.SimpleKey;
import tools.jackson.databind.ObjectMapper;

public final class CacheKeyFactory {

    private final ObjectMapper mapper;

    public CacheKeyFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String businessKey(
            String tenantId,
            CacheDescriptor<?> descriptor,
            Object sourceKey,
            long generation
    ) {
        if (descriptor.tenantRequired() && (tenantId == null || tenantId.isBlank())) {
            throw new IllegalArgumentException("tenantId is required for cache " + descriptor.name());
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        String tenantHash = shortHash(tenantId == null ? "global" : tenantId.trim());
        String keyHash = fullHash(serializeCanonical(canonical(sourceKey)));
        return "erg:cache:v2:{" + tenantHash + "}:" + descriptor.name() + ":g" + generation + ":" + keyHash;
    }

    private byte[] serializeCanonical(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Cache key cannot be canonicalized", exception);
        }
    }

    private Object canonical(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof SimpleKey simpleKey) {
            return simpleKey.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
            return sorted;
        }
        if (value instanceof java.util.Set<?> set) {
            List<Object> items = new ArrayList<>();
            set.forEach(item -> items.add(canonical(item)));
            items.sort(Comparator.comparing(this::stableString));
            return items;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonical).toList();
        }
        if (value instanceof Collection<?> collection) {
            List<Object> items = collection.stream().map(this::canonical).collect(java.util.stream.Collectors.toList());
            items.sort(Comparator.comparing(this::stableString));
            return items;
        }
        if (value.getClass().isArray()) {
            List<Object> items = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                items.add(canonical(Array.get(value, index)));
            }
            return items;
        }
        return new LinkedHashMap<>(mapper.convertValue(value, Map.class));
    }

    private String stableString(Object value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(serializeCanonical(value));
    }

    private String shortHash(String value) {
        return fullHash(value.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    private String fullHash(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
