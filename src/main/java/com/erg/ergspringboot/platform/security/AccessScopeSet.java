package com.erg.ergspringboot.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable, extensible authorization scope value object keyed by normalized scope type. */
public final class AccessScopeSet {

    public static final String EDUCATION_UNIT = "EDUCATION_UNIT";
    public static final String CLASS = "CLASS";
    public static final String CAMPUS = "CAMPUS";
    public static final String PROGRAM = "PROGRAM";
    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String COURSE = "COURSE";

    private final Map<String, List<String>> managedByType;
    private final Map<String, String> currentByType;
    private final String fingerprint;

    private AccessScopeSet(Map<String, List<String>> managedByType, Map<String, String> currentByType) {
        this.managedByType = Map.copyOf(managedByType);
        this.currentByType = Map.copyOf(currentByType);
        this.fingerprint = fingerprint(managedByType, currentByType);
    }

    public static AccessScopeSet of(
            Map<String, ? extends Collection<String>> managedByType,
            Map<String, String> currentByType
    ) {
        Map<String, List<String>> managed = new TreeMap<>();
        if (managedByType != null) {
            managedByType.forEach((rawType, rawIds) -> {
                String type = normalizeType(rawType);
                List<String> ids = normalizeIds(rawIds);
                if (!ids.isEmpty()) {
                    managed.merge(type, ids, AccessScopeSet::mergeIds);
                }
            });
        }
        Map<String, String> current = new TreeMap<>();
        if (currentByType != null) {
            currentByType.forEach((rawType, rawId) -> {
                String type = normalizeType(rawType);
                String id = normalizeId(rawId);
                if (id == null) {
                    return;
                }
                List<String> ids = managed.getOrDefault(type, List.of());
                if (!ids.contains("*") && !ids.contains(id)) {
                    throw new IllegalArgumentException(type + " current selection is outside managed scope");
                }
                current.put(type, id);
            });
        }
        return new AccessScopeSet(managed, current);
    }

    public static AccessScopeSet legacy(
            Collection<String> educationUnitIds,
            Collection<String> classIds,
            String currentEducationUnitId,
            String currentClassId
    ) {
        Map<String, Collection<String>> managed = new LinkedHashMap<>();
        managed.put(EDUCATION_UNIT, educationUnitIds == null ? List.of() : educationUnitIds);
        managed.put(CLASS, classIds == null ? List.of() : classIds);
        Map<String, String> current = new LinkedHashMap<>();
        if (normalizeId(currentEducationUnitId) != null) current.put(EDUCATION_UNIT, currentEducationUnitId);
        if (normalizeId(currentClassId) != null) current.put(CLASS, currentClassId);
        return of(managed, current);
    }

    public List<String> managedIds(String scopeType) {
        return managedByType.getOrDefault(normalizeType(scopeType), List.of());
    }

    public Optional<String> currentId(String scopeType) {
        return Optional.ofNullable(currentByType.get(normalizeType(scopeType)));
    }

    public boolean canAccess(String scopeType, String scopeId) {
        String id = normalizeId(scopeId);
        if (id == null) return true;
        List<String> managed = managedIds(scopeType);
        return managed.contains("*") || managed.contains(id);
    }

    public boolean hasManagedScope(String scopeType) {
        return !managedIds(scopeType).isEmpty();
    }

    public Map<String, List<String>> managedByType() {
        return managedByType;
    }

    public String fingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AccessScopeSet that
                && managedByType.equals(that.managedByType)
                && currentByType.equals(that.currentByType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(managedByType, currentByType);
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("scope type must not be blank");
        return value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeId(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> normalizeIds(Collection<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> normalized = values.stream()
                .map(AccessScopeSet::normalizeId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return normalized.contains("*") ? List.of("*") : normalized;
    }

    private static List<String> mergeIds(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>(left);
        merged.addAll(right);
        return normalizeIds(merged);
    }

    private static String fingerprint(Map<String, List<String>> managed, Map<String, String> current) {
        StringBuilder canonical = new StringBuilder();
        managed.forEach((type, ids) -> canonical.append(type).append('=').append(String.join(",", ids))
                .append('@').append(current.getOrDefault(type, "")).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
