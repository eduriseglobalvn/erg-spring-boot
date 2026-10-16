package com.erg.ergspringboot.enterpriseconnectors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure, bounded policy for secrets and field mappings. */
public final class EnterpriseConnectorPolicy {
    public static final int MAX_MAPPING_ENTRIES = 32;
    public static final int MAX_MAPPING_FIELD = 96;

    private EnterpriseConnectorPolicy() {
    }

    public static String secretReference(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.startsWith("secret://") || normalized.length() > 255
                || normalized.length() <= "secret://".length()
                || normalized.contains("\n") || normalized.contains("\r")
                || normalized.contains("=") || normalized.contains(" ")) {
            throw new IllegalArgumentException("secretRef must be a server-side secret:// reference");
        }
        return normalized;
    }

    public static Mapping mapping(String version, Map<String, String> raw) {
        String normalizedVersion = required(version, "mappingVersion");
        if (raw == null || raw.isEmpty() || raw.size() > MAX_MAPPING_ENTRIES) {
            throw new IllegalArgumentException("fieldMapping must contain 1-32 entries");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        raw.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                .forEach(entry -> {
                    String key = required(entry.getKey(), "mapping field");
                    String value = required(entry.getValue(), "mapping target");
                    if (key.length() > MAX_MAPPING_FIELD || value.length() > MAX_MAPPING_FIELD) {
                        throw new IllegalArgumentException("mapping field is too long");
                    }
                    normalized.put(key, value);
                });
        List<String> canonical = new ArrayList<>();
        normalized.forEach((key, value) -> canonical.add(key + "=" + value));
        return new Mapping(normalizedVersion, sha256(String.join("\u001f", canonical)), List.copyOf(normalized.keySet()));
    }

    public record Mapping(String version, String hash, List<String> fields) {
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
