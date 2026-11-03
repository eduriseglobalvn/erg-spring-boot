package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.lms.LmsAssignmentCreated;
import com.erg.ergspringboot.lms.LmsAttemptSubmitted;
import com.erg.ergspringboot.lms.LmsNotificationProjection;
import com.erg.ergspringboot.lms.LmsNotificationProjection.AssignmentAudience;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.service.notifications.LmsNotificationEventPublisher;
import java.util.List;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Notifications-owned consumer that turns LMS learning events into in-app notifications (C10). */
@Component
public class LmsLearningEventConsumer {

    private final LmsNotificationProjection lms;
    private final LmsNotificationEventPublisher notifications;

    public LmsLearningEventConsumer(LmsNotificationProjection lms, LmsNotificationEventPublisher notifications) {
        this.lms = lms;
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(LmsAssignmentCreated event) {
        // A deleted assignment has nobody left to notify; completing the event is correct.
        lms.assignmentAudience(event.tenantId(), event.assignmentId()).ifPresent(audience ->
                notifications.assignmentCreated(event.eventId(), event.tenantId(), audience.assignmentId(),
                        audience.title(), audience.classId(), audience.studentUserIds(), event.actorId()));
    }

    @ApplicationModuleListener
    public void on(LmsAttemptSubmitted event) {
        AssignmentAudience audience = lms.assignmentAudience(event.tenantId(), event.assignmentId()).orElse(null);
        if (audience == null || audience.assignedBy() == null) {
            return;
        }
        List<String> teachers = List.of(audience.assignedBy());
        String studentName = lms.studentName(event.tenantId(), event.studentId()).orElse(event.studentId());
        notifications.attemptSubmitted(event.eventId(), event.tenantId(), event.assignmentId(), audience.title(),
                event.attemptId(), event.studentId(), studentName, teachers);
        // A needs_review score is provisional (manual answers count as 0), so it is not a low score yet.
        if (!event.passed() && !LmsStatePolicy.attemptNeedsReview(event.status())) {
            notifications.supportNeededLowScore(event.eventId() + ":low-score", event.tenantId(),
                    event.assignmentId(), audience.title(), event.attemptId(), event.studentId(), studentName,
                    event.score(), event.maxScore(), teachers);
        }
    }
}
