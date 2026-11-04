package com.erg.ergspringboot.platform.graphql;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Lexical inspection of a GraphQL request (depth, aliases, introspection, sensitive or oversized
 * variables). It only measures; {@link GraphQlGuardFilter} decides and rejects.
 */
final class GraphQlRequestInspector {

    private static final Set<String> SENSITIVE_VARIABLE_NAMES = Set.of(
            "password", "passwd", "secret", "token", "access_token", "refresh_token",
            "apikey", "api_key", "credential", "credentials", "authorization"
    );

    private GraphQlRequestInspector() {
    }

    static boolean containsIntrospection(String query) {
        return query.contains("__schema") || query.contains("__type");
    }

    static int maxDepth(String query) {
        int depth = 0;
        int max = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inString) {
                escaped = c == '\\' && !escaped;
                if (c == '"' && !escaped) {
                    inString = false;
                }
                if (c != '\\') {
                    escaped = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
                max = Math.max(max, depth);
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return max;
    }

    static int aliasCount(String query) {
        int aliases = 0;
        int parentheses = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inString) {
                escaped = c == '\\' && !escaped;
                if (c == '"' && !escaped) {
                    inString = false;
                }
                if (c != '\\') {
                    escaped = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                parentheses++;
            } else if (c == ')') {
                parentheses = Math.max(0, parentheses - 1);
            } else if (c == ':' && parentheses == 0 && looksLikeAlias(query, i)) {
                aliases++;
            }
        }
        return aliases;
    }

    private static boolean looksLikeAlias(String query, int colonIndex) {
        int before = colonIndex - 1;
        while (before >= 0 && Character.isWhitespace(query.charAt(before))) {
            before--;
        }
        if (before < 0 || !isGraphQlNamePart(query.charAt(before))) {
            return false;
        }
        while (before >= 0 && isGraphQlNamePart(query.charAt(before))) {
            before--;
        }
        int after = colonIndex + 1;
        while (after < query.length() && Character.isWhitespace(query.charAt(after))) {
            after++;
        }
        return after < query.length() && isGraphQlNameStart(query.charAt(after));
    }

    private static boolean isGraphQlNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isGraphQlNamePart(char c) {
        return isGraphQlNameStart(c) || Character.isDigit(c);
    }

    static boolean containsSensitiveVariable(JsonNode variables) {
        if (variables == null || variables.isNull()) {
            return false;
        }
        if (variables.isObject()) {
            for (java.util.Map.Entry<String, JsonNode> entry : variables.properties()) {
                if (isSensitiveName(entry.getKey()) || containsSensitiveVariable(entry.getValue())) {
                    return true;
                }
            }
        } else if (variables.isArray()) {
            for (JsonNode item : variables) {
                if (containsSensitiveVariable(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean containsOversizedVariableArray(JsonNode node, int maxItems) {
        if (node == null || node.isNull()) return false;
        int safeMax = Math.max(1, maxItems);
        if (node.isArray()) {
            if (node.size() > safeMax) return true;
            for (JsonNode child : node) {
                if (containsOversizedVariableArray(child, safeMax)) return true;
            }
        } else if (node.isObject()) {
            for (java.util.Map.Entry<String, JsonNode> entry : node.properties()) {
                if (containsOversizedVariableArray(entry.getValue(), safeMax)) return true;
            }
        }
        return false;
    }

    private static boolean isSensitiveName(String name) {
        String normalized = name == null ? "" : name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace("-", "_")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_VARIABLE_NAMES.contains(normalized)
                || normalized.endsWith("_password")
                || normalized.endsWith("_secret")
                || normalized.endsWith("_token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token");
    }
}
