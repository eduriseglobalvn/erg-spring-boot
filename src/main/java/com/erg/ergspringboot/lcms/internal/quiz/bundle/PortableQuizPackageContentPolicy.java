package com.erg.ergspringboot.lcms.internal.quiz.bundle;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Content policy for a portable package snapshot: asset bounds, secret/answer-key
 * stripping and external-reference detection. Lineage and signing checks stay in
 * {@link PortableQuizBundleService}.
 */
final class PortableQuizPackageContentPolicy {

    private static final int MAX_ASSET_NODES = 10_000;
    private static final long MAX_ASSET_BYTES = 1_073_741_824L;

    private PortableQuizPackageContentPolicy() {
    }

    static boolean assetsWithinBounds(Object value) {
        return inspectAssets(value).valid();
    }

    static Map<String, Object> sanitizedRuntime(Object value, boolean stripAnswerKey) {
        return copyAndSanitize(value, stripAnswerKey);
    }

    static boolean containsExternalAssetReference(Object value, String field) {
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            return ("url".equalsIgnoreCase(field) || "src".equalsIgnoreCase(field))
                    && (normalized.startsWith("https://") || normalized.startsWith("http://"));
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (containsExternalAssetReference(entry.getValue(), String.valueOf(entry.getKey()))) return true;
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (containsExternalAssetReference(item, field)) return true;
            }
        }
        return false;
    }

    private static AssetBounds inspectAssets(Object value) {
        AssetBounds bounds = new AssetBounds();
        inspectAssets(value, bounds, 0);
        return bounds;
    }

    @SuppressWarnings("unchecked")
    private static void inspectAssets(Object value, AssetBounds bounds, int depth) {
        if (!bounds.valid() || value == null || depth > 32) return;
        if (value instanceof Map<?, ?> map) {
            bounds.nodes += map.size();
            if (map.size() > MAX_ASSET_NODES || bounds.nodes > MAX_ASSET_NODES) {
                bounds.valid = false;
                return;
            }
            Object bytes = map.get("byteSize") != null ? map.get("byteSize") : map.get("sizeBytes");
            if (bytes instanceof Number number) {
                bounds.bytes += Math.max(0, number.longValue());
                if (bounds.bytes > MAX_ASSET_BYTES) bounds.valid = false;
            }
            for (Object child : map.values()) inspectAssets(child, bounds, depth + 1);
        } else if (value instanceof Collection<?> collection) {
            bounds.nodes += collection.size();
            if (bounds.nodes > MAX_ASSET_NODES) {
                bounds.valid = false;
                return;
            }
            for (Object child : collection) inspectAssets(child, bounds, depth + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyAndSanitize(Object value, boolean stripAnswerKey) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key) || (stripAnswerKey && isAnswerKey(key))) continue;
            copy.put(key, sanitizeValue(entry.getValue(), stripAnswerKey));
        }
        return copy;
    }

    private static Object sanitizeValue(Object value, boolean stripAnswerKey) {
        if (value instanceof Map<?, ?>) return copyAndSanitize(value, stripAnswerKey);
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> sanitizeValue(item, stripAnswerKey)).toList();
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.equals("accesstoken")
                || normalized.equals("refreshtoken")
                || normalized.equals("password")
                || normalized.equals("clientsecret")
                || normalized.equals("apikey")
                || normalized.equals("privatekey")
                || normalized.equals("credential")
                || normalized.equals("credentials");
    }

    private static boolean isAnswerKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.equals("answerkey") || normalized.equals("answerkeys");
    }

    static String safeDownloadUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.startsWith("https://") ? normalized : null;
    }

    private static final class AssetBounds {
        private int nodes;
        private long bytes;
        private boolean valid = true;

        private boolean valid() {
            return valid && nodes <= MAX_ASSET_NODES && bytes <= MAX_ASSET_BYTES;
        }
    }
}
