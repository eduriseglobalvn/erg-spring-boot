package com.erg.ergspringboot.lms.internal.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttemptDeadlinePolicyTest {
    private static final OffsetDateTime DUE = OffsetDateTime.of(2026, 11, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void corruptSnapshotDeadlineFallsBackToTheAssignmentDeadline() {
        Assignment assignment = new Assignment();
        assignment.setDueAt(DUE);
        Attempt attempt = new Attempt();
        attempt.setId("att-1");
        attempt.setPolicySnapshot(Map.of("deadlineAt", "not-a-date"));

        var policy = new AttemptDeadlinePolicy(Duration.ZERO);

        assertThat(policy.effectiveDeadline(attempt, assignment)).isEqualTo(DUE);
        assertThat(policy.isExpired(attempt, assignment, DUE.plusMinutes(1))).isTrue();
    }

    @Test
    void validSnapshotDeadlineWinsOverTheLiveAssignment() {
        Assignment assignment = new Assignment();
        assignment.setDueAt(DUE.plusDays(1));
        Attempt attempt = new Attempt();
        attempt.setPolicySnapshot(Map.of("deadlineAt", DUE.toString()));

        assertThat(new AttemptDeadlinePolicy(Duration.ZERO).effectiveDeadline(attempt, assignment)).isEqualTo(DUE);
    }
}
