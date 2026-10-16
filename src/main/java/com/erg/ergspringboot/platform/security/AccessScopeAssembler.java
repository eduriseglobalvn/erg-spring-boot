package com.erg.ergspringboot.platform.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility boundary between legacy principal attributes and {@link AccessScopeSet}. */
public final class AccessScopeAssembler {

    public static final String MANAGED_EDUCATION_UNIT_IDS = "managedEducationUnitIds";
    public static final String MANAGED_CLASS_IDS = "managedClassIds";
    public static final String CURRENT_EDUCATION_UNIT_ID = "currentEducationUnitId";
    public static final String CURRENT_CLASS_ID = "currentClassId";

    private AccessScopeAssembler() {
    }

    public static AccessScopeSet from(ErgPrincipal principal) {
        if (principal == null) return AccessScopeSet.legacy(List.of(), List.of(), null, null);
        if (principal.permissions().contains("*")) {
            return AccessScopeSet.legacy(List.of("*"), List.of("*"), null, null);
        }
        Map<String, Object> attributes = principal.attributes();
        List<String> educationUnits = values(attributes, MANAGED_EDUCATION_UNIT_IDS);
        List<String> classes = values(attributes, MANAGED_CLASS_IDS);
        return AccessScopeSet.legacy(
                educationUnits,
                classes,
                selection(string(attributes, CURRENT_EDUCATION_UNIT_ID), educationUnits),
                selection(string(attributes, CURRENT_CLASS_ID), classes));
    }

    public static Map<String, Object> toLegacyAttributes(AccessScopeSet scopes) {
        AccessScopeSet safeScopes = scopes == null
                ? AccessScopeSet.legacy(List.of(), List.of(), null, null)
                : scopes;
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(MANAGED_EDUCATION_UNIT_IDS,
                safeScopes.managedIds(AccessScopeSet.EDUCATION_UNIT));
        attributes.put(MANAGED_CLASS_IDS, safeScopes.managedIds(AccessScopeSet.CLASS));
        safeScopes.currentId(AccessScopeSet.EDUCATION_UNIT)
                .ifPresent(value -> attributes.put(CURRENT_EDUCATION_UNIT_ID, value));
        safeScopes.currentId(AccessScopeSet.CLASS)
                .ifPresent(value -> attributes.put(CURRENT_CLASS_ID, value));
        return Map.copyOf(attributes);
    }

    public static Map<String, Object> mergeLegacyAttributes(
            Map<String, ?> baseAttributes,
            AccessScopeSet scopes
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseAttributes != null) merged.putAll(baseAttributes);
        merged.putAll(toLegacyAttributes(scopes));
        return Map.copyOf(merged);
    }

    public static List<String> values(ErgPrincipal principal, String key) {
        return values(principal == null ? null : principal.attributes(), key);
    }

    public static String string(ErgPrincipal principal, String key) {
        return string(principal == null ? null : principal.attributes(), key);
    }

    private static List<String> values(Map<String, ?> attributes, String key) {
        if (attributes == null) return List.of();
        Object value = attributes.get(key);
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            collection.stream().map(String::valueOf).forEach(result::add);
            return normalized(result);
        }
        return normalized(value == null ? List.of() : Arrays.asList(value.toString().split(",")));
    }

    private static String string(Map<String, ?> attributes, String key) {
        if (attributes == null) return null;
        Object value = attributes.get(key);
        if (value == null) return null;
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> normalized(Collection<String> values) {
        if (values == null) return List.of();
        return values.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String selection(String requested, List<String> managedIds) {
        if (managedIds == null || managedIds.isEmpty()) return null;
        if (requested != null && (managedIds.contains("*") || managedIds.contains(requested))) return requested;
        return managedIds.getFirst();
    }
}
