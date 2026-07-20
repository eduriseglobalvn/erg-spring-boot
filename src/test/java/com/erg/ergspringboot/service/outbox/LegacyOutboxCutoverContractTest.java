package com.erg.ergspringboot.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyOutboxCutoverContractTest {
    @Test
    void legacyWorkerHasAnExplicitOwnerScopeAndShutdownGate() throws Exception {
        String worker = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/outbox/TransactionalOutboxWorker.java"));
        String runbook = Files.readString(Path.of("docs/runbooks/outbox-cutover.md"));

        assertThat(worker)
                .contains("ACCOUNT_TEACHER_INVITE", "ACCOUNT_RECOVERY_CODE")
                .doesNotContain("CalendarBatchPublished", "ATTEMPT_SUBMITTED", "ASSIGNMENT_PUBLISHED");
        assertThat(runbook)
                .contains("2026-08-31", "ERG_OUTBOX_WORKER_ENABLED=false", "backlog = 0", "parity");
    }
}
