package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentAssignmentQueryPerformanceContractTest {

    @Test
    void explicitAndLegacyRecipientsUseIndependentIndexableBranches() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/repository/lms/AssignmentRepository.java"));

        assertThat(repository)
                .contains("union all")
                .contains("join assignment_recipients recipient")
                .contains("not exists (")
                .doesNotContain("left join assignment_recipients recipient");
    }
}
