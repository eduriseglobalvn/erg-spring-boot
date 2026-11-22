package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Student;
import jakarta.persistence.Basic;
import jakarta.persistence.FetchType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** S1/H6: the legacy student_ids snapshot is lazy and only read for assignments without recipient rows. */
class LmsAssignmentLegacyRecipientsTest {

    @Test
    void theSnapshotColumnIsLazyAndTheEntityIsEnhancedSoItIsNotLoadedWithEveryAssignment() throws Exception {
        Basic basic = Assignment.class.getDeclaredField("studentIds").getAnnotation(Basic.class);

        assertThat(basic).isNotNull();
        assertThat(basic.fetch()).isEqualTo(FetchType.LAZY);
        // hibernate-maven-plugin (enhance goal, lazy initialization on by default) rewrites the class at build time.
        assertThat(org.hibernate.engine.spi.PersistentAttributeInterceptable.class).isAssignableFrom(Assignment.class);
    }

    @Test
    void aLegacyAssignmentCountsItsSnapshotInSqlAndASavedRowsAssignmentCountsTheRows() {
        var fixture = new LmsAttemptFixture();
        Assignment assignment = new Assignment();
        assignment.setId("asg-1");
        assignment.setTenantId("erg");
        when(fixture.recipients.countByAssignmentId("asg-1")).thenReturn(0L);
        when(fixture.recipients.countLegacyStudentIds("asg-1")).thenReturn(3);

        assertThat(fixture.policy.recipientCount(assignment)).isEqualTo(3);

        when(fixture.recipients.countByAssignmentId("asg-1")).thenReturn(40L);
        assertThat(fixture.policy.recipientCount(assignment)).isEqualTo(40);
    }

    @Test
    void importCandidatesAreTheAssignmentsTheSqlSelectedForTheLearner() {
        var fixture = new LmsAttemptFixture();
        Student learner = new Student();
        learner.setId("stu-1");
        learner.setAcademicClassId("class-1");
        learner.setAuthUserId("usr-1");
        Assignment visible = assignment("asg-1", "active");
        Assignment closed = assignment("asg-2", "draft");
        when(fixture.resources.findVisibleAssignmentIdsForLearner("erg", "quiz-1", null, "stu-1", "class-1", "usr-1"))
                .thenReturn(List.of("asg-1", "asg-2"));
        when(fixture.assignments.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(visible, closed));

        assertThat(fixture.policy.visibleAssignmentsFor("erg", "quiz-1", learner)).containsExactly(visible);
    }

    private static Assignment assignment(String id, String status) {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setTenantId("erg");
        assignment.setStatus(status);
        return assignment;
    }
}
