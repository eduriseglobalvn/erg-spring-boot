package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpringModulithDependencyContractTest {

    @Test
    void modulithReleaseAndScopesArePinned() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).contains("<spring-modulith.version>2.1.0</spring-modulith.version>");
        assertThat(pom).contains("<artifactId>spring-modulith-bom</artifactId>");
        assertThat(pom).contains("<artifactId>spring-modulith-starter-core</artifactId>");
        assertThat(pom).contains("<artifactId>spring-modulith-starter-jdbc</artifactId>");
        assertThat(pom).contains("<artifactId>spring-modulith-starter-insight</artifactId>");
        assertThat(pom).contains("<artifactId>spring-modulith-starter-test</artifactId>");

        int testDependency = pom.indexOf("<artifactId>spring-modulith-starter-test</artifactId>");
        assertThat(pom.substring(testDependency, Math.min(pom.length(), testDependency + 300)))
                .contains("<scope>test</scope>");
    }
}
