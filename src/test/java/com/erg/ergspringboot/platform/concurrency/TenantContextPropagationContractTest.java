package com.erg.ergspringboot.platform.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantContextPropagationContractTest {

    @Test
    void readExecutorDoesNotPropagateAuthenticationOrCreateAdHocSecurityCarriers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutor.java"));
        assertThat(source).doesNotContain("SecurityContextHolder", "copySecurityContext", "setAuthentication");
        assertThat(source).contains("TenantContext.capture()", "TenantContext.install");
    }
}
