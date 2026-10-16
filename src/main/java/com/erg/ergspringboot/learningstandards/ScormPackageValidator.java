package com.erg.ergspringboot.learningstandards;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, XXE-free validation of the SCORM manifest contract. */
public final class ScormPackageValidator {
    public static final int MAX_MANIFEST_CHARS = 512 * 1024;
    private static final Pattern IDENTIFIER = Pattern.compile(
            "(?i)<manifest\\b[^>]*\\bidentifier\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern SCHEMA_VERSION = Pattern.compile(
            "(?i)<schemaversion\\b[^>]*>\\s*([^<]+?)\\s*</schemaversion\\s*>");

    private ScormPackageValidator() {
    }

    public static Result validate(String standard, String manifestXml, String launchPath) {
        String kind = required(standard, "standard").toUpperCase(Locale.ROOT);
        if (!kind.equals("SCORM_12") && !kind.equals("SCORM_2004")) {
            return Result.invalid("unsupported SCORM standard");
        }
        if (manifestXml == null || manifestXml.isBlank() || manifestXml.length() > MAX_MANIFEST_CHARS) {
            return Result.invalid("manifest is missing or too large");
        }
        String lower = manifestXml.toLowerCase(Locale.ROOT);
        if (lower.contains("<!doctype") || lower.contains("<!entity") || !lower.contains("<manifest")) {
            return Result.invalid("manifest is not a safe SCORM manifest");
        }
        Matcher identifier = IDENTIFIER.matcher(manifestXml);
        if (!identifier.find()) return Result.invalid("manifest identifier is missing");
        Matcher schema = SCHEMA_VERSION.matcher(manifestXml);
        if (!schema.find()) return Result.invalid("SCORM schemaVersion is missing");
        String schemaVersion = schema.group(1).trim();
        if (kind.equals("SCORM_12") && !schemaVersion.startsWith("1.2")) {
            return Result.invalid("manifest schemaVersion is not SCORM 1.2");
        }
        if (kind.equals("SCORM_2004") && !schemaVersion.startsWith("2004")) {
            return Result.invalid("manifest schemaVersion is not SCORM 2004");
        }
        if (!safeLaunchPath(launchPath)) return Result.invalid("launch path is not safe");
        return new Result(true, null, identifier.group(1).trim(), schemaVersion);
    }

    public static boolean safeArtifactKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255) return false;
        if (key.startsWith("/") || key.contains("\\") || key.contains("..")) return false;
        return key.chars().noneMatch(Character::isISOControl);
    }

    public static boolean safeLaunchPath(String path) {
        if (path == null || path.isBlank() || path.length() > 255 || path.contains("..") || path.contains("\\")) {
            return false;
        }
        try {
            URI uri = URI.create(path);
            String uriPath = uri.getPath();
            return uri.getScheme() == null && uri.getHost() == null && uriPath != null
                    && !uriPath.isBlank() && !uriPath.startsWith("/")
                    && path.chars().noneMatch(Character::isISOControl);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public record Result(boolean valid, String reason, String identifier, String schemaVersion) {
        static Result invalid(String reason) {
            return new Result(false, reason, null, null);
        }
    }
}
