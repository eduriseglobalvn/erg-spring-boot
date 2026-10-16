package com.erg.ergspringboot.service.quiz.publish;

import com.erg.ergspringboot.exception.BadRequestException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts editor data-URI media into a signed, content-addressed offline asset manifest. */
final class PortableQuizAssetBundler {

    private static final int MAX_ASSET_COUNT = 500;
    private static final int MAX_ASSET_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TOTAL_ASSET_BYTES = 10 * 1024 * 1024;
    private static final Pattern DATA_URI = Pattern.compile("^data:([^;,]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "image/svg+xml");

    private PortableQuizAssetBundler() {
    }

    static Bundle bundle(Map<String, Object> runtimeQuiz) {
        State state = new State();
        @SuppressWarnings("unchecked")
        Map<String, Object> quiz = (Map<String, Object>) transform(runtimeQuiz, state, 0);
        return new Bundle(quiz, Map.of("assets", List.copyOf(state.assets.values())));
    }

    private static Object transform(Object value, State state, int depth) {
        if (depth > 40) throw new BadRequestException("quiz media nesting is too deep");
        if (value instanceof String text) return transformString(text, state);
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) result.add(transform(item, state, depth + 1));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), transform(entry.getValue(), state, depth + 1));
            }
            return result;
        }
        return value;
    }

    private static Object transformString(String value, State state) {
        Matcher matcher = DATA_URI.matcher(value.trim());
        if (!matcher.matches()) return value;
        String mimeType = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BadRequestException("portable quiz media type is not supported: " + mimeType);
        }

        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("portable quiz media contains invalid base64");
        }
        if (bytes.length == 0 || bytes.length > MAX_ASSET_BYTES) {
            throw new BadRequestException("portable quiz media exceeds the per-asset size limit");
        }

        String sha256 = sha256(bytes);
        String assetId = "sha256-" + sha256;
        if (!state.assets.containsKey(assetId)) {
            if (state.assets.size() >= MAX_ASSET_COUNT || state.totalBytes + bytes.length > MAX_TOTAL_ASSET_BYTES) {
                throw new BadRequestException("portable quiz media exceeds the package asset limit");
            }
            state.totalBytes += bytes.length;
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("id", assetId);
            asset.put("mimeType", mimeType);
            asset.put("contentBase64", Base64.getEncoder().encodeToString(bytes));
            asset.put("sha256", "sha256:" + sha256);
            asset.put("sizeBytes", bytes.length);
            state.assets.put(assetId, asset);
        }
        return "asset://" + assetId;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Bundle(Map<String, Object> runtimeQuiz, Map<String, Object> assetsManifest) {
    }

    private static final class State {
        private final Map<String, Map<String, Object>> assets = new LinkedHashMap<>();
        private int totalBytes;
    }
}
