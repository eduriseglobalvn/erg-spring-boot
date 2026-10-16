package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErgPrincipalConstructionContractTest {

    @Test
    void productionCodeConstructsPrincipalsThroughTheFactoryMethod() throws Exception {
        List<Path> offenders = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                if (path.endsWith("platform/security/ErgPrincipal.java")) continue;
                if (Files.readString(path).contains("new ErgPrincipal(")) offenders.add(path);
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void legacyScopeAttributeLiteralsAreOwnedOnlyByTheAssembler() throws Exception {
        List<Path> offenders = new ArrayList<>();
        List<String> literals = List.of(
                "\"managedEducationUnitIds\"",
                "\"managedClassIds\"",
                "\"currentEducationUnitId\"",
                "\"currentClassId\"");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                if (path.endsWith("platform/security/AccessScopeAssembler.java")) continue;
                String source = Files.readString(path);
                if (literals.stream().anyMatch(source::contains)) offenders.add(path);
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void factorySnapshotsMutableAuthoritiesAndAttributes() {
        List<String> roles = new ArrayList<>(List.of("teacher"));
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("managedClassIds", new ArrayList<>(List.of("class-1")));

        ErgPrincipal principal = ErgPrincipal.authenticated(
                "user-1", "session-1", "tenant-1", roles, List.of("lms.read"), List.of(),
                List.of("lms"), "teacher", "managed", attributes);
        roles.add("admin");
        attributes.put("unsafe", true);

        assertThat(principal.roles()).containsExactly("teacher");
        assertThat(principal.attributes()).doesNotContainKey("unsafe");
    }
}
