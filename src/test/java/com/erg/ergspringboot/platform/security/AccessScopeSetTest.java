package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccessScopeSetTest {

    @Test
    void normalizesScopeTypesAndProducesOrderIndependentFingerprint() {
        AccessScopeSet first = AccessScopeSet.of(
                Map.of("class", List.of("class-b", "class-a", "class-a"),
                        "education_unit", List.of("school-a")),
                Map.of("CLASS", "class-a"));
        Map<String, List<String>> reordered = new LinkedHashMap<>();
        reordered.put("EDUCATION_UNIT", List.of("school-a"));
        reordered.put("CLASS", List.of("class-a", "class-b"));
        AccessScopeSet second = AccessScopeSet.of(reordered, Map.of("class", "class-a"));

        assertThat(first.managedIds("CLASS")).containsExactly("class-a", "class-b");
        assertThat(first.currentId("class")).contains("class-a");
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint()).hasSize(64);
    }

    @Test
    void supportsNewScopeTypesWithoutChangingTheValueObjectShape() {
        AccessScopeSet scopes = AccessScopeSet.of(
                Map.of("campus", List.of("campus-1"), "course", List.of("*")),
                Map.of("campus", "campus-1"));

        assertThat(scopes.canAccess("CAMPUS", "campus-1")).isTrue();
        assertThat(scopes.canAccess("CAMPUS", "campus-2")).isFalse();
        assertThat(scopes.canAccess("COURSE", "any-course")).isTrue();
    }

    @Test
    void snapshotsInputsAndRejectsCurrentSelectionOutsideManagedScope() {
        List<String> mutable = new java.util.ArrayList<>(List.of("class-1"));
        AccessScopeSet scopes = AccessScopeSet.of(Map.of("class", mutable), Map.of());
        mutable.add("class-2");

        assertThat(scopes.managedIds("class")).containsExactly("class-1");
        assertThatThrownBy(() -> AccessScopeSet.of(
                Map.of("class", List.of("class-1")), Map.of("class", "class-2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLASS");
    }

    @Test
    void legacyUserAccessContextExposesTheSameScopesAndStableFingerprint() {
        UserAccessContext access = new UserAccessContext(
                "user-1", "tenant-1", List.of("school-1"), List.of("class-1"), "school-1", "class-1");

        assertThat(access.scopeSet().managedIds(AccessScopeSet.EDUCATION_UNIT)).containsExactly("school-1");
        assertThat(access.scopeSet().managedIds(AccessScopeSet.CLASS)).containsExactly("class-1");
        assertThat(access.scopeFingerprint()).isEqualTo(access.scopeSet().fingerprint());
        assertThat(access.managedEducationUnitIds()).containsExactly("school-1");
        assertThat(access.managedClassIds()).containsExactly("class-1");
    }
}
