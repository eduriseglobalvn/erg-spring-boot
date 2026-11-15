package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.lms.LessonReportNotifier;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Hands the class-log reminders of lms to the notification outbox. */
@Component
class LessonReportNotifierAdapter implements LessonReportNotifier {

    private final NotificationCommandService commands;
    private final TransactionTemplate isolated;

    LessonReportNotifierAdapter(NotificationCommandService commands, PlatformTransactionManager transactionManager) {
        this.commands = commands;
        // own transaction: a failed notice must never mark the caller's business transaction rollback-only
        this.isolated = new TransactionTemplate(transactionManager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void remind(String tenantId, String actorUserId, String dedupeKey, String teacherUserId, String title, String body) {
        // "system" is a job marker, not a users row; notification_messages.created_by is a nullable FK to users
        String createdBy = actorUserId == null || "system".equals(actorUserId) ? null : actorUserId;
        isolated.executeWithoutResult(status -> commands.enqueue(new NotificationCommand(tenantId, createdBy, dedupeKey, "LESSON_REPORT_REMINDER",
                "lms_lesson_log", teacherUserId, title, body, "/teacher/class-log?tab=teacher", "normal", "lms",
                List.of(teacherUserId), null, Map.of())));
    }
}
