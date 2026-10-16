package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerImageDeliveryContractTest {

    @Test
    void releaseWorkflowBuildsAndPushesOnlyImmutableGitShaImages() throws Exception {
        String windows = Files.readString(Path.of("scripts/docker.ps1"));
        String linux = Files.readString(Path.of("scripts/docker.sh"));
        String runbook = Files.readString(Path.of("ops/docker/README.md"));
        String productionExample = Files.readString(Path.of("ops/docker/.env.production.example"));

        assertThat(windows)
                .contains("\"release\"", "Assert-ImmutableImage", "\"--platform\", \"linux/amd64\"", "\"--push\"")
                .contains("7-64 lowercase hex characters")
                .contains("Assert-ProductionImage", "Invoke-ProductionDeployment", "ERG_ROLLBACK_IMAGE", "wait-timeout", "\"--no-build\"");
        assertThat(linux)
                .contains("release)", "assert_immutable_image", "--platform linux/amd64 --push")
                .contains("assert_production_image", "ERG_ROLLBACK_IMAGE", "--wait --wait-timeout 180", "--no-build");
        assertThat(runbook)
                .contains(".\\scripts\\docker.ps1 release -Image", "./scripts/docker.sh release")
                .contains("Git SHA", "latest", "không tạo image tạm")
                .contains("@sha256:", "ERG_ROLLBACK_IMAGE", "health gate", "rollback")
                .contains("--no-build");
        assertThat(productionExample)
                .contains("ERG_APP_IMAGE=", "@sha256:", "ERG_ROLLBACK_IMAGE=")
                .doesNotContain("replace-with-git-sha");
    }
}
