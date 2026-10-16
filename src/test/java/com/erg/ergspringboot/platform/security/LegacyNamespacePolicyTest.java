package com.erg.ergspringboot.platform.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LegacyNamespacePolicyTest {
    @Test
    void acceptsLegacyOnlyAsCompatibilityNamespace() {
        assertTrue(LegacyNamespacePolicy.isLegacyNamespace("lcms"));
        assertTrue(LegacyNamespacePolicy.isLegacyNamespace("ELEARNING"));
        assertFalse(LegacyNamespacePolicy.isLegacyNamespace("author"));
    }

    @Test
    void recognizesCanonicalPublicWorkspaceVocabulary() {
        assertTrue(LegacyNamespacePolicy.isPublicWorkspaceVocabulary("ADMIN"));
        assertTrue(LegacyNamespacePolicy.isPublicWorkspaceVocabulary("student"));
        assertFalse(LegacyNamespacePolicy.isPublicWorkspaceVocabulary("lcms"));
    }

    @Test
    void exposesExplicitCompatibilityAliasSunsetWithoutTurningAliasIntoAuthorization() {
        LegacyNamespacePolicy.LegacyAlias alias = LegacyNamespacePolicy.alias("LCMS").orElseThrow();

        assertTrue(alias.canonicalWorkspace().equals("ADMIN"));
        assertTrue(LegacyNamespacePolicy.isCompatibilityActive("lcms", LocalDate.of(2026, 10, 31)));
        assertFalse(LegacyNamespacePolicy.isCompatibilityActive("lcms", LocalDate.of(2026, 11, 1)));
        assertFalse(LegacyNamespacePolicy.isCompatibilityActive("admin", LocalDate.of(2026, 8, 7)));
    }
}
