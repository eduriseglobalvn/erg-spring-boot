package com.erg.ergspringboot.controller.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminEmployeeControllerContractTest {

    @Test
    void exposesCanonicalEmployeeAccountActivitySecurityAndSessionEndpoints() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/users/AdminEmployeeController.java"));
        assertThat(source).contains(
                "@RequestMapping(\"/api/v1/admin/users\")",
                "@GetMapping({\"\", \"/\"})",
                "@PostMapping",
                "@GetMapping(\"/{userId}\")",
                "@PatchMapping(\"/{userId}\")",
                "@PutMapping(\"/{userId}/status\")",
                "@PostMapping(\"/{userId}/roles\")",
                "@PostMapping(\"/{userId}/reset-credential\")",
                "@GetMapping(\"/{userId}/activity\")",
                "@GetMapping(\"/{userId}/security-events\")",
                "@GetMapping(\"/{userId}/sessions\")",
                "@PostMapping(\"/{userId}/sessions/revoke\")"
        );
    }
}
