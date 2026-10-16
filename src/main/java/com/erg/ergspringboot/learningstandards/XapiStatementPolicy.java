package com.erg.ergspringboot.learningstandards;

import java.net.URI;
import java.util.regex.Pattern;

/** Pure xAPI boundary rules; raw statement bodies never enter persistence. */
public final class XapiStatementPolicy {
    public static final int MAX_FIELD_LENGTH = 512;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private XapiStatementPolicy() {
    }

    public static Validation validate(String statementId, String actorId, String verb,
                                      String objectId, String payloadSha256) {
        if (!bounded(statementId, 96) || !bounded(actorId, MAX_FIELD_LENGTH)
                || !bounded(verb, MAX_FIELD_LENGTH) || !bounded(objectId, MAX_FIELD_LENGTH)
                || !SHA256.matcher(payloadSha256 == null ? "" : payloadSha256).matches()) {
            return Validation.invalid("xAPI statement fields are invalid");
        }
        if (!absoluteHttpUri(verb) || !absoluteHttpUri(objectId)) {
            return Validation.invalid("xAPI verb and object must be absolute http(s) URIs");
        }
        return new Validation(true, null);
    }

    public static String actorHash(String actorId) {
        return Hashing.sha256(actorId);
    }

    private static boolean bounded(String value, int max) {
        return value != null && !value.isBlank() && value.trim().length() <= max;
    }

    private static boolean absoluteHttpUri(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record Validation(boolean valid, String reason) {
        static Validation invalid(String reason) {
            return new Validation(false, reason);
        }
    }
}
