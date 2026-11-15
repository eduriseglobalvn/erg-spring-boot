package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.lms.LessonReportNotifier;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Hands the class-log reminders of lms to the notification outbox. */
@Component
class LessonReportNotifierAdapter implements LessonReportNotifier {

    private final NotificationCommandService commands;

    LessonReportNotifierAdapter(NotificationCommandService commands) {
        this.commands = commands;
    }

    @Override
    public void remind(String tenantId, String actorUserId, String dedupeKey, String teacherUserId, String title, String body) {
        commands.enqueue(new NotificationCommand(tenantId, actorUserId, dedupeKey, "LESSON_REPORT_REMINDER",
                "lms_lesson_log", teacherUserId, title, body, "/teacher/class-log?tab=teacher", "normal", "lms",
                List.of(teacherUserId), null, Map.of()));
    }
}
