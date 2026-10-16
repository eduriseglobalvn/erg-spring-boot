package com.erg.ergspringboot.platform.security;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

/** Central policy for legacy namespace acceptance; it is not an authorization decision. */
public final class LegacyNamespacePolicy {
    private static final Set<String> LEGACY = Set.of("lcms", "lms", "elearning", "crm");
    private static final LocalDate DEFAULT_SUNSET = LocalDate.of(2026, 10, 31);
    private static final Map<String, LegacyAlias> ALIASES = Map.of(
            "lcms", new LegacyAlias("lcms", "ADMIN", DEFAULT_SUNSET, "/admin"),
            "lms", new LegacyAlias("lms", "TEACHER", DEFAULT_SUNSET, "/teacher"),
            "elearning", new LegacyAlias("elearning", "STUDENT", DEFAULT_SUNSET, "/student"),
            "crm", new LegacyAlias("crm", "CRM", DEFAULT_SUNSET, "https://crm.erg.edu.vn")
    );

    private LegacyNamespacePolicy() {}

    public static boolean isLegacyNamespace(String value) {
        return value != null && LEGACY.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the migration metadata for a legacy namespace. This is compatibility metadata,
     * never an authorization grant.
     */
    public static Optional<LegacyAlias> alias(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ALIASES.get(value.trim().toLowerCase(Locale.ROOT)));
    }

    public static List<LegacyAlias> aliases() {
        return List.copyOf(ALIASES.values());
    }

    public static boolean isCompatibilityActive(String value, LocalDate today) {
        return alias(value).map(metadata -> today == null || !today.isAfter(metadata.sunset())).orElse(false);
    }

    public static boolean isPublicWorkspaceVocabulary(String value) {
        if (value == null) return false;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN", "TEACHER", "STUDENT", "CRM" -> true;
            default -> false;
        };
    }

    public record LegacyAlias(
            String alias,
            String canonicalWorkspace,
            LocalDate sunset,
            String successor
    ) {
    }
}
