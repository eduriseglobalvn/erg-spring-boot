package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphQlPolicyUnificationContractTest {

    @Test
    void graphqlDelegatesPermissionDecisionsToTheRestPolicyEngine() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlAccessSupport.java"));

        assertThat(source)
                .contains("private final ErgPolicyService policyService")
                .contains("policyService.isAllowedAny(")
                .doesNotContain("private boolean matches(");
    }
}
