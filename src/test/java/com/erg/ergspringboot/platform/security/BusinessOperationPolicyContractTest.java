package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessOperationPolicyContractTest {

    @Test
    void rawPreAuthorizeExpressionsExistOnlyInsideOwnedAuthorizationPackages() throws Exception {
        List<Path> offenders = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java/com/erg/ergspringboot"))) {
            for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/authorization/")) continue;
                if (Files.readString(path).contains("@PreAuthorize(")) offenders.add(path);
            }
        }
        assertThat(offenders).isEmpty();
    }
}
