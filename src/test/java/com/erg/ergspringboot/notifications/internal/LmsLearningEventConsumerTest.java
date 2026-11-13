package com.erg.ergspringboot.notifications.internal;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAssignmentCreated;
import com.erg.ergspringboot.lms.LmsAttemptSubmitted;
import com.erg.ergspringboot.lms.LmsNotificationProjection;
import com.erg.ergspringboot.lms.LmsNotificationProjection.AssignmentAudience;
import com.erg.ergspringboot.lms.LmsNotificationProjection.Submitter;
import com.erg.ergspringboot.notifications.internal.LmsNotificationEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LmsLearningEventConsumerTest {

    private final LmsNotificationProjection lms = mock(LmsNotificationProjection.class);
    private final LmsNotificationEventPublisher publisher = mock(LmsNotificationEventPublisher.class);
    private final LmsLearningEventConsumer consumer = new LmsLearningEventConsumer(lms, publisher);
    private final AssignmentAudience audience =
            new AssignmentAudience("asg-1", "Bai 1", "class-1", "teacher-1", List.of("user-1", "user-2"));

    @Test
    void assignmentCreatedNotifiesLinkedStudentsWithTheEventIdAsKey() {
        when(lms.assignmentAudience("erg", "asg-1")).thenReturn(Optional.of(audience));

        consumer.on(new LmsAssignmentCreated("assignment-created:asg-1", "erg", "asg-1", "teacher-1", OffsetDateTime.now()));

        verify(publisher).assignmentCreated("assignment-created:asg-1", "erg", "asg-1", "Bai 1", "class-1",
                List.of("user-1", "user-2"), "teacher-1");
    }

    @Test
    void deletedAssignmentIsSkipped() {
        when(lms.assignmentAudience("erg", "asg-1")).thenReturn(Optional.empty());

        consumer.on(new LmsAssignmentCreated("e", "erg", "asg-1", "teacher-1", OffsetDateTime.now()));

        verifyNoInteractions(publisher);
    }

    @Test
    void failedGradedAttemptAlsoRaisesLowScoreSupportForTheAssigningTeacher() {
        when(lms.assignmentAudience("erg", "asg-1")).thenReturn(Optional.of(audience));
        when(lms.submitter("erg", "stu-1")).thenReturn(Optional.of(new Submitter("An", "user-9")));

        consumer.on(submitted("submitted", false));

        verify(publisher).attemptSubmitted("attempt-submitted:att-1", "erg", "asg-1", "Bai 1", "att-1", "stu-1",
                "user-9", "An", List.of("teacher-1"));
        verify(publisher).supportNeededLowScore("attempt-submitted:att-1:low-score", "erg", "asg-1", "Bai 1",
                "att-1", "stu-1", "user-9", "An", 3.0, 10.0, List.of("teacher-1"));
    }

    @Test
    void provisionalNeedsReviewScoreIsNotReportedAsLowScore() {
        when(lms.assignmentAudience("erg", "asg-1")).thenReturn(Optional.of(audience));
        when(lms.submitter("erg", "stu-1")).thenReturn(Optional.of(new Submitter("An", "user-9")));

        consumer.on(submitted("needs_review", false));

        verify(publisher).attemptSubmitted(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any());
        verify(publisher, never()).supportNeededLowScore(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble(), any());
    }

    @Test
    void studentWithoutLoginAccountIsSentWithNoCreator() {
        when(lms.assignmentAudience("erg", "asg-1")).thenReturn(Optional.of(audience));
        when(lms.submitter("erg", "stu-1")).thenReturn(Optional.of(new Submitter("An", null)));

        consumer.on(submitted("submitted", true));

        verify(publisher).attemptSubmitted("attempt-submitted:att-1", "erg", "asg-1", "Bai 1", "att-1", "stu-1",
                null, "An", List.of("teacher-1"));
    }

    private LmsAttemptSubmitted submitted(String status, boolean passed) {
        return new LmsAttemptSubmitted("attempt-submitted:att-1", "erg", "att-1", "asg-1", "stu-1", status,
                3.0, 10.0, passed, OffsetDateTime.now());
    }
}
