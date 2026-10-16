package com.erg.ergspringboot.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentLifecycleAuthorizationPolicyContractTest {

    @Test
    void controllerUsesModuleOwnedTypedLifecyclePolicy() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/content/ContentLifecycleController.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/content/lifecycle/authorization/ContentLifecycleTransition.java"));
        assertThat(controller).contains("@ContentLifecycleTransition").doesNotContain("@PreAuthorize(");
        assertThat(policy).contains("@PreAuthorize", "content.lifecycle.submit", "content.lifecycle.approve",
                "content.lifecycle.publish", "content.lifecycle.archive", "content.lifecycle.rollback");
    }
}
