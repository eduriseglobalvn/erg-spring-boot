package com.erg.ergspringboot.service.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Publisher that hooks LMS domain events to the notification system.
 *
 * Each method accepts the relevant domain event parameters, builds a
 * {@link NotificationCommand}, and delegates to
 * {@link NotificationCommandService#enqueue(NotificationCommand)}.
 * Failures are logged as warnings and never propagated.
 *
 * Event types map to §9 Event Catalog V1:
 * <ul>
 *   <li>{@code ASSIGNMENT_CREATED}</li>
 *   <li>{@code ASSIGNMENT_DUE_SOON} (scheduled-job placeholder)</li>
 *   <li>{@code ASSIGNMENT_OVERDUE} (scheduled-job placeholder)</li>
 *   <li>{@code ATTEMPT_SUBMITTED}</li>
 *   <li>{@code SUPPORT_NEEDED_LOW_SCORE}</li>
 *   <li>{@code TEACHER_SCHEDULE_ASSIGNED} — already implemented in
 *       {@code TeachingScheduleCommandService}</li>
 * </ul>
 */
@Service
public class LmsNotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LmsNotificationEventPublisher.class);

    private final NotificationCommandService notificationCommandService;

    public LmsNotificationEventPublisher(NotificationCommandService notificationCommandService) {
        this.notificationCommandService = notificationCommandService;
    }

    // -------------------------------------------------------------------------
    // ASSIGNMENT_CREATED
    // -------------------------------------------------------------------------

    /**
     * Notify all students in the class when a new assignment is created.
     *
     * @param tenantId        tenant scope
     * @param assignmentId    identifier of the created assignment
     * @param assignmentTitle display title of the assignment
     * @param classId         identifier of the target class
     * @param studentUserIds  user IDs of enrolled students
     * @param createdBy       user ID of the creator
     */
    public void assignmentCreated(
            String tenantId,
            String assignmentId,
            String assignmentTitle,
            String classId,
            List<String> studentUserIds,
            String createdBy
    ) {
        if (studentUserIds == null || studentUserIds.isEmpty()) {
            return;
        }
        try {
            String title = "Bai tap moi: " + safeLabel(assignmentTitle);
            String bodyPreview = "Co bai tap moi duoc tao trong lop. Vui long kiem tra va hoan thanh dung han.";

            NotificationCommand command = new NotificationCommand(
                    tenantId,
                    createdBy,
                    null,                           // idempotencyKey
                    "ASSIGNMENT_CREATED",
                    "assignment",
                    assignmentId,
                    title,
                    bodyPreview,
                    null,                           // actionUrl
                    "normal",
                    "lms",
                    studentUserIds,
                    null,                           // topicName
                    Map.of("assignmentId", assignmentId, "classId", classId)
            );

            notificationCommandService.enqueue(command);
            log.info("ASSIGNMENT_CREATED notification enqueued for assignment {} targeting {} students",
                    assignmentId, studentUserIds.size());
        } catch (Exception e) {
            log.warn("Failed to emit ASSIGNMENT_CREATED notification for assignment {}: {}",
                    assignmentId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // ASSIGNMENT_DUE_SOON (scheduled-job placeholder)
    // -------------------------------------------------------------------------

    /**
     * Placeholder for the scheduled job that reminds students of upcoming
     * assignment deadlines.
     *
     * @param tenantId        tenant scope
     * @param assignmentId    identifier of the assignment
     * @param assignmentTitle display title of the assignment
     * @param dueAt           ISO-8601 due-date string
     * @param studentUserIds  user IDs of enrolled students
     */
    public void assignmentDueSoon(
            String tenantId,
            String assignmentId,
            String assignmentTitle,
            String dueAt,
            List<String> studentUserIds
    ) {
        if (studentUserIds == null || studentUserIds.isEmpty()) {
            return;
        }
        try {
            String title = "Sap den han: " + safeLabel(assignmentTitle);
            String bodyPreview = "Bai tap \"" + safeLabel(assignmentTitle)
                    + "\" sap den han vao " + safeLabel(dueAt) + ". Vui long nop bai dung thoi gian.";

            NotificationCommand command = new NotificationCommand(
                    tenantId,
                    "system",
                    null,                           // idempotencyKey
                    "ASSIGNMENT_DUE_SOON",
                    "assignment",
                    assignmentId,
                    title,
                    bodyPreview,
                    null,                           // actionUrl
                    "normal",
                    "lms",
                    studentUserIds,
                    null,                           // topicName
                    Map.of("assignmentId", assignmentId, "dueAt", safeLabel(dueAt))
            );

            notificationCommandService.enqueue(command);
            log.info("ASSIGNMENT_DUE_SOON notification enqueued for assignment {} targeting {} students",
                    assignmentId, studentUserIds.size());
        } catch (Exception e) {
            log.warn("Failed to emit ASSIGNMENT_DUE_SOON notification for assignment {}: {}",
                    assignmentId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // ASSIGNMENT_OVERDUE (scheduled-job placeholder)
    // -------------------------------------------------------------------------

    /**
     * Placeholder for the scheduled job that notifies students whose
     * assignment submissions are past the deadline.
     *
     * @param tenantId        tenant scope
     * @param assignmentId    identifier of the assignment
     * @param assignmentTitle display title of the assignment
     * @param dueAt           ISO-8601 due-date string
     * @param studentUserIds  user IDs of overdue students
     */
    public void assignmentOverdue(
            String tenantId,
            String assignmentId,
            String assignmentTitle,
            String dueAt,
            List<String> studentUserIds
    ) {
        if (studentUserIds == null || studentUserIds.isEmpty()) {
            return;
        }
        try {
            String title = "Qua han: " + safeLabel(assignmentTitle);
            String bodyPreview = "Bai tap \"" + safeLabel(assignmentTitle)
                    + "\" da qua han vao " + safeLabel(dueAt) + ". Vui long nop bai som nhat co the.";

            NotificationCommand command = new NotificationCommand(
                    tenantId,
                    "system",
                    null,                           // idempotencyKey
                    "ASSIGNMENT_OVERDUE",
                    "assignment",
                    assignmentId,
                    title,
                    bodyPreview,
                    null,                           // actionUrl
                    "high",
                    "lms",
                    studentUserIds,
                    null,                           // topicName
                    Map.of("assignmentId", assignmentId, "dueAt", safeLabel(dueAt))
            );

            notificationCommandService.enqueue(command);
            log.info("ASSIGNMENT_OVERDUE notification enqueued for assignment {} targeting {} students",
                    assignmentId, studentUserIds.size());
        } catch (Exception e) {
            log.warn("Failed to emit ASSIGNMENT_OVERDUE notification for assignment {}: {}",
                    assignmentId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // ATTEMPT_SUBMITTED
    // -------------------------------------------------------------------------

    /**
     * Notify teacher(s) when a student submits an attempt for an assignment.
     *
     * @param tenantId        tenant scope
     * @param assignmentId    identifier of the assignment
     * @param assignmentTitle display title of the assignment
     * @param attemptId       identifier of the submitted attempt
     * @param studentId       user ID of the submitting student
     * @param studentName     display name of the student
     * @param teacherUserIds  user IDs of the teachers to notify
     */
    public void attemptSubmitted(
            String tenantId,
            String assignmentId,
            String assignmentTitle,
            String attemptId,
            String studentId,
            String studentName,
            List<String> teacherUserIds
    ) {
        if (teacherUserIds == null || teacherUserIds.isEmpty()) {
            return;
        }
        try {
            String title = "Bai lam da duoc nop: " + safeLabel(assignmentTitle);
            String bodyPreview = "Hoc sinh " + safeLabel(studentName)
                    + " da nop bai cho \"" + safeLabel(assignmentTitle) + "\".";

            NotificationCommand command = new NotificationCommand(
                    tenantId,
                    studentId,
                    null,                           // idempotencyKey
                    "ATTEMPT_SUBMITTED",
                    "attempt",
                    attemptId,
                    title,
                    bodyPreview,
                    null,                           // actionUrl
                    "normal",
                    "lms",
                    teacherUserIds,
                    null,                           // topicName
                    Map.of(
                            "assignmentId", assignmentId,
                            "attemptId", attemptId,
                            "studentId", studentId
                    )
            );

            notificationCommandService.enqueue(command);
            log.info("ATTEMPT_SUBMITTED notification enqueued for attempt {} from student {}",
                    attemptId, studentId);
        } catch (Exception e) {
            log.warn("Failed to emit ATTEMPT_SUBMITTED notification for attempt {}: {}",
                    attemptId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // SUPPORT_NEEDED_LOW_SCORE
    // -------------------------------------------------------------------------

    /**
     * Notify teacher(s) and/or support staff when a student scores low on
     * an assignment attempt.
     *
     * @param tenantId        tenant scope
     * @param assignmentId    identifier of the assignment
     * @param assignmentTitle display title of the assignment
     * @param attemptId       identifier of the graded attempt
     * @param studentId       user ID of the student
     * @param studentName     display name of the student
     * @param score           numeric score achieved
     * @param maxScore        maximum possible score
     * @param recipientUserIds user IDs of teachers / support staff to notify
     */
    public void supportNeededLowScore(
            String tenantId,
            String assignmentId,
            String assignmentTitle,
            String attemptId,
            String studentId,
            String studentName,
            double score,
            double maxScore,
            List<String> recipientUserIds
    ) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        try {
            String title = "Can ho tro: " + safeLabel(studentName) + " - " + safeLabel(assignmentTitle);
            String bodyPreview = "Hoc sinh " + safeLabel(studentName)
                    + " dat diem thap (" + formatScore(score) + "/" + formatScore(maxScore)
                    + ") cho bai tap \"" + safeLabel(assignmentTitle) + "\". Vui long xem xet ho tro.";

            NotificationCommand command = new NotificationCommand(
                    tenantId,
                    studentId,
                    null,                           // idempotencyKey
                    "SUPPORT_NEEDED_LOW_SCORE",
                    "attempt",
                    attemptId,
                    title,
                    bodyPreview,
                    null,                           // actionUrl
                    "high",
                    "lms",
                    recipientUserIds,
                    null,                           // topicName
                    Map.of(
                            "assignmentId", assignmentId,
                            "attemptId", attemptId,
                            "studentId", studentId,
                            "score", String.valueOf(score),
                            "maxScore", String.valueOf(maxScore)
                    )
            );

            notificationCommandService.enqueue(command);
            log.info("SUPPORT_NEEDED_LOW_SCORE notification enqueued for attempt {} student {} score {}/{}",
                    attemptId, studentId, formatScore(score), formatScore(maxScore));
        } catch (Exception e) {
            log.warn("Failed to emit SUPPORT_NEEDED_LOW_SCORE notification for attempt {}: {}",
                    attemptId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String safeLabel(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String formatScore(double score) {
        if (score == Math.floor(score)) {
            return String.valueOf((int) score);
        }
        return String.format("%.1f", score);
    }
}
