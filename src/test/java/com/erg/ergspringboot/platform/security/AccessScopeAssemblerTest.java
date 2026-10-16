package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccessScopeAssemblerTest {

    @Test
    void readsLegacyPrincipalAttributesIntoTheCanonicalScopeSet() {
        ErgPrincipal principal = new ErgPrincipal(
                "user-1", "session-1", "tenant-1", List.of("teacher"), List.of("lms.read"),
                List.of(), List.of("lms"), "teacher", "managed",
                Map.of(
                        "managedEducationUnitIds", List.of("unit-2", "unit-1"),
                        "managedClassIds", "class-2,class-1",
                        "currentEducationUnitId", "unit-2",
                        "currentClassId", "class-1"));

        AccessScopeSet scopes = AccessScopeAssembler.from(principal);

        assertThat(scopes.managedIds(AccessScopeSet.EDUCATION_UNIT)).containsExactly("unit-1", "unit-2");
        assertThat(scopes.managedIds(AccessScopeSet.CLASS)).containsExactly("class-1", "class-2");
        assertThat(scopes.currentId(AccessScopeSet.EDUCATION_UNIT)).contains("unit-2");
        assertThat(scopes.currentId(AccessScopeSet.CLASS)).contains("class-1");
    }

    @Test
    void wildcardPermissionProducesWildcardLegacyScopes() {
        ErgPrincipal principal = new ErgPrincipal(
                "admin", "session-1", "tenant-1", List.of("admin"), List.of("*"),
                List.of(), List.of("*"), "admin", "admin", Map.of());

        AccessScopeSet scopes = AccessScopeAssembler.from(principal);

        assertThat(scopes.managedIds(AccessScopeSet.EDUCATION_UNIT)).containsExactly("*");
        assertThat(scopes.managedIds(AccessScopeSet.CLASS)).containsExactly("*");
    }

    @Test
    void writesLegacyCompatibilityAttributesFromCanonicalScopes() {
        AccessScopeSet scopes = AccessScopeSet.legacy(
                List.of("unit-1"), List.of("class-1"), "unit-1", "class-1");

        assertThat(AccessScopeAssembler.toLegacyAttributes(scopes)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "managedEducationUnitIds", List.of("unit-1"),
                "managedClassIds", List.of("class-1"),
                "currentEducationUnitId", "unit-1",
                "currentClassId", "class-1"));
    }
}
